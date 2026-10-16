import numpy as np

from ocr.text_detector import EAST_MEAN, blob_from_image, east_forward, img_reshape


class TestBlobFromImage:
    """
        Testes do blob de entrada do EAST (não precisam do modelo)
    """

    def test_blob_shape_matches_image(self, sample_image):
        """
        O blob deve ter a forma 1×3×H×W, com H e W da imagem redimensionada.
        Se H e W saírem trocados, a imagem foi distorcida antes de entrar na
        rede: o blobFromImage recebe o tamanho como (largura, altura), ao
        contrário do img.shape, que é (altura, largura).
        """
        img, _, _ = img_reshape(sample_image)
        H, W = img.shape[:2]

        blob = blob_from_image(img)

        assert blob.shape == (1, 3, H, W)

    def test_blob_is_rgb_minus_mean(self, sample_image):
        """
        O EAST foi treinado com imagens RGB das quais foi subtraída a média
        do ImageNet. Errar isso não dá erro nenhum, só piora a detecção em
        silêncio, então o teste confere o primeiro canal do blob: deve ser o
        vermelho da imagem (canal 2 no BGR do OpenCV) menos a média do R.
        """
        img, _, _ = img_reshape(sample_image)

        blob = blob_from_image(img)

        expected_red = img[:, :, 2].astype(np.float32) - EAST_MEAN[0]
        np.testing.assert_allclose(blob[0, 0], expected_red, atol=1e-3)


class TestEastForward:
    """
        Testes de integração do forward do EAST, usando a imagem real de amostra
    """

    def test_output_shapes(self, sample_image, east_net):
        """
        Cada célula das saídas corresponde a um bloco de 4×4 px da entrada:
        scores tem 1 canal (probabilidade de texto) e geometry tem 5
        (4 distâncias até as bordas da caixa + ângulo).
        """
        img, _, _ = img_reshape(sample_image)
        H, W = img.shape[:2]

        scores, geometry = east_forward(east_net, blob_from_image(img))

        assert scores.shape == (1, 1, H // 4, W // 4)
        assert geometry.shape == (1, 5, H // 4, W // 4)

    def test_scores_are_probabilities(self, sample_image, east_net):
        """
        scores sai de uma Sigmoid, então fica entre 0 e 1. Numa capa com
        texto grande e nítido, a rede deve ter alta confiança em algum bloco;
        se não tiver, o pré-processamento do blob provavelmente está errado.
        """
        img, _, _ = img_reshape(sample_image)

        scores, _ = east_forward(east_net, blob_from_image(img))

        assert scores.min() >= 0
        assert scores.max() <= 1
        assert scores.max() > 0.5
