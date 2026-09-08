import cv2
import pytesseract


def grayscale_image(img):
    """Transforma imagem BGR em grayscale

    ARGS:
        img: Imagem com os 3 canais padrões do OpenCV (BGR)

    RETURNS:
        Imagem em escala de cinza (monocromática)
    """
    return cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)


def binary_image(img):
    """Transforma uma imagem em binaria usando o método de Otsu

    ARGS:
        img: Imagem com os 3 canais padrões do OpenCV (BGR)

    RETURNS:
        Imagem binária (preto e branco puro) ideal para o OCR
    """
    img_gray = grayscale_image(img)

    ret, img_bin = cv2.threshold(img_gray, 0, 255, cv2.THRESH_OTSU)

    return img_bin


def pipeline_preprocessing(img):
    """Faz o tratamento de uma imagem BGR para o OCR

    ARGS:
        img: Imagem com os 3 canais padrões do OpenCV (BGR)

    RETURNS:
        Imagem processada e binarizada própria para o OCR
    """
    img_processed = binary_image(img)

    return img_processed

def ocr_image(img):
    """Faz o OCR de uma imagem BGR

    ARGS:
        img: Imagem com os 3 canais padrzões do OpenCV (BGR)

    RETURNS:
        Texto extraído da imagem
    """

    img_ocr = pipeline_preprocessing(img)
    return pytesseract.image_to_string(img_ocr, lang="eng")
