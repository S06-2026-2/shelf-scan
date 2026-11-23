import cv2
import numpy as np

from scripts.avaliar_fotos import SAMPLES_DIR, count_boxes, count_keyword, load_gabarito, normalize


def white_png():
    """Imagem válida e em branco, só para passar pela decodificação"""
    ok, buffer = cv2.imencode(".png", np.full((400, 600, 3), 255, dtype=np.uint8))
    assert ok
    return buffer.tobytes()


def rect(x, y, w, h):
    """Região w × h na ordem do EAST: base-esquerda, topo-esquerda, topo-direita, base-direita"""
    return {"box": [[x, y + h], [x, y], [x + w, y], [x + w, y + h]], "confidence": 0.9}


class DetectorWithFixedRegions:
    """Detector falso: dois pedaços horizontais sobrepostos e uma caixa de lombada"""

    def detect_text(self, img):
        return [rect(0, 0, 100, 40), rect(70, 0, 60, 40), rect(300, 0, 30, 100)]


class TestNormalize:
    """
        Testes da comparação sem caixa e sem acento (o OCR erra acentos, e a fusão não resolve isso)
    """

    def test_uppercase_without_accents(self):
        assert normalize("Memórias do Além") == "MEMORIAS DO ALEM"

    def test_cedilla_and_tilde(self):
        """Ç e Ã também perdem o sinal, como o Tesseract costuma ler"""
        assert normalize("Tradução de São Paulo") == "TRADUCAO DE SAO PAULO"


class TestCountKeyword:
    """
        Testes da métrica: a palavra-chave só conta quando sai inteira numa mesma linha do raw_text
    """

    def test_word_inside_a_line_counts(self):
        """A pontuação colada não atrapalha: "SUBSOLO," conta para SUBSOLO"""
        assert count_keyword("MEMÓRIAS\nDO SUBSOLO,\n34", "SUBSOLO", limit=1) == 1

    def test_word_split_across_lines_does_not_count(self):
        """
        É o caso que a fusão quer resolver: o EAST cortou a palavra em duas
        caixas, e cada pedaço virou uma linha
        """
        assert count_keyword("SUB\nSOLO", "SUBSOLO", limit=1) == 0

    def test_case_and_accents_are_ignored(self):
        assert count_keyword("memórias", "MEMORIAS", limit=1) == 1

    def test_counts_each_line(self):
        """Numa foto com várias lombadas, cada linha com a palavra é um acerto"""
        raw_text = "FRIEREN\nROTEIRO\nFrieren e a Jornada\nARTE"

        assert count_keyword(raw_text, "FRIEREN", limit=8) == 2

    def test_capped_at_the_number_of_books(self):
        """Não passa do número de livros da foto, mesmo com o título repetido em mais linhas"""
        raw_text = "BLUE BOX\nBLUE\nBLUE"

        assert count_keyword(raw_text, "BLUE", limit=2) == 2

    def test_empty_text(self):
        """O no_text (raw_text vazio) dá zero, sem erro"""
        assert count_keyword("", "BLUE", limit=6) == 0


class TestCountBoxes:
    """
        Testes da contagem de caixas: as do EAST e as que sobram depois da fusão
    """

    def test_counts_before_and_after_the_fusion(self):
        """
        3 caixas do EAST viram 2 na leitura: os dois pedaços sobrepostos se
        juntam, e a caixa de lombada fica como veio
        """
        assert count_boxes(DetectorWithFixedRegions(), white_png()) == (3, 2)


class TestGabarito:
    """
        Testes do samples/gabarito.json, que a avaliação lê
    """

    def test_every_photo_exists(self):
        missing = [photo["arquivo"] for photo in load_gabarito() if not (SAMPLES_DIR / photo["arquivo"]).exists()]

        assert missing == []

    def test_keywords_are_normalized_and_long_enough(self):
        """
        As palavras já estão como a métrica compara; as curtas ("DO", "E",
        "PARA") ficam de fora porque aparecem no raw_text por acaso
        """
        for photo in load_gabarito():
            for word in photo["palavras"]:
                assert word == normalize(word), photo["arquivo"]
                assert len(word) >= 3, photo["arquivo"]

    def test_books_match_the_volumes(self):
        """O limite da contagem é o número de livros: um por volume, ou 1 sem volume"""
        for photo in load_gabarito():
            assert photo["livros"] == max(1, len(photo["volumes"])), photo["arquivo"]
