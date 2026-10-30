import re

import pytest

from ocr import exceptions
from ocr.exceptions import OcrError

# Identificadores combinados no contrato (docs/contrato-api.md, seção 4.3).
# O front e o bot decidem o que fazer a partir deles: renomear um quebra os dois.
CONTRACT_CODES = {"file_too_large", "unsupported_format", "corrupt_image", "image_too_large", "image_too_small"}

CATALOG = [
    cls for cls in vars(exceptions).values()
    if isinstance(cls, type) and issubclass(cls, OcrError) and cls is not OcrError
]


class TestErrorCatalog:
    """
        Testes do catálogo de erros de domínio (não precisam de Tesseract)
    """

    def test_codes_match_contract(self):
        """Os códigos existentes são exatamente os documentados no contrato"""
        assert {cls.code for cls in CATALOG} == CONTRACT_CODES

    def test_codes_are_unique(self):
        """Dois erros com o mesmo código seriam indistinguíveis para o front"""
        codes = [cls.code for cls in CATALOG]

        assert len(codes) == len(set(codes))

    @pytest.mark.parametrize("error_class", CATALOG, ids=lambda cls: cls.__name__)
    def test_code_is_snake_case(self, error_class):
        """Identificador estável: minúsculas e _ , sem acento nem espaço"""
        assert re.fullmatch(r"[a-z]+(_[a-z]+)*", error_class.code)

    def test_instance_keeps_message_and_code(self):
        """A mensagem é para quem lê o log; o código é para quem decide o que fazer"""
        error = exceptions.UnsupportedFormatError("o arquivo começa com %PDF")

        assert isinstance(error, OcrError)
        assert error.code == "unsupported_format"
        assert str(error) == "o arquivo começa com %PDF"
