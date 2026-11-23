import copy

import ocr.regions as ocr_regions
from ocr import text_extractor
from ocr.regions import top_to_bottom


def fake_region(x, y, confidence=0.9, size=40):
    """Região no formato do detect_text, com canto superior esquerdo em (x, y)"""
    return {
        "box": [[x, y + size], [x, y], [x + size, y], [x + size, y + size]],
        "confidence": confidence,
    }


def rect_region(x, y, w, h, confidence=0.9):
    """Região retangular w × h, na ordem do EAST: base-esquerda, topo-esquerda, topo-direita, base-direita"""
    return {
        "box": [[x, y + h], [x, y], [x + w, y], [x + w, y + h]],
        "confidence": confidence,
    }


# Par real do Blue Box (lombadas dos vols. 2 e 3): as duas caixas verticais se sobrepõem
# 82%, e juntas atravessariam a divisa entre as lombadas em x = 228 (medido na t4)
BLUE_BOX_TITLE_A = [[222, 291], [210, 104], [296, 99], [308, 285]]
BLUE_BOX_TITLE_B = [[220, 485], [237, 56], [322, 59], [306, 488]]


class TestTopToBottom:
    """
        Testes da ordenação das regiões (pelo topo; em empate, pela esquerda)
    """

    def test_sorts_by_top_then_left(self):
        bottom = fake_region(10, 300)
        top_right = fake_region(200, 50)
        top_left = fake_region(10, 50)

        ordered = top_to_bottom([bottom, top_right, top_left])

        assert ordered == [top_left, top_right, bottom]

    def test_does_not_modify_input(self):
        """Devolve uma lista nova; a lista do detector continua na ordem original"""
        regions = [fake_region(10, 300), fake_region(10, 50)]
        before = list(regions)

        top_to_bottom(regions)

        assert regions == before


class TestIsVertical:
    """
        Testes da regra de caixa vertical (lombada), a mesma que o ocr_region usa para girar o recorte
    """

    def test_tall_box_is_vertical(self):
        assert ocr_regions.is_vertical(rect_region(0, 0, 30, 100)["box"]) is True

    def test_wide_box_is_not_vertical(self):
        assert ocr_regions.is_vertical(rect_region(0, 0, 100, 30)["box"]) is False

    def test_square_box_is_not_vertical(self):
        """Quase quadrada (como o "34" da editora) conta como horizontal e pode ser unida"""
        assert ocr_regions.is_vertical(fake_region(0, 0)["box"]) is False

    def test_limit_is_strictly_above_the_ratio(self):
        """20 × 30 está exatamente em 1,5 e não é vertical; 20 × 31 já passa do limite"""
        assert ocr_regions.is_vertical(rect_region(0, 0, 20, 30)["box"]) is False
        assert ocr_regions.is_vertical(rect_region(0, 0, 20, 31)["box"]) is True

    def test_uses_the_ratio_of_the_ocr(self):
        """A constante é a do text_extractor, importada, e não uma cópia com o mesmo valor"""
        assert ocr_regions.VERTICAL_RATIO is text_extractor.VERTICAL_RATIO


class TestOverlapRatio:
    """
        Testes da sobreposição sobre a menor: interseção ÷ área da caixa menor (não é o IoU)
    """

    def test_identical_boxes(self):
        box = rect_region(0, 0, 100, 40)["box"]

        assert ocr_regions.overlap_ratio(box, box) == 1.0

    def test_disjoint_boxes(self):
        assert ocr_regions.overlap_ratio(rect_region(0, 0, 100, 40)["box"], rect_region(200, 0, 100, 40)["box"]) == 0.0

    def test_small_box_inside_big_is_full(self):
        """
        É o caso que separa esta medida do IoU: a menor está inteira dentro da
        maior, então dá 1,0 (o IoU daria 400 ÷ 10000 = 0,04)
        """
        big = rect_region(0, 0, 100, 100)["box"]
        small = rect_region(40, 40, 20, 20)["box"]

        assert ocr_regions.overlap_ratio(big, small) == 1.0

    def test_divides_by_the_smaller_area(self):
        """30 × 40 em comum, e a menor tem 60 × 40: metade dela (o IoU seria 0,23)"""
        a = rect_region(0, 0, 100, 40)["box"]
        b = rect_region(70, 0, 60, 40)["box"]

        assert ocr_regions.overlap_ratio(a, b) == 0.5

    def test_is_symmetric(self):
        a = rect_region(0, 0, 100, 40)["box"]
        b = rect_region(70, 0, 60, 40)["box"]

        assert ocr_regions.overlap_ratio(a, b) == ocr_regions.overlap_ratio(b, a)

    def test_tilted_boxes_use_the_exact_polygon(self):
        """
        Duas faixas inclinadas e paralelas que não se tocam: os retângulos retos
        em volta delas se sobrepõem, mas as caixas não
        """
        strip_a = [[0, 10], [10, 0], [100, 90], [90, 100]]
        strip_b = [[40, 10], [50, 0], [140, 90], [130, 100]]

        assert ocr_regions.overlap_ratio(strip_a, strip_b) == 0.0


