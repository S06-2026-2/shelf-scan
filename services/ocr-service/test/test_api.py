import threading
import time
from pathlib import Path
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from api import main
from api.main import app, load_detector
from ocr import exceptions
from ocr.exceptions import OcrError

SAMPLE_PATH = Path(__file__).resolve().parent.parent / "samples" / "memorias_subsolo.jpg"
EMPTY_BOOK = {"nome": None, "volume": None, "autores": None, "editora": None}
GIF_BYTES = b"GIF89a\x01\x00\x01\x00"

# Resposta válida do contrato, devolvida pelo pipeline falso dos testes de erro
VALID_RESPONSE = {
    "books": [EMPTY_BOOK],
    "raw_text": "",
    "result": "no_text",
    "meta": {"detector": "fallback", "elapsed_ms": 0},
}


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


def pipeline_raising(error):
    """Pipeline falso que só levanta o erro: testa a tradução para HTTP sem depender do OCR"""
    def fake_pipeline(img_bytes, detector):
        raise error
    return fake_pipeline


def assert_error(response, status, code):
    """Formato único dos erros (contrato, seção 4.3): o status do catálogo e o corpo {"code", "message"}"""
    assert response.status_code == status
    body = response.json()
    assert set(body) == {"code", "message"}
    assert body["code"] == code
    assert body["message"]
    return body


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


class TestDomainErrors:
    """
        Erros do catálogo de domínio (ocr/exceptions.py) viram o status e o code do
        contrato (seção 4.3). A mensagem é a da exceção, para quem lê o log
    """

    @pytest.mark.parametrize("error_name, status, code", [
        pytest.param("FileTooLargeError", 413, "file_too_large", id="file_too_large"),
        pytest.param("UnsupportedFormatError", 415, "unsupported_format", id="unsupported_format"),
        pytest.param("CorruptImageError", 422, "corrupt_image", id="corrupt_image"),
        pytest.param("ImageTooLargeError", 422, "image_too_large", id="image_too_large"),
        pytest.param("ImageTooSmallError", 422, "image_too_small", id="image_too_small"),
    ])
    def test_error_becomes_contract_status(self, client, monkeypatch, error_name, status, code):
        set_model_status("ready", detector=DetectorWithoutRegions())
        error = getattr(exceptions, error_name)("mensagem de teste")
        monkeypatch.setattr("api.main.run_pipeline", pipeline_raising(error))

        response = upload(client, SAMPLE_PATH.read_bytes())

        body = assert_error(response, status, code)
        assert body["message"] == "mensagem de teste"

    def test_every_domain_error_has_a_status(self):
        """Um erro novo no catálogo, sem status na API, viraria 500 sem ninguém perceber"""
        assert set(main.ERROR_STATUS) == {cls.code for cls in OcrError.__subclasses__()}

    @pytest.mark.parametrize("data, status, code", [
        pytest.param(GIF_BYTES, 415, "unsupported_format", id="gif"),
        pytest.param(SAMPLE_PATH.read_bytes()[:200_000], 422, "corrupt_image", id="jpeg_cortado"),
    ])
    def test_real_validation_reaches_client(self, client, data, status, code):
        """Sem pipeline falso: o erro do decode_image atravessa a thread e chega ao cliente"""
        set_model_status("ready", detector=DetectorWithoutRegions())

        response = upload(client, data)

        assert_error(response, status, code)


class TestUploadLimit:
    """
        Limite de tamanho do arquivo, verificado antes de qualquer outra coisa (contrato,
        seção 5). Os testes baixam o limite para não precisar enviar 20 MB
    """

    def test_limit_is_20_mb(self):
        assert main.MAX_UPLOAD_BYTES == 20 * 1024 * 1024

    def test_one_byte_over_limit_is_413(self, client, monkeypatch):
        set_model_status("ready", detector=DetectorWithoutRegions())
        data = SAMPLE_PATH.read_bytes()
        monkeypatch.setattr("api.main.MAX_UPLOAD_BYTES", len(data) - 1)

        response = upload(client, data)

        assert_error(response, 413, "file_too_large")

    def test_file_at_limit_reaches_pipeline_whole(self, client, monkeypatch):
        """O limite é inclusivo, e o pipeline recebe o arquivo inteiro: nenhum byte cortado"""
        set_model_status("ready", detector=DetectorWithoutRegions())
        data = SAMPLE_PATH.read_bytes()
        monkeypatch.setattr("api.main.MAX_UPLOAD_BYTES", len(data))
        received = []

        def fake_pipeline(img_bytes, detector):
            received.append(img_bytes)
            return VALID_RESPONSE

        monkeypatch.setattr("api.main.run_pipeline", fake_pipeline)

        response = upload(client, data)

        assert response.status_code == 200
        assert received == [data]

    def test_size_is_checked_before_format(self, client, monkeypatch):
        """Ordem do contrato, tamanho → magic numbers: um arquivo grande que não é foto dá 413, não 415"""
        set_model_status("ready", detector=DetectorWithoutRegions())
        monkeypatch.setattr("api.main.MAX_UPLOAD_BYTES", 100)

        response = upload(client, b"x" * 101)

        assert_error(response, 413, "file_too_large")


