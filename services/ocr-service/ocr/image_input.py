"""Validação e decodificação da imagem recebida pelo ocr-service.

Ordem das verificações (docs/contrato-api.md, seção 5): o limite em MB fica na
API, antes de ler o arquivo; aqui ficam os magic numbers, a decodificação (com o
limite de pixels do OpenCV) e o tamanho mínimo.
"""

import cv2
import numpy as np

from ocr.exceptions import CorruptImageError, ImageTooLargeError, ImageTooSmallError, UnsupportedFormatError

# Assinaturas (magic numbers) dos formatos aceitos
JPEG_SIGNATURE = b"\xff\xd8\xff"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"

# Menor lado aceito, em pixels
# TODO: valor provisório; definir com o experimento de altura das letras
MIN_IMAGE_SIDE = 32


def decode_image(img_bytes):
    """Valida os bytes recebidos e decodifica a imagem

    ARGS:
        img_bytes: Conteúdo do arquivo enviado (bytes)

    RETURNS:
        Imagem com os 3 canais padrões do OpenCV (BGR)

    RAISES:
        UnsupportedFormatError: os bytes não começam como JPEG nem PNG
        CorruptImageError: começam como imagem, mas não decodificam
        ImageTooLargeError: passam do limite OPENCV_IO_MAX_IMAGE_PIXELS
        ImageTooSmallError: o menor lado tem menos de MIN_IMAGE_SIDE pixels
    """
    # O tipo vem dos próprios bytes: a extensão e o cabeçalho do cliente podem mentir
    if not img_bytes.startswith((JPEG_SIGNATURE, PNG_SIGNATURE)):
        raise UnsupportedFormatError("o arquivo não é JPEG nem PNG")

    try:
        img = cv2.imdecode(np.frombuffer(img_bytes, dtype=np.uint8), cv2.IMREAD_COLOR)
    except cv2.error as exc:
        # O limite de pixels chega como cv2.error citando CV_IO_MAX_IMAGE_PIXELS; a
        # versão do OpenCV é fixa no requirements e um teste quebra se a mensagem mudar
        if "CV_IO_MAX_IMAGE_PIXELS" in str(exc):
            raise ImageTooLargeError("a imagem passa do limite de pixels") from exc
        raise CorruptImageError("o OpenCV não conseguiu decodificar a imagem") from exc

    if img is None:
        raise CorruptImageError("o arquivo começa como imagem, mas não pôde ser decodificado")

    h, w = img.shape[:2]
    if min(h, w) < MIN_IMAGE_SIDE:
        raise ImageTooSmallError(f"o menor lado tem {min(h, w)} px; o mínimo é {MIN_IMAGE_SIDE}")

    return img
