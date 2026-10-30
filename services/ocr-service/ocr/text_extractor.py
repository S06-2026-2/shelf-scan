import cv2
import numpy as np
import pytesseract

# Margem do recorte de cada região, como fração do lado menor da caixa
REGION_MARGIN = 0.15

# Caixa com altura maior que 1,5 × a largura é texto vertical (lombada)
VERTICAL_RATIO = 1.5


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

def ocr_image(img, lang="eng"):
    """Faz o OCR de uma imagem BGR

    ARGS:
        img: Imagem com os 3 canais padrzões do OpenCV (BGR)
        lang: Idiomas do Tesseract (ex.: "eng", "por+eng"); o padrão "eng"
              mantém o comportamento anterior

    RETURNS:
        Texto extraído da imagem
    """

    img_ocr = pipeline_preprocessing(img)
    return pytesseract.image_to_string(img_ocr, lang=lang)


def crop_region(img, box, margin=REGION_MARGIN):
    """Recorta a região de uma caixa do EAST, endireitada e com margem

    ARGS:
        img: Imagem com os 3 canais padrões do OpenCV (BGR)
        box: 4 pontos [x, y] na ordem do EAST: base-esquerda, topo-esquerda,
             topo-direita, base-direita (a caixa pode estar inclinada)
        margin: Margem em volta da caixa, como fração do lado menor dela

    RETURNS:
        Recorte retangular e endireitado da caixa (BGR), com a margem;
        a imagem original não é alterada
    """
    bl, tl, tr, br = np.array(box, dtype=np.float32)

    # Lados reais da caixa: o maior entre os dois lados opostos
    w = max(np.linalg.norm(tr - tl), np.linalg.norm(br - bl))
    h = max(np.linalg.norm(bl - tl), np.linalg.norm(br - tr))

    # Margem proporcional ao lado menor: numa lombada fina, uma margem
    # proporcional à altura invadiria a lombada vizinha
    pad = margin * min(w, h)

    # Destino deslocado pela margem: o warp traz junto os pixels em volta da caixa
    src = np.array([bl, tl, tr, br], dtype=np.float32)
    dst = np.array([[pad, pad + h], [pad, pad], [pad + w, pad], [pad + w, pad + h]], dtype=np.float32)
    matrix = cv2.getPerspectiveTransform(src, dst)

    # (largura, altura), como o OpenCV espera; onde a margem passa da borda da
    # imagem, BORDER_REPLICATE repete a borda em vez de pintar de preto
    # (o preto vira "texto fantasma" no Tesseract)
    size = (int(round(w + 2 * pad)), int(round(h + 2 * pad)))
    return cv2.warpPerspective(img, matrix, size, borderMode=cv2.BORDER_REPLICATE)


def read_line(img, lang):
    """Lê uma linha de texto com o Tesseract e calcula a confiança média

    ARGS:
        img: Imagem já preparada para o OCR (ex.: recorte em escala de cinza),
             com uma única linha de texto
        lang: Idiomas do Tesseract (ex.: "por+eng")

    RETURNS:
        Dicionário {"text": str, "confidence": float}, com a média simples da
        confiança das palavras, de 0 a 1; sem nenhuma palavra lida,
        {"text": "", "confidence": 0.0}
    """
    # --psm 7: a imagem é uma linha só, e não uma página com layout
    data = pytesseract.image_to_data(img, lang=lang, config="--psm 7",
                                     output_type=pytesseract.Output.DICT)

    # Confiança -1 marca blocos do layout que não são palavras
    words = []
    confidences = []
    for text, conf in zip(data["text"], data["conf"]):
        if text.strip() and float(conf) >= 0:
            words.append(text.strip())
            confidences.append(float(conf))

    if not words:
        return {"text": "", "confidence": 0.0}

    # O Tesseract dá a confiança de 0 a 100; o detector usa de 0 a 1
    return {"text": " ".join(words), "confidence": sum(confidences) / len(confidences) / 100}


def ocr_region(img, box, lang="por+eng", margin=REGION_MARGIN):
    """Lê o texto de uma caixa do EAST, girando a caixa quando ela é vertical

    Caixas verticais (lombadas) são lidas nos dois sentidos, porque cada
    editora escreve num sentido (a JBC de cima para baixo, a Planet Manga de
    baixo para cima); fica a leitura de maior confiança.

    ARGS:
        img: Imagem com os 3 canais padrões do OpenCV (BGR)
        box: 4 pontos [x, y] na ordem do EAST (ver crop_region)
        lang: Idiomas do Tesseract
        margin: Margem do recorte, como fração do lado menor da caixa

    RETURNS:
        Dicionário {"text": str, "confidence": float}, com a confiança de 0 a 1
    """
    # Só cinza: o Tesseract já binariza por dentro, e o Otsu piora os recortes
    gray = cv2.cvtColor(crop_region(img, box, margin), cv2.COLOR_BGR2GRAY)

    h, w = gray.shape[:2]
    if h <= VERTICAL_RATIO * w:
        return read_line(gray, lang)

    # Em empate, o max fica com o primeiro da lista: o anti-horário,
    # padrão de lombada da JBC e dos livros americanos
    readings = [
        read_line(cv2.rotate(gray, cv2.ROTATE_90_COUNTERCLOCKWISE), lang),
        read_line(cv2.rotate(gray, cv2.ROTATE_90_CLOCKWISE), lang),
    ]
    return max(readings, key=lambda reading: reading["confidence"])
