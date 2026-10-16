import cv2

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


def img_reshape(img):
    """
    Redimensiona a imagem para dimensões múltiplas de 32, exigência da rede EAST.

    ARGS:
        img: Imagem com os 3 canais padrões do OpenCV (BGR)

    RETURNS:
        Tupla contendo:
            - img_reshaped: Imagem redimensionada para múltiplos de 32
            - H: Altura da imagem redimensionada (usada nos parâmetros de entrada)
            - W: Largura da imagem redimensionada (usada nos parâmetros de entrada)
    """
    h, w = img.shape[:2]

    # Garante que as dimensões sejam múltiplas de 32
    new_h = (h // 32) * 32 
    new_w = (w // 32) * 32 

    img_reshaped = cv2.resize(img, (new_w, new_h))
    
    H, W = img_reshaped.shape[:2]

    return img_reshaped, H, W

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

