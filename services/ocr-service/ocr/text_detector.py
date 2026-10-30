import cv2
import numpy as np

# Médias RGB do ImageNet, usadas no treino do EAST (subtraídas de cada canal)
EAST_MEAN = (123.68, 116.78, 103.94)

# Camadas de saída do EAST: probabilidade de texto (scores) e geometria das caixas
EAST_OUTPUT_LAYERS = ["feature_fusion/Conv_7/Sigmoid", "feature_fusion/concat_3"]

def load_east_model(model_path):
    """
    Carrega o modelo EAST para detecção de texto

    Args:
        model_path: Caminho para o arquivo do modelo EAST

    Returns:
        Modelo EAST carregado

    """

    return cv2.dnn.readNet(model_path)


def img_reshape(img, max_side=1280):
    """
    Redimensiona a imagem para dimensões múltiplas de 32, exigência da rede EAST.

    ARGS:
        img: Imagem com os 3 canais padrões do OpenCV (BGR)
        max_side: Lado máximo da imagem redimensionada (deve ser múltiplo de 32 e pelo menos 32)

    RETURNS:
        Tupla contendo:
            - img_reshaped: Imagem redimensionada para múltiplos de 32
            - rW: Fator de escala da largura (original / redimensionada)
            - rH: Fator de escala da altura (original / redimensionada)
    """

    if (max_side < 32 or max_side % 32 != 0):
        raise ValueError(f"max_side deve ser múltiplo de 32 e pelo menos 32; recebido {max_side}")

    h, w = img.shape[:2]

    scale = min(1.0, max_side / max(h, w))

    new_h = max(32, round((h * scale)/32) * 32)
    new_w = max(32, round((w * scale)/32) * 32)

    img_reshaped = cv2.resize(img, (new_w, new_h), interpolation=cv2.INTER_AREA)
    
    rW = w / new_w
    rH = h / new_h

    return img_reshaped, rW, rH

def blob_from_image(img):
    """
    Cria um blob a partir da imagem BGR redimensionada para ser usado como entrada no modelo EAST.

    ARGS:
        img: Imagem com os 3 canais padrões do OpenCV (BGR) e redimensionada para múltiplos de 32

    RETURNS:
        blob: Blob criado a partir da imagem
    """

    h, w = img.shape[:2]

    blob = cv2.dnn.blobFromImage(img, 1.0, (w, h), EAST_MEAN, swapRB=True, crop=False)
    return blob


def east_forward(net, blob):
    """Roda a rede EAST sobre um blob e devolve as duas saídas

    ARGS:
        net: Rede EAST carregada por load_east_model
        blob: Blob criado por blob_from_image

    RETURNS:
        Tupla (scores, geometry):
            - scores: 1×1×H/4×W/4, probabilidade de texto em cada bloco de 4×4 px
            - geometry: 1×5×H/4×W/4, distâncias até as 4 bordas da caixa + ângulo
    """
    
    net.setInput(blob)
    return net.forward(EAST_OUTPUT_LAYERS)


class EastTextDetector:
    """
    Detector de texto baseado no modelo EAST 
    

    """

    def __init__(self, model_path, confidence=0.5, nms=0.4, max_side=1280):
        """
        Inicializa o detector de texto EAST

        ARGS:
            model_path: Caminho para o arquivo do modelo EAST
            confidence: Limite de confiança para considerar uma detecção válida
            nms: Limite de supressão não máxima (NMS) para filtrar caixas sobrepostas
            max_side: Lado máximo da imagem redimensionada (deve ser múltiplo de 32 e pelo menos 32)
        """

        self.confidence = confidence
        self.nms = nms
        self.max_side = max_side

        self.model = cv2.dnn.TextDetectionModel_EAST(model_path)
        self.model.setConfidenceThreshold(confidence)
        self.model.setNMSThreshold(nms)

    def detect_text(self, img):
        """
        Detecta regiões de texto na imagem usando o modelo EAST
        
        ARGS:
            img: Imagem com os 3 canais padrões do OpenCV BGR

        RETURNS:
            regions: Lista de dicionários, cada um contendo:
                - "box": Lista de 4 pontos (x, y) representando a caixa delimitadora do texto
                - "confidence": Confiança da detecção

        """

        h_origin, w_origin = img.shape[:2]

        img_reshaped, rW, rH = img_reshape(img, self.max_side)

        h, w = img_reshaped.shape[:2]
        self.model.setInputParams(size=(w, h), scale=1.0, mean=EAST_MEAN, swapRB=True)

        boxes, confidences = self.model.detect(img_reshaped)

        regions = []
        for box, conf in zip(boxes, confidences):
            pts = np.array(box, dtype=np.float32) * (rW, rH)

            pts[:,0] = np.clip(pts[:,0], 0, w_origin - 1)
            pts[:,1] = np.clip(pts[:,1], 0, h_origin - 1)

            region = {"box": np.rint(pts).astype(int).tolist(),
             "confidence": float(conf)}

            regions.append(region)

        return regions

