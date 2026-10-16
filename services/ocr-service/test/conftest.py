import os

import cv2
import pytest

from ocr.text_detector import load_east_model

# Caminho relativo: tests/ -> ocr-service/samples/memorias_subsolo.jpg
SAMPLE_IMAGE_PATH = os.path.join(
    os.path.dirname(__file__), "..", "samples", "memorias_subsolo.jpg"
)

# Caminho relativo: tests/ -> ocr-service/models/ (baixado por scripts/download_east.py)
EAST_MODEL_PATH = os.path.join(
    os.path.dirname(__file__), "..", "models", "frozen_east_text_detection.pb"
)


@pytest.fixture(scope="module")
def sample_image():
    """Carrega a imagem real de amostra (capa de livro) usada em todos os testes.

    scope='module' evita reler o arquivo do disco a cada teste — a imagem
    não é modificada em nenhuma das funções testadas (todas retornam uma
    imagem nova), então reaproveitar é seguro.
    """
    img = cv2.imread(SAMPLE_IMAGE_PATH)
    assert img is not None, (
        f"Não foi possível carregar a imagem de teste em '{SAMPLE_IMAGE_PATH}'. "
        "Verifique se o arquivo existe e se o teste está sendo rodado a partir "
        "do diretório correto (ocr-service/)."
    )
    return img


@pytest.fixture(scope="module")
def east_net():
    """Carrega a rede EAST uma vez por módulo de teste.

    O modelo tem ~96 MB e leva cerca de 1 s para carregar, então recarregar
    a cada teste deixaria a suíte lenta. O forward não altera a rede, então
    reaproveitar entre os testes é seguro.
    """
    assert os.path.exists(EAST_MODEL_PATH), (
        f"Modelo EAST não encontrado em '{EAST_MODEL_PATH}'. "
        "Rode `python scripts/download_east.py` a partir de services/ocr-service/."
    )
    return load_east_model(EAST_MODEL_PATH)
