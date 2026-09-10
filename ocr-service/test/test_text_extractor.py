import pytest

from ocr_engine import ocr_image


class TestOcrImage:
    """
        Testes de integração do OCR, usando uma imagem real de capa de livro
    """
    
    def test_returns_string(self, sample_image):
        """ 
        Sanity check básico:
        o OCR deve retornar uma string, mesmo que vazia
        """
        result = ocr_image(sample_image)
        assert isinstance(result, str)

    def test_returns_non_empty_text(self, sample_image):
        """
        Sanity check básico:
        numa capa de livro real, com texto grande e nítido, 
        o Tesseract deve extrair alguma coisa, mesmo que o
        idioma esteja errado. Se isso falhar, o problema está no pré-processamento
        (imagem binarizada ruim) ou no path do Tesseract, não no idioma.
        """
        result = ocr_image(sample_image)
        assert result.strip() != ""

    # mark.xfail é usado para marcar testes que devem falhar, mas não devem quebrar a suite de testes
    @pytest.mark.xfail(
        reason=(
            "ocr_image usa lang='eng' fixo, mas o texto da capa de amostra "
            "é em português (Dostoiévski / Memórias do Subsolo). Acentos e "
            "caracteres específicos do português tendem a ser reconhecidos "
            "errado com o modelo de idioma inglês. Remover este marcador "
            "quando lang for parametrizado ou trocado para 'por'/'por+eng'."
            "ainda não foi feita uma separação de ROI para capas poluídas,"
            "então o OCR pode falhar em extrair os textos corretos, mesmo"
            "que o idioma esteja correto. Remover este marcador quando a"
            "separação de ROI for implementada (com sucesso de preferência)."
        )
    )

    def test_extracts_author_name_correctly(self, sample_image):
        """Teste para verificar se o OCR consegue extrair o nome do autor da capa do livro"""
        result = ocr_image(sample_image)
        assert "Dostoi" in result

    @pytest.mark.xfail(
        reason="Mesmo motivo: dependência do idioma e falta de separação de ROI."
    )

    def test_extracts_title_correctly(self, sample_image):
        """Teste para verificar se o OCR consegue extrair o título da capa do livro"""
        result = ocr_image(sample_image)
        assert "Subsolo" in result