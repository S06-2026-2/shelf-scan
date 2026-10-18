from ocr.text_extractor import ocr_image


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

    def test_extracts_author_name_correctly(self, sample_image):
        """
        Teste para verificar se o OCR consegue extrair o nome do autor da capa do livro.
        A capa escreve tudo em maiúsculas (DOSTOIÉVSKI), por isso a comparação
        ignora maiúsculas e minúsculas
        """
        result = ocr_image(sample_image)
        assert "DOSTOI" in result.upper()

    def test_extracts_title_correctly(self, sample_image):
        """
        Teste para verificar se o OCR consegue extrair o título da capa do livro.
        Mesmo motivo da comparação em maiúsculas (MEMÓRIAS DO SUBSOLO)
        """
        result = ocr_image(sample_image)
        assert "SUBSOLO" in result.upper()