from ocr.regions import top_to_bottom


def fake_region(x, y, confidence=0.9, size=40):
    """Região no formato do detect_text, com canto superior esquerdo em (x, y)"""
    return {
        "box": [[x, y + size], [x, y], [x + size, y], [x + size, y + size]],
        "confidence": confidence,
    }


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
