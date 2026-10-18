import os

# O OpenCV lê o limite de pixels no import do cv2: precisa ser definido antes
# dele. No container o Dockerfile já define o mesmo valor; aqui vale para a
# suíte rodando fora dele.
os.environ.setdefault("OPENCV_IO_MAX_IMAGE_PIXELS", "55000000")

import cv2  # noqa: E402
import pytest  # noqa: E402

from ocr.text_detector import EastTextDetector, load_east_model  # noqa: E402

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


@pytest.fixture(scope="session")
def east_model_path():
    """Caminho do modelo EAST, conferindo antes se ele foi baixado"""
    assert os.path.exists(EAST_MODEL_PATH), (
        f"Modelo EAST não encontrado em '{EAST_MODEL_PATH}'. "
        "Rode `python scripts/download_east.py` a partir de services/ocr-service/."
    )
    return EAST_MODEL_PATH


@pytest.fixture(scope="class")
def east_net(east_model_path):
    """Carrega a rede EAST uma vez por classe de teste.

    O modelo tem ~96 MB e leva cerca de 1 s para carregar, então recarregar
    a cada teste deixaria a suíte lenta. O forward não altera a rede, então
    reaproveitar entre os testes é seguro. O escopo é de classe (e não de
    módulo) para a rede, com os buffers do forward, ser liberada ao fim da
    classe: manter várias cópias do EAST vivas ao mesmo tempo já esgotou a
    memória do OpenCV no Windows.
    """
    return load_east_model(east_model_path)


@pytest.fixture(scope="module")
def text_detector(east_model_path):
    """Cria o EastTextDetector uma vez por módulo de teste.

    Mesmo motivo do east_net: o construtor carrega o modelo de ~96 MB.
    O detect_text() não guarda estado entre chamadas que afete o resultado
    (o tamanho de entrada é reconfigurado a cada imagem).
    """
    return EastTextDetector(east_model_path)
