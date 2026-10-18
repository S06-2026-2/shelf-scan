"""API HTTP do ocr-service (FastAPI).

Camada fina sobre o núcleo (ocr/): carrega o detector, expõe as rotas do contrato
(docs/contrato-api.md, seção 3) e traduz o domínio para HTTP.
"""

import logging
import threading
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Annotated

from fastapi import FastAPI, File, UploadFile
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse

from api.schemas import ExtractionResponse
from ocr.pipeline import run_pipeline
from ocr.text_detector import EastTextDetector

logger = logging.getLogger(__name__)

SERVICE_DIR = Path(__file__).resolve().parent.parent
EAST_MODEL_PATH = SERVICE_DIR / "models" / "frozen_east_text_detection.pb"


def load_detector(state, model_path):
    """Carrega o EastTextDetector e guarda o resultado no estado da aplicação

    Roda numa thread separada. Enquanto ela trabalha, state.model_status é
    "loading"; ao terminar, vira "ready" ou, se a carga falhar, "error".

    ARGS:
        state: Estado da aplicação (app.state do FastAPI)
        model_path: Caminho do modelo EAST
    """
    try:
        state.detector = EastTextDetector(str(model_path))
        state.model_status = "ready"
    except Exception:
        # O processo continua vivo (/live) para o container e o log poderem ser inspecionados
        logger.exception("Falha ao carregar o modelo EAST em %s", model_path)
        state.model_status = "error"


@asynccontextmanager
async def lifespan(app):
    # O servidor começa a responder na hora; o modelo (~630 MB, ~1 s) carrega em paralelo
    app.state.detector = None
    app.state.model_status = "loading"
    threading.Thread(target=load_detector, args=(app.state, EAST_MODEL_PATH), daemon=True).start()
    yield


app = FastAPI(
    title="Shelf Scan OCR Service",
    version="0.1.0",
    description="Detecção (EAST) e leitura (Tesseract) de texto em capas e lombadas",
    lifespan=lifespan,
)


@app.get("/live")
def live():
    """Vivo: o processo responde (não diz nada sobre o modelo)"""
    return {"status": "alive"}


@app.get("/health")
def health():
    """Pronto: o modelo foi carregado e o serviço aceita fotos

    RETURNS:
        200 {"status": "ready"}, ou 503 {"status": "loading" | "error"}
    """
    status = getattr(app.state, "model_status", "loading")
    if status != "ready":
        return JSONResponse(status_code=503, content={"status": status})
    return {"status": "ready"}


@app.post("/v1/extraction", response_model=ExtractionResponse)
async def extraction(image: Annotated[UploadFile, File(description="Foto da capa ou da lombada (JPEG ou PNG)")]):
    """Lê a foto enviada no campo multipart "image" e devolve os livros encontrados

    RETURNS:
        Resposta do contrato (books, raw_text, result, meta)
    """
    img_bytes = await image.read()

    # O pipeline bloqueia por alguns segundos (CPU e Tesseract): roda numa thread
    # para o servidor continuar respondendo /live e /health enquanto isso
    return await run_in_threadpool(run_pipeline, img_bytes, app.state.detector)
