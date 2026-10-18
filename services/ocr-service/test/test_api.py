import time
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from api.main import app, load_detector


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
