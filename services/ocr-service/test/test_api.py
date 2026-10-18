import time
from pathlib import Path
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from api.main import app, load_detector

SAMPLE_PATH = Path(__file__).resolve().parent.parent / "samples" / "memorias_subsolo.jpg"
EMPTY_BOOK = {"nome": None, "volume": None, "autores": None, "editora": None}


class DetectorWithoutRegions:
    """Detector falso que nunca acha caixas: força o fallback, rápido e previsível"""

    def detect_text(self, img):
        return []


def upload(client, data, filename="capa.jpg", content_type="image/jpeg"):
    """Envia os bytes no campo multipart "image", como o front fará com FormData"""
    return client.post("/v1/extraction", files={"image": (filename, data, content_type)})


@pytest.fixture
def client():
    """Cliente sem o lifespan: cada teste define o estado do modelo que quer simular"""
    return TestClient(app)


def set_model_status(status, detector=None):
    app.state.model_status = status
    app.state.detector = detector


class TestLive:
    """
        /live: o processo responde, independente do modelo
    """

    @pytest.mark.parametrize("status", ["loading", "ready", "error"])
    def test_live_is_always_ok(self, client, status):
        set_model_status(status)

        response = client.get("/live")

        assert response.status_code == 200
        assert response.json() == {"status": "alive"}


class TestHealth:
    """
        /health: só responde 200 quando o modelo está carregado
    """

    @pytest.mark.parametrize("status", ["loading", "error"])
    def test_not_ready_is_503(self, client, status):
        set_model_status(status)

        response = client.get("/health")

        assert response.status_code == 503
        assert response.json() == {"status": status}

    def test_ready_is_200(self, client):
        set_model_status("ready", detector=object())

        response = client.get("/health")

        assert response.status_code == 200
        assert response.json() == {"status": "ready"}


class TestModelLoading:
    """
        Carga do modelo em segundo plano
    """

    def test_failed_load_is_reported_as_error(self, tmp_path):
        """Modelo ausente: o estado vira "error" (e não fica "loading" para sempre)"""
        state = SimpleNamespace()

        load_detector(state, tmp_path / "nao_existe.pb")

        assert state.model_status == "error"
        assert getattr(state, "detector", None) is None

    def test_lifespan_loads_model_in_background(self, east_model_path):
        """
        Ao subir, o servidor já responde /live; o /health passa a 200 quando a
        thread termina de carregar o modelo
        """
        try:
            with TestClient(app) as client:  # o "with" dispara o lifespan
                assert client.get("/live").status_code == 200

                deadline = time.monotonic() + 30
                while client.get("/health").status_code != 200 and time.monotonic() < deadline:
                    time.sleep(0.2)

                assert client.get("/health").json() == {"status": "ready"}
                assert app.state.detector is not None
        finally:
            # Libera os ~630 MB do modelo para os próximos módulos de teste
            set_model_status("loading")


class TestExtraction:
    """
        POST /v1/extraction: o caminho feliz (os erros ficam na etapa C3)
    """

    def test_returns_contract_fields(self, client):
        set_model_status("ready", detector=DetectorWithoutRegions())

        response = upload(client, SAMPLE_PATH.read_bytes())

        assert response.status_code == 200
        body = response.json()
        assert set(body) == {"books", "raw_text", "result", "meta"}
        assert body["books"] == [EMPTY_BOOK]
        assert body["result"] == "unstructured"
        assert body["meta"]["detector"] == "fallback"

    def test_reads_cover_with_east(self, client, text_detector):
        """De ponta a ponta: upload → EAST → OCR por região → JSON"""
        set_model_status("ready", detector=text_detector)

        response = upload(client, SAMPLE_PATH.read_bytes())

        assert response.status_code == 200
        assert response.json()["meta"]["detector"] == "east"
        assert "BORIS" in response.json()["raw_text"].upper()

    def test_route_is_documented(self, client):
        """O /docs mostra a rota e o formato da resposta para o front e o bot"""
        openapi = client.get("/openapi.json").json()

        assert "/v1/extraction" in openapi["paths"]
        assert "ExtractionResponse" in openapi["components"]["schemas"]
