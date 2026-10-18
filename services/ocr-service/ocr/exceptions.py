"""Exceções de domínio do ocr-service.

Cada erro do catálogo (docs/contrato-api.md, seção 4.3) é uma subclasse de
OcrError com um identificador estável em `code`. A API traduz a exceção para
o status HTTP e devolve o `code`, para o front e o bot decidirem o que
oferecer ao usuário sem depender do texto da mensagem.
"""


class OcrError(Exception):
    """Erro de domínio do ocr-service; cada subclasse define o seu `code`"""

    code = "ocr_error"


class UnsupportedFormatError(OcrError):
    """O arquivo não é JPEG nem PNG (verificado pelos magic numbers)"""

    code = "unsupported_format"


class CorruptImageError(OcrError):
    """O arquivo começa como imagem, mas não pôde ser decodificado"""

    code = "corrupt_image"


class ImageTooLargeError(OcrError):
    """A imagem passa do limite de pixels (OPENCV_IO_MAX_IMAGE_PIXELS)"""

    code = "image_too_large"


class ImageTooSmallError(OcrError):
    """A imagem está abaixo do tamanho mínimo aceito"""

    code = "image_too_small"