class TestServiceNotReady:
    """
        Sem o modelo carregado, a foto não é lida: 503 para o cliente tentar de novo
    """

    @pytest.mark.parametrize("status", ["loading", "error"])
    def test_not_ready_is_503(self, client, status):
        set_model_status(status)

        response = upload(client, SAMPLE_PATH.read_bytes())

        assert_error(response, 503, "service_not_ready")

    def test_readiness_is_checked_before_reading(self, client):
        """Sem modelo não adianta validar: até um GIF recebe 503, e não 415"""
        set_model_status("loading")

        response = upload(client, GIF_BYTES)

        assert_error(response, 503, "service_not_ready")


class TestInvalidRequest:
    """
        Requisição fora do contrato (validação do próprio FastAPI): 422 com code, no mesmo formato
    """

    @pytest.mark.parametrize("files", [
        pytest.param({"file": ("capa.jpg", GIF_BYTES, "image/jpeg")}, id="campo_file"),
        pytest.param(None, id="sem_arquivo"),
    ])
    def test_missing_image_is_invalid_request(self, client, files):
        """O campo "file" é o que o front envia hoje; a mensagem diz qual campo faltou"""
        set_model_status("ready", detector=DetectorWithoutRegions())

        response = client.post("/v1/extraction", files=files)

        body = assert_error(response, 422, "invalid_request")
        assert "image" in body["message"]


class TestUnexpectedError:
    """
        Erro fora do catálogo: 500 com code, sem os detalhes internos
    """

    def test_unexpected_error_is_500_without_details(self, monkeypatch):
        set_model_status("ready", detector=DetectorWithoutRegions())
        monkeypatch.setattr("api.main.run_pipeline", pipeline_raising(RuntimeError("segredo em /app/ocr")))
        # Sem isso, o TestClient repassa a exceção ao teste em vez de devolver a resposta
        client = TestClient(app, raise_server_exceptions=False)

        response = upload(client, SAMPLE_PATH.read_bytes())

        body = assert_error(response, 500, "internal_error")
        assert "segredo" not in body["message"]


class TestPipelineLock:
    """
        Uma leitura por vez (contrato, seção 5): o EAST é um modelo só, e duas fotos
        ao mesmo tempo trocariam o tamanho de entrada uma da outra
    """

    def test_readings_never_overlap(self, monkeypatch):
        """Três fotos enviadas juntas: em nenhum momento duas leituras rodam ao mesmo tempo"""
        set_model_status("ready", detector=DetectorWithoutRegions())
        active = 0
        max_active = 0
        counter_lock = threading.Lock()

        def slow_pipeline(img_bytes, detector):
            nonlocal active, max_active
            with counter_lock:
                active += 1
                max_active = max(max_active, active)
            time.sleep(0.3)  # tempo para as outras requisições tentarem entrar
            with counter_lock:
                active -= 1
            return VALID_RESPONSE

        monkeypatch.setattr("api.main.run_pipeline", slow_pipeline)
        statuses = []

        def send():
            statuses.append(upload(TestClient(app), SAMPLE_PATH.read_bytes()).status_code)

        threads = [threading.Thread(target=send, daemon=True) for _ in range(3)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join(timeout=10)

        assert statuses == [200, 200, 200]
        assert max_active == 1

    def test_lock_is_released_after_an_error(self, client, monkeypatch):
        """Uma leitura que falha não pode prender as próximas: o lock fica livre depois do erro"""
        set_model_status("ready", detector=DetectorWithoutRegions())
        monkeypatch.setattr("api.main.run_pipeline", pipeline_raising(exceptions.CorruptImageError("quebrada")))

        response = upload(client, SAMPLE_PATH.read_bytes())

        assert response.status_code == 422
        assert not main.PIPELINE_LOCK.locked(), "o lock ficou preso: as próximas leituras esperariam para sempre"


class TestCors:
    """
        CORS: o navegador só entrega a resposta ao site se a origem dele estiver liberada.
        A configuração segue a do notification-service (CORS_ORIGENS_PERMITIDAS)
    """

    def test_default_origin_matches_notifier(self):
        """Mesmo padrão do notification-service: o front do Vite em localhost:5173"""
        assert main.DEFAULT_CORS_ORIGINS == "http://localhost:5173"

    def test_origins_are_comma_separated(self):
        assert main.parse_origins(" http://a.test:1, http://b.test:2 ,,") == ["http://a.test:1", "http://b.test:2"]

    def test_allowed_origin_gets_cors_header(self, client):
        origin = main.CORS_ORIGINS[0]

        response = client.get("/live", headers={"Origin": origin})

        assert response.headers.get("access-control-allow-origin") == origin

    def test_other_origin_gets_no_cors_header(self, client):
        """Outro site não recebe a liberação: o navegador esconde a resposta dele"""
        response = client.get("/live", headers={"Origin": "http://outro-site.test"})

        assert "access-control-allow-origin" not in response.headers

    def test_preflight_allows_post_from_front(self, client):
        """A pergunta prévia do navegador (OPTIONS) libera o POST da foto"""
        origin = main.CORS_ORIGINS[0]

        response = client.options("/v1/extraction", headers={
            "Origin": origin,
            "Access-Control-Request-Method": "POST",
        })

        assert response.status_code == 200
        assert response.headers.get("access-control-allow-origin") == origin
        assert "POST" in response.headers.get("access-control-allow-methods", "")
