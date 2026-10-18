from pathlib import Path

import cv2
import numpy as np

from scripts.debug_detector import collect_images, draw_regions, process_image, top_to_bottom

SAMPLE_PATH = Path(__file__).resolve().parent.parent / "samples" / "memorias_subsolo.jpg"


def fake_region(x, y, confidence=0.9, size=40):
    """Região no formato do detect_text, com canto superior esquerdo em (x, y)"""
    return {
        "box": [[x, y + size], [x, y], [x + size, y], [x + size, y + size]],
        "confidence": confidence,
    }


class TestCollectImages:
    """
        Testes da expansão de arquivos e pastas de entrada
    """

    def test_folder_expands_to_images_only(self, tmp_path):
        """
        Uma pasta vira as imagens dentro dela, em ordem alfabética; outros
        arquivos e as saídas do próprio script (*_boxes) ficam de fora
        """
        for name in ["b.png", "a.JPG", "notas.txt", "a_boxes.jpg"]:
            (tmp_path / name).touch()

        images = collect_images([tmp_path])

        assert [p.name for p in images] == ["a.JPG", "b.png"]

    def test_file_is_passed_through(self, tmp_path):
        """Um arquivo informado diretamente entra como está"""
        photo = tmp_path / "lombada.jpg"

        assert collect_images([photo]) == [photo]


class TestTopToBottom:
    """
        Testes da numeração das caixas (pelo topo; em empate, pela esquerda)
    """

    def test_sorts_by_top_then_left(self):
        bottom = fake_region(10, 300)
        top_right = fake_region(200, 50)
        top_left = fake_region(10, 50)

        ordered = top_to_bottom([bottom, top_right, top_left])

        assert ordered == [top_left, top_right, bottom]


class TestDrawRegions:
    """
        Testes do desenho das caixas
    """

    def test_returns_annotated_copy(self):
        """
        O desenho sai numa cópia do mesmo tamanho; a imagem original não pode
        ser alterada, porque o pipeline ainda vai usá-la para o OCR
        """
        img = np.full((200, 300, 3), 255, dtype=np.uint8)

        annotated = draw_regions(img, [fake_region(50, 80)])

        assert annotated.shape == img.shape
        assert (img == 255).all()
        assert (annotated != 255).any()


class TestProcessImage:
    """
        Teste de ponta a ponta do script na imagem de amostra
    """

    def test_saves_debug_image(self, text_detector, tmp_path):
        """
        Gera <nome>_boxes.jpg na pasta de saída, com o mesmo tamanho da
        imagem de entrada
        """
        out_path = process_image(text_detector, SAMPLE_PATH, tmp_path)

        saved = cv2.imread(str(out_path))
        assert out_path.name == "memorias_subsolo_boxes.jpg"
        assert saved is not None
        assert saved.shape == cv2.imread(str(SAMPLE_PATH)).shape

    def test_unreadable_file_is_skipped(self, text_detector, tmp_path):
        """Um arquivo que não é imagem é ignorado (None), sem derrubar o script"""
        not_an_image = tmp_path / "foto.jpg"
        not_an_image.write_text("isto não é uma imagem")

        assert process_image(text_detector, not_an_image, tmp_path) is None
