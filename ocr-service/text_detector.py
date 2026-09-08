import cv2

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
    Redimensiona a imagem para o tamanho padrão do EAST 
    (mínimo de 32 pixels e múltiplo de 32)

    ARGS:
        img: Imagem com os 3 canais padrões do OpenCV (BGR)

    RETURNS:
        Tupla com Imagem redimensionada para o tamanho padrão do EAST e dimensões da imagem original
    """

    h, w, _ = img.shape

    # Redimensiona a imagem para o tamanho padrão do EAST
    new_h = (h // 32) * 32 # obs: // é a divisão inteira, * 32 é para garantir que a altura seja múltiplo de 32
    new_w = (w // 32) * 32 

    img_reshaped = cv2.resize(img, (new_w, new_h))

    return (img_reshaped, h, w,) 