class TestMergeBoxes:
    """
        Testes da fusão das caixas horizontais que se sobrepõem (critério da t4)
    """

    def test_overlapping_horizontal_boxes_become_one(self):
        """Metade da menor em comum: as duas viram o retângulo que contém as duas, na ordem do EAST"""
        merged = ocr_regions.merge_boxes([rect_region(0, 0, 100, 40), rect_region(70, 0, 60, 40)])

        assert [region["box"] for region in merged] == [[[0, 40], [0, 0], [130, 0], [130, 40]]]

    def test_keeps_the_highest_confidence(self):
        merged = ocr_regions.merge_boxes([rect_region(0, 0, 100, 40, 0.6), rect_region(70, 0, 60, 40, 0.9)])

        assert merged[0]["confidence"] == 0.9

    def test_separate_boxes_stay(self):
        regions = [rect_region(0, 0, 100, 40), rect_region(200, 0, 100, 40)]

        assert ocr_regions.merge_boxes(regions) == regions

    def test_small_overlap_stays_separate(self):
        """12 × 40 em comum é 20% da menor, abaixo dos 30%: são palavras vizinhas, não pedaços"""
        regions = [rect_region(0, 0, 100, 40), rect_region(88, 0, 60, 40)]

        assert len(ocr_regions.merge_boxes(regions)) == 2

    def test_threshold_is_inclusive(self):
        """18 × 40 em comum é exatamente 30% da menor, e já junta"""
        regions = [rect_region(0, 0, 100, 40), rect_region(82, 0, 60, 40)]

        assert len(ocr_regions.merge_boxes(regions)) == 1

    def test_chain_merges_transitively(self):
        """A toca B e B toca C, mas A não toca C: as três viram uma caixa só"""
        regions = [rect_region(0, 0, 100, 40), rect_region(50, 0, 100, 40), rect_region(100, 0, 100, 40)]

        merged = ocr_regions.merge_boxes(regions)

        assert [region["box"] for region in merged] == [[[0, 40], [0, 0], [200, 0], [200, 40]]]

    def test_vertical_boxes_never_merge(self):
        """O par real do Blue Box se sobrepõe 82%, mas são caixas de lombada: ficam como estão"""
        regions = [{"box": BLUE_BOX_TITLE_A, "confidence": 0.8}, {"box": BLUE_BOX_TITLE_B, "confidence": 0.9}]

        assert ocr_regions.merge_boxes(regions) == regions

    def test_vertical_and_horizontal_do_not_merge(self):
        """Basta uma das duas ser vertical para o par ficar separado"""
        regions = [rect_region(0, 0, 30, 100), rect_region(0, 40, 100, 30)]

        assert len(ocr_regions.merge_boxes(regions)) == 2

    def test_square_boxes_merge_like_horizontal(self):
        regions = [fake_region(0, 0), fake_region(20, 0)]

        assert len(ocr_regions.merge_boxes(regions)) == 1

    def test_does_not_modify_input(self):
        """
        A lista do detector continua igual, na mesma ordem e com as mesmas
        caixas: o debug_detector ainda pode desenhar as originais. As
        confianças são diferentes para uma reordenação aparecer
        """
        regions = [rect_region(0, 0, 100, 40, 0.6), rect_region(70, 0, 60, 40, 0.9), rect_region(300, 0, 50, 40, 0.7)]
        before = copy.deepcopy(regions)

        ocr_regions.merge_boxes(regions)

        assert regions == before

    def test_empty_list(self):
        """Sem caixas (o caso do fallback), devolve uma lista vazia"""
        assert ocr_regions.merge_boxes([]) == []

    def test_keeps_the_detector_format(self):
        """
        Mesmo formato do detect_text: {"box": [[x, y] × 4] com int do Python,
        "confidence": float}, para o pipeline não mudar
        """
        merged = ocr_regions.merge_boxes([rect_region(0, 0, 100, 40, 0.6), rect_region(70, 0, 60, 40, 0.9),
                                          rect_region(300, 0, 50, 40, 0.7)])

        for region in merged:
            assert set(region) == {"box", "confidence"}
            assert len(region["box"]) == 4
            assert all(type(value) is int for point in region["box"] for value in point)
            assert type(region["confidence"]) is float
