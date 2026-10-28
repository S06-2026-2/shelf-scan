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
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from api.schemas import ExtractionResponse
from ocr.exceptions import FileTooLargeError, OcrError
from ocr.pipeline import run_pipeline
from ocr.text_detector import EastTextDetector

logger = logging.getLogger(__name__)

SERVICE_DIR = Path(__file__).resolve().parent.parent
EAST_MODEL_PATH = SERVICE_DIR / "models" / "frozen_east_text_detection.pb"

# Maior arquivo aceito, verificado antes de qualquer outra validação (docs/contrato-api.md, seção 5)
MAX_UPLOAD_BYTES = 20 * 1024 * 1024

# Uma leitura por vez (docs/contrato-api.md, seção 5): o EAST é um modelo só, e o
# setInputParams troca o tamanho de entrada dele a cada foto
PIPELINE_LOCK = threading.Lock()


def run_pipeline_exclusive(img_bytes, detector):
    """Roda o pipeline com o lock: as outras leituras esperam a vez

    O "with" libera o lock mesmo quando o pipeline levanta um erro.

    ARGS / RETURNS:
        Os mesmos do run_pipeline
    """
    with PIPELINE_LOCK:
        return run_pipeline(img_bytes, detector)


# Status HTTP de cada code do catálogo de domínio (docs/contrato-api.md, seção 4.3).
# O domínio (ocr/) não conhece HTTP: a tradução fica toda aqui
ERROR_STATUS = {
    "file_too_large": 413,      # Content Too Large
    "unsupported_format": 415,  # Unsupported Media Type
    "corrupt_image": 422,       # Unprocessable Content: o arquivo chegou, mas não dá para ler
    "image_too_large": 422,
    "image_too_small": 422,
}


def error_response(status_code, code, message):
    """Monta o corpo único dos erros da API: {"code", "message"}

    O cliente age pelo code; a message é para quem lê o log ou desenvolve (cada
    cliente escreve a própria mensagem para o usuário).

    ARGS:
        status_code: Status HTTP
        code: Identificador estável do catálogo
        message: Descrição do erro, para o log
    """
    return JSONResponse(status_code=status_code, content={"code": code, "message": message})


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


@app.exception_handler(OcrError)
async def ocr_error_handler(request, exc):
    """Traduz um erro do domínio para o status e o code do catálogo"""
    # Um code sem status na tabela é erro de programação: vira 500 (um teste confere a tabela)
    return error_response(ERROR_STATUS.get(exc.code, 500), exc.code, str(exc))


@app.exception_handler(RequestValidationError)
async def invalid_request_handler(request, exc):
    """Requisição fora do contrato (como o campo da foto com outro nome): 422 no formato da API

    O FastAPI valida a requisição antes de chamar a rota; sem este handler, ele
    responderia no formato dele ({"detail": [...]}), sem o code.
    """
    fields = ", ".join(".".join(str(part) for part in error["loc"]) for error in exc.errors())
    return error_response(422, "invalid_request", f"campos ausentes ou inválidos: {fields}")


@app.exception_handler(Exception)
async def internal_error_handler(request, exc):
    """Erro fora do catálogo: 500 sem os detalhes internos

    O Starlette relança a exceção depois desta resposta, e o servidor (uvicorn)
    registra o traceback no log: o detalhe fica com quem mantém o serviço.
    """
    return error_response(500, "internal_error", "erro interno do ocr-service")


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
        Resposta do contrato (books, raw_text, result, meta), ou 503 service_not_ready
        enquanto o modelo não estiver carregado
    """
    # Sem o modelo não adianta validar a foto: o cliente tenta de novo em instantes
    status = getattr(app.state, "model_status", "loading")
    if status != "ready":
        message = "o modelo EAST ainda está carregando" if status == "loading" else "o modelo EAST não pôde ser carregado"
        return error_response(503, "service_not_ready", message)

    # Lê no máximo um byte além do limite: basta para saber se passou, sem carregar o resto na memória
    img_bytes = await image.read(MAX_UPLOAD_BYTES + 1)
    if len(img_bytes) > MAX_UPLOAD_BYTES:
        raise FileTooLargeError(f"o arquivo passa do limite de {MAX_UPLOAD_BYTES // (1024 * 1024)} MB")

    # O pipeline bloqueia por alguns segundos (CPU e Tesseract): roda numa thread
    # para o servidor continuar respondendo /live e /health enquanto isso
    return await run_in_threadpool(run_pipeline_exclusive, img_bytes, app.state.detector)
