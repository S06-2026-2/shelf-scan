import json

import numpy as np
import pytest

from ocr.text_detector import EAST_MEAN, blob_from_image, east_forward, img_reshape

# (altura, largura) da entrada -> (altura, largura) esperada com max_side=1280
RESHAPE_CASES = [
    pytest.param((10, 5), (32, 32), id="minuscula-viraria-zero"),
    pytest.param((300, 500), (288, 512), id="menor-que-o-limite"),
    pytest.param((1761, 1181), (1280, 864), id="proporcao-da-amostra"),
    pytest.param((3000, 4000), (960, 1280), id="foto-paisagem"),
    pytest.param((4000, 3000), (1280, 960), id="foto-retrato"),
]


def blank_image(h, w):
    """Imagem BGR preta de h×w: o img_reshape só depende do tamanho"""
    return np.zeros((h, w, 3), dtype=np.uint8)


class TestImgReshape:
    """
        Testes do redimensionamento de entrada do EAST (não precisam do modelo)
    """

    @pytest.mark.parametrize("size, expected", RESHAPE_CASES)
    def test_output_dimensions(self, size, expected):
        """
        Imagens maiores que max_side são reduzidas preservando a proporção;
        as menores mantêm o tamanho. Em ambos os casos, cada lado é
        arredondado para o múltiplo de 32 mais próximo, com mínimo de 32.
        """
        img_redim, _, _ = img_reshape(blank_image(*size))

        assert img_redim.shape[:2] == expected

    @pytest.mark.parametrize("size, expected", RESHAPE_CASES)
    def test_dimensions_are_valid_for_east(self, size, expected):
        """
        Nenhuma dimensão pode ser zero (o cv2.resize quebraria), deixar de
        ser múltipla de 32 (as camadas do EAST não se encaixariam) ou
        passar do limite (o EAST ficaria lento).
        """
        img_redim, _, _ = img_reshape(blank_image(*size))

        for side in img_redim.shape[:2]:
            assert side >= 32
            assert side % 32 == 0
            assert side <= 1280

    @pytest.mark.parametrize("size, expected", RESHAPE_CASES)
    def test_ratios_map_back_to_original(self, size, expected):
        """
        rW e rH levam as coordenadas da imagem reduzida de volta à original:
        a largura reduzida vezes rW tem de dar a largura original (idem altura).
        São duas razões porque cada lado é arredondado separadamente.
        """
        h, w = size

        img_redim, rW, rH = img_reshape(blank_image(h, w))

        assert img_redim.shape[1] * rW == pytest.approx(w)
        assert img_redim.shape[0] * rH == pytest.approx(h)

    def test_custom_max_side(self):
        """Com max_side=640, a proporção da amostra vira 640×416"""
        img_redim, _, _ = img_reshape(blank_image(1761, 1181), max_side=640)

        assert img_redim.shape[:2] == (640, 416)

    @pytest.mark.parametrize("max_side", [1010, 16, 0])
    def test_invalid_max_side_raises(self, max_side):
        """
        max_side precisa ser múltiplo de 32 e pelo menos 32; caso contrário,
        o arredondamento poderia passar do limite (1010 viraria 1024)
        """
        with pytest.raises(ValueError):
            img_reshape(blank_image(100, 100), max_side=max_side)


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


def box_center(box):
    """Centro (x, y) de uma caixa de 4 pontos"""
    return np.array(box, dtype=np.float32).mean(axis=0)


class TestEastTextDetector:
    """
        Testes do EastTextDetector (detecção completa: entrada -> lista de regiões)
    """

    def test_detects_title_and_author(self, sample_image, text_detector):
        """
        Critério de pronto do t5: na capa de amostra, o detector encontra
        pelo menos 2 regiões (título e autor), todas com confiança >= 0,5
        """
        regions = text_detector.detect_text(sample_image)

        assert len(regions) >= 2
        assert all(region["confidence"] >= 0.5 for region in regions)

    def test_region_format_is_json_ready(self, sample_image, text_detector):
        """
        Cada região é {"box": [[x, y] x 4], "confidence": float}, com tipos
        nativos do Python (int e float, não numpy), para a API do t7
        serializar direto, como as regions do contrato do t1
        """
        regions = text_detector.detect_text(sample_image)

        for region in regions:
            assert set(region) == {"box", "confidence"}
            assert len(region["box"]) == 4
            for point in region["box"]:
                assert len(point) == 2
                assert all(type(coord) is int for coord in point)
            assert type(region["confidence"]) is float
        json.dumps(regions)  # não pode lançar TypeError

    def test_boxes_stay_inside_image(self, sample_image, text_detector):
        """
        Quando o texto encosta na borda, o EAST devolve pontos fora da imagem:
        nesta faixa do título, cortada em cima e embaixo, 4 pontos passam da
        borda. Eles precisam ser limitados à imagem para o recorte da região
        (OCR) não falhar.
        """
        strip = sample_image[1100:1300, 250:950].copy()
        h, w = strip.shape[:2]

        regions = text_detector.detect_text(strip)

        assert regions, "a faixa do título deveria ter texto detectado"
        for region in regions:
            for x, y in region["box"]:
                assert 0 <= x < w
                assert 0 <= y < h

    def test_blank_image_returns_empty_list(self, text_detector):
        """
        Sem texto, o detector devolve lista vazia em vez de lançar exceção;
        quem decide o que fazer (ex.: avisar o usuário) é o pipeline
        """
        blank = np.full((600, 400, 3), 255, dtype=np.uint8)

        assert text_detector.detect_text(blank) == []

    def test_boxes_are_in_original_scale(self, sample_image, text_detector):
        """
        O detector roda na imagem reduzida pelo img_reshape; as caixas
        precisam voltar à escala original (x rW, x rH). A referência é o
        próprio OpenCV reduzindo a imagem original por dentro: cada caixa
        dele deve ter uma do detector com o centro a poucos pixels. Sem as
        razões, a distância na amostra passaria de 200 px.

        A referência usa o mesmo modelo do detector (text_detector.model) em
        vez de carregar outro: cada EAST com os buffers do forward ocupa
        ~630 MB, e uma cópia extra esgotou a memória no Windows e no Docker.
        Mexer no setInputParams dele é seguro porque o detect_text
        reconfigura o tamanho de entrada a cada chamada.
        """
        img_redim, _, _ = img_reshape(sample_image)
        H, W = img_redim.shape[:2]
        text_detector.model.setInputParams(1.0, (W, H), EAST_MEAN, True)
        reference_boxes, _ = text_detector.model.detect(sample_image)

        regions = text_detector.detect_text(sample_image)

        centers = [box_center(region["box"]) for region in regions]
        for ref_box in reference_boxes:
            distance = min(np.linalg.norm(box_center(ref_box) - c) for c in centers)
            assert distance < 8
