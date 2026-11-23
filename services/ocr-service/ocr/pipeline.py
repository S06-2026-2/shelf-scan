"""Orquestra a leitura de uma foto: validação → detecção → fusão das caixas → OCR por região.

Devolve o dict no formato do contrato (docs/contrato-api.md, seção 4.2); a API só
traduz as exceções de domínio para HTTP e serializa a resposta.
"""

import time

from ocr.image_input import decode_image
from ocr.regions import merge_boxes, top_to_bottom
from ocr.text_extractor import ocr_image, ocr_region

# O pipeline só lê o texto: o livro sai com os campos null, e quem os preenche é o
# agente estruturador (structurer.structure), que a API chama depois do pipeline
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
            - books: lista com um livro (MVP), com os campos null (o agente preenche depois)
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
        # Os pedaços de uma palavra que o EAST cortou em caixas sobrepostas viram uma
        # caixa só; as caixas de lombada ficam como vieram (ver regions.merge_boxes)
        regions = merge_boxes(regions)
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
