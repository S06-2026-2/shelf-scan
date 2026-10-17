from pathlib import Path

import cv2
import numpy as np
import pytest

SAMPLES_DIR = Path(__file__).resolve().parent.parent / "samples"

# Anotação manual das fotos de estante em samples/ (coordenadas da foto):
#   spines: x de cada divisa entre lombadas, da esquerda para a direita
#   volumes: número impresso em cada lombada, na mesma ordem
#   title / author / number: faixa vertical (y inicial, y final) de cada informação
SHELVES = {
    "blue_box": {
        "file": "lombada_blue_box.jpeg",  # JBC: texto lido de cima para baixo
        "spines": [35, 130, 228, 325, 425, 522, 620],
        "volumes": ["1", "2", "3", "4", "5", "6"],
        "title": (0, 600),
        "author": (1040, 1420),
        "number": (700, 840),
    },
    "frieren": {
        "file": "lombada_frieren.jpeg",  # Planet Manga: texto lido de baixo para cima
        "spines": [15, 130, 235, 335, 440, 545, 650, 755, 855],
        "volumes": ["4", "6", "7", "8", "9", "10", "11", "12"],
        "title": (230, 880),
        "author": (1060, 1370),
        "number": (920, 1020),
    },
}

# Falhas conhecidas do detector nestas fotos. Ficam como xfail para
# documentar o que ainda não funciona sem quebrar a suíte; quando
# passarem a funcionar, o pytest mostra XPASS e o marcador pode sair.
KNOWN_MISSES = {
    ("blue_box", "title", "4"): "o título do vol. 4 do Blue Box não é detectado",
}


@pytest.fixture(scope="module")
def shelf_regions(text_detector):
    """Roda o detector uma vez em cada foto de estante e guarda as regiões

    Cada detect_text custa ~1,5 s; os testes só consultam o resultado.
    """
    regions = {}
    for name, shelf in SHELVES.items():
        img = cv2.imread(str(SAMPLES_DIR / shelf["file"]))
        assert img is not None, f"Amostra não encontrada em samples/: {shelf['file']}"
        regions[name] = text_detector.detect_text(img)
    return regions


def box_bounds(region):
    """(x mínimo, x máximo, y mínimo, y máximo) da caixa de uma região"""
    box = np.array(region["box"])
    return box[:, 0].min(), box[:, 0].max(), box[:, 1].min(), box[:, 1].max()


def count_in_band(regions, shelf, volume, band):
    """Quantas caixas têm o centro dentro da lombada do volume, na faixa pedida"""
    i = shelf["volumes"].index(volume)
    x0, x1 = shelf["spines"][i], shelf["spines"][i + 1]
    y0, y1 = shelf[band]
    count = 0
    for region in regions:
        cx, cy = np.array(region["box"], dtype=np.float32).mean(axis=0)
        if x0 <= cx < x1 and y0 <= cy < y1:
            count += 1
    return count


def spine_cases(band):
    """Um caso por lombada de cada foto, com xfail nas falhas conhecidas"""
    cases = []
    for name, shelf in SHELVES.items():
        for volume in shelf["volumes"]:
            reason = KNOWN_MISSES.get((name, band, volume))
            marks = [pytest.mark.xfail(reason=reason)] if reason else []
            cases.append(pytest.param(name, volume, marks=marks, id=f"{name}-vol{volume}"))
    return cases


class TestSpineCoverage:
    """
        Cada lombada das fotos reais precisa ter o título e o autor detectados
    """

    @pytest.mark.parametrize("shelf_name, volume", spine_cases("title"))
    def test_title_is_detected(self, shelf_regions, shelf_name, volume):
        shelf = SHELVES[shelf_name]

        assert count_in_band(shelf_regions[shelf_name], shelf, volume, "title") >= 1

    @pytest.mark.parametrize("shelf_name, volume", spine_cases("author"))
    def test_author_is_detected(self, shelf_regions, shelf_name, volume):
        shelf = SHELVES[shelf_name]

        assert count_in_band(shelf_regions[shelf_name], shelf, volume, "author") >= 1

    @pytest.mark.xfail(reason=(
        "o EAST não detecta números isolados de 1 ou 2 dígitos: nenhum dos "
        "14 números de volume das duas fotos é encontrado"
    ))
    @pytest.mark.parametrize("shelf_name", SHELVES)
    def test_volume_numbers_are_detected(self, shelf_regions, shelf_name):
        shelf = SHELVES[shelf_name]

        for volume in shelf["volumes"]:
            assert count_in_band(shelf_regions[shelf_name], shelf, volume, "number") >= 1


class TestSpineBoxShape:
    """
        Formato das caixas em lombadas, onde o texto corre na vertical
    """

    @pytest.mark.parametrize("shelf_name", SHELVES)
    def test_boxes_are_vertical(self, shelf_regions, shelf_name):
        """
        Em lombada o texto é vertical, então pelo menos 90% das caixas
        devem ser mais altas do que largas. Isso também avisa o OCR de que
        os recortes precisarão ser girados antes do Tesseract.
        """
        regions = shelf_regions[shelf_name]
        vertical = 0
        for region in regions:
            x_min, x_max, y_min, y_max = box_bounds(region)
            if (y_max - y_min) > (x_max - x_min):
                vertical += 1

        assert vertical >= 0.9 * len(regions)

    @pytest.mark.parametrize("shelf_name", [
        pytest.param("blue_box", marks=pytest.mark.xfail(
            reason="9 caixas de título se inclinam e atravessam a divisa entre duas lombadas")),
        "frieren",
    ])
    def test_boxes_do_not_cross_spines(self, shelf_regions, shelf_name):
        """
        Uma caixa que atravessa a divisa mistura o texto de dois livros no
        OCR. A margem de 10 px tolera a imprecisão da anotação manual.
        """
        dividers = SHELVES[shelf_name]["spines"][1:-1]

        for region in shelf_regions[shelf_name]:
            x_min, x_max, _, _ = box_bounds(region)
            assert not any(x_min < d - 10 and x_max > d + 10 for d in dividers)
