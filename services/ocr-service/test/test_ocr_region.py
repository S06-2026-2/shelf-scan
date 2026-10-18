import inspect
from pathlib import Path

import cv2
import numpy as np
import pytest

from ocr.text_extractor import ocr_image, ocr_region

SAMPLES_DIR = Path(__file__).resolve().parent.parent / "samples"

# Caixas fixas (4 pontos na ordem do EAST: base-esq, topo-esq, topo-dir,
# base-dir), copiadas da saída do EastTextDetector, com o trecho que a
# leitura precisa conter. São fixas de propósito: estes testes avaliam só
# o OCR da região, mesmo que o detector mude.
READ_CASES = [
    pytest.param("memorias_subsolo.jpg", [[457, 1453], [457, 1423], [657, 1417], [657, 1447]],
                 "TRADUÇÃO", id="capa-horizontal-com-acento"),
    pytest.param("memorias_subsolo.jpg", [[364, 1490], [364, 1462], [492, 1460], [492, 1487]],
                 "BORIS", id="capa-horizontal"),
    pytest.param("memorias_subsolo.jpg", [[513, 1495], [511, 1469], [793, 1457], [794, 1483]],
                 "SCHNAIDERMAN", id="capa-horizontal-longa"),
    pytest.param("lombada_blue_box.jpeg", [[108, 510], [126, 39], [213, 42], [195, 512]],
                 "BLUE BOX", id="lombada-cima-para-baixo-titulo"),
    pytest.param("lombada_blue_box.jpeg", [[76, 1389], [62, 1098], [101, 1095], [115, 1388]],
                 "MIURA", id="lombada-cima-para-baixo-autor"),
    pytest.param("lombada_frieren.jpeg", [[277, 580], [266, 378], [293, 376], [305, 579]],
                 "JORNADA", id="lombada-baixo-para-cima-titulo"),
    pytest.param("lombada_frieren.jpeg", [[380, 531], [373, 269], [400, 269], [407, 531]],
                 "ALÉM", id="lombada-baixo-para-cima-com-acento"),
    pytest.param("lombada_frieren.jpeg", [[494, 1255], [489, 1098], [514, 1096], [519, 1254]],
                 "TSUKASA", id="lombada-baixo-para-cima-autor"),
]

BORIS_BOX = [[364, 1490], [364, 1462], [492, 1460], [492, 1487]]


@pytest.fixture(scope="module")
def samples():
    """Carrega as fotos de samples/ usadas nos casos de leitura, uma vez só"""
    images = {}
    for name in ("memorias_subsolo.jpg", "lombada_blue_box.jpeg", "lombada_frieren.jpeg"):
        img = cv2.imread(str(SAMPLES_DIR / name))
        assert img is not None, f"Amostra não encontrada em samples/: {name}"
        images[name] = img
    return images


def white_image():
    return np.full((300, 500, 3), 255, dtype=np.uint8)


class TestOcrRegionReading:
    """
        Testes de leitura de regiões reais (precisam do Tesseract com por e eng)
    """

    @pytest.mark.parametrize("sample, box, expected", READ_CASES)
    def test_reads_expected_text(self, samples, sample, box, expected):
        """
        Caixas horizontais são lidas como estão. Caixas verticais (lombadas)
        precisam ser giradas, e o sentido muda com a editora: o Blue Box (JBC)
        é lido de cima para baixo e o Frieren (Planet Manga), de baixo para
        cima. Os acentos (TRADUÇÃO, ALÉM) só saem certos com lang="por+eng".
        """
        result = ocr_region(samples[sample], box)

        assert expected in result["text"].upper()


class TestOcrRegionContract:
    """
        Formato do resultado e casos de borda do ocr_region
    """

    def test_returns_text_and_normalized_confidence(self, samples):
        """
        O resultado é {"text": str, "confidence": float}, com a confiança de
        0 a 1 (a mesma escala do detector), não de 0 a 100 como o Tesseract
        """
        result = ocr_region(samples["memorias_subsolo.jpg"], BORIS_BOX)

        assert set(result) == {"text", "confidence"}
        assert type(result["text"]) is str
        assert type(result["confidence"]) is float
        assert 0.5 < result["confidence"] <= 1

    def test_blank_region_returns_empty_text(self):
        """Sem texto na região, o resultado é texto vazio com confiança 0"""
        box = [[150, 180], [150, 120], [350, 120], [350, 180]]

        assert ocr_region(white_image(), box) == {"text": "", "confidence": 0.0}

    def test_margin_outside_image_does_not_create_text(self):
        """
        Numa caixa encostada no canto, a margem do recorte sai da imagem. Se
        essa área for preenchida de preto (o padrão do warpPerspective), o
        Tesseract "lê" a faixa preta: numa imagem toda branca saiu "Do", com
        confiança 0,51. Replicar a borda da imagem evita o texto fantasma.
        """
        box = [[0, 60], [0, 0], [200, 0], [200, 60]]

        assert ocr_region(white_image(), box)["text"] == ""

    def test_does_not_modify_image(self, samples):
        """O recorte e a rotação não podem alterar a imagem original"""
        img = samples["memorias_subsolo.jpg"]
        before = img.copy()

        ocr_region(img, BORIS_BOX)

        assert np.array_equal(img, before)


class TestOcrImageLanguage:
    """
        O ocr_image ganha o parâmetro lang sem mudar o comportamento atual
    """

    def test_default_language_is_still_english(self):
        """O padrão continua "eng", para os testes atuais do ocr_image não mudarem"""
        assert inspect.signature(ocr_image).parameters["lang"].default == "eng"

    def test_accepts_portuguese(self, sample_image):
        result = ocr_image(sample_image, lang="por+eng")

        assert isinstance(result, str)
        assert result.strip() != ""
