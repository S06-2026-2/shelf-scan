import os

import cv2
import pytest

# Caminho relativo: tests/ -> ocr-service/samples/memorias_subsolo.jpg
SAMPLE_IMAGE_PATH = os.path.join(
    os.path.dirname(__file__), "..", "samples", "memorias_subsolo.jpg"
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