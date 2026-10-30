import struct
import zlib
from pathlib import Path

import cv2
import numpy as np
import pytest

from ocr.exceptions import CorruptImageError, ImageTooLargeError, ImageTooSmallError, UnsupportedFormatError
from ocr.image_input import MIN_IMAGE_SIDE, decode_image

SAMPLE_PATH = Path(__file__).resolve().parent.parent / "samples" / "memorias_subsolo.jpg"


def png_bytes(img):
    """Codifica uma imagem do OpenCV como PNG em memória"""
    ok, buffer = cv2.imencode(".png", img)
    assert ok
    return buffer.tobytes()


def huge_blank_png(side):
    """PNG em cinza, todo preto, de side x side pixels, montado sem alocar a imagem

    A compressão é feita linha por linha: o arquivo fica com poucas centenas de KB,
    mas declara side x side pixels, como uma "bomba de descompressão".
    """
    def chunk(kind, data):
        crc = zlib.crc32(kind + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", crc)

    compressor = zlib.compressobj(9)
    row = b"\x00" * (side + 1)  # byte de filtro da linha + side pixels
    idat = b"".join(compressor.compress(row) for _ in range(side)) + compressor.flush()
    ihdr = struct.pack(">IIBBBBB", side, side, 8, 0, 0, 0, 0)  # 8 bits, cinza
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")


class TestValidImages:
    """
        Imagens aceitas: viram uma matriz BGR de 3 canais
    """

    def test_decodes_jpeg_sample(self):
        img = decode_image(SAMPLE_PATH.read_bytes())

        assert img.shape == (1761, 1181, 3)

    def test_decodes_png(self):
        original = np.full((100, 200, 3), 128, dtype=np.uint8)

        img = decode_image(png_bytes(original))

        assert np.array_equal(img, original)

    def test_grayscale_png_becomes_bgr(self):
        """O detector espera 3 canais, mesmo quando o PNG enviado é em cinza"""
        gray = np.full((100, 200), 200, dtype=np.uint8)

        img = decode_image(png_bytes(gray))

        assert img.shape == (100, 200, 3)

    def test_minimum_side_is_accepted(self):
        """O limite é inclusivo: o menor lado exatamente igual ao mínimo passa"""
        img = np.zeros((MIN_IMAGE_SIDE, 200, 3), dtype=np.uint8)

        assert decode_image(png_bytes(img)).shape[0] == MIN_IMAGE_SIDE


class TestRejectedImages:
    """
        Cada recusa levanta o erro do catálogo que o front usa para decidir o que oferecer
    """

    @pytest.mark.parametrize("data", [
        pytest.param(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3 conteudo de um pdf", id="pdf"),
        pytest.param(b"GIF89a\x01\x00\x01\x00", id="gif"),
        pytest.param(b"", id="vazio"),
    ])
    def test_unsupported_format(self, data):
        """O tipo é decidido pelos magic numbers, e não pela extensão ou pelo cabeçalho"""
        with pytest.raises(UnsupportedFormatError):
            decode_image(data)

    def test_corrupt_png(self):
        """Começa com a assinatura de PNG, mas o resto é lixo"""
        with pytest.raises(CorruptImageError):
            decode_image(b"\x89PNG\r\n\x1a\n" + bytes(range(256)) * 8)

    def test_truncated_jpeg(self):
        """Um JPEG cortado na metade (upload interrompido) não decodifica"""
        data = SAMPLE_PATH.read_bytes()

        with pytest.raises(CorruptImageError):
            decode_image(data[: len(data) // 2])

    def test_too_small(self):
        img = np.zeros((MIN_IMAGE_SIDE - 12, 200, 3), dtype=np.uint8)

        with pytest.raises(ImageTooSmallError):
            decode_image(png_bytes(img))

    def test_too_large_is_refused_before_allocating(self):
        """
        8000 x 8000 = 64 milhões de pixels, acima do limite de 55 milhões
        (OPENCV_IO_MAX_IMAGE_PIXELS, no Dockerfile e no conftest). O OpenCV recusa
        ao ler o cabeçalho, antes de reservar ~190 MB. Este teste também quebra se
        uma atualização do OpenCV mudar a mensagem que decode_image reconhece.
        """
        with pytest.raises(ImageTooLargeError):
            decode_image(huge_blank_png(8000))
