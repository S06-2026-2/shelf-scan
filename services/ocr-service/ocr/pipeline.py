"""Orquestra a leitura de uma foto: validação → detecção → OCR por região.

Devolve o dict no formato do contrato (docs/contrato-api.md, seção 4.2); a API só
traduz as exceções de domínio para HTTP e serializa a resposta.
"""

import time

from ocr.image_input import decode_image
from ocr.regions import top_to_bottom
from ocr.text_extractor import ocr_image, ocr_region

# Enquanto o agente estruturador (tarefa E) não existe, os campos saem sempre null
EMPTY_BOOK = {"nome": None, "volume": None, "autores": None, "editora": None}


def run_pipeline(img_bytes, detector, lang="por+eng"):
    """Lê uma foto e devolve a resposta do contrato

    ARGS:
        img_bytes: Conteúdo do arquivo enviado (bytes); o limite em MB já foi
                   verificado pela API
        detector: EastTextDetector já carregado (criado uma vez, fora daqui)
        lang: Idiomas do Tesseract

    RETURNS:
        Dicionário com:
            - books: lista com um livro (MVP), campos null até existir o agente
            - raw_text: texto lido, uma região por linha, de cima para baixo
            - result: "unstructured" se algo foi lido, "no_text" se nada foi
            - meta: {"detector": "east" | "fallback", "elapsed_ms": int}

    RAISES:
        OcrError (subclasses): a imagem foi recusada na validação (ver image_input)
    """
    start = time.perf_counter()
    img = decode_image(img_bytes)

    regions = detector.detect_text(img)
    if regions:
        readings = [ocr_region(img, region["box"], lang) for region in top_to_bottom(regions)]
        raw_text = "\n".join(reading["text"] for reading in readings if reading["text"])
        detector_used = "east"
    else:
        # Fallback: o EAST não achou caixas; o Tesseract tenta a imagem inteira
        raw_text = ocr_image(img, lang=lang).strip()
        detector_used = "fallback"

    return {
        "books": [dict(EMPTY_BOOK)],
        "raw_text": raw_text,
        "result": "unstructured" if raw_text else "no_text",
        "meta": {
            "detector": detector_used,
            "elapsed_ms": round((time.perf_counter() - start) * 1000),
        },
    }
