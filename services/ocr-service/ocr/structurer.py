"""Agente estruturador: transforma o texto bruto do OCR nos campos do livro.

O raw_text vai para um LLM (Gemini), que devolve nome, volume, autores e editora num
JSON com schema fixo. O agente é opcional: sem chave, com erro ou acima do tempo
limite, a resposta do pipeline segue como "unstructured", com o texto bruto.
"""

import logging
import time
from concurrent.futures import ThreadPoolExecutor

from google.genai import types
from pydantic import BaseModel

logger = logging.getLogger(__name__)

# Modelo estável mais rápido da linha 3.5, com free tier
MODEL = "gemini-3.5-flash-lite"

# O nosso limite: passado esse tempo, a foto segue sem o agente
LLM_TIMEOUT_S = 5

# Prazo que o SDK manda ao servidor do Gemini, em milissegundos; o servidor recusa menos que 10 s
SERVER_DEADLINE_MS = 10_000

EMPTY_BOOK = {"nome": None, "volume": None, "autores": None, "editora": None}

PROMPT = """Você recebe as linhas de texto que um OCR leu numa foto da capa ou da lombada de um livro, de cima para baixo. As palavras podem vir partidas em duas linhas, com letras trocadas ou com ruído.

Identifique o livro e devolva: nome (o título), volume, autores e editora.
- autores: quem escreveu e, em mangás e HQs, também quem desenhou (roteiro e arte). Não inclua tradutores, revisores nem outros créditos.
- volume: só quando o texto mostrar uma marca de volume junto do número, como "Vol.", "Volume", "nº" ou "#". Sem essa marca, use null.
- Se aparecerem vários volumes da mesma série, devolva o livro uma vez só.
- Use null em qualquer campo que não der para identificar com segurança. Não invente: use só o que está no texto, corrigindo apenas erros evidentes do OCR.

Texto lido:
"""

# Criado uma vez só, no módulo: uma chamada que estourou o tempo continua na thread
# dela até o Gemini responder, e a foto seguinte não precisa esperar por ela
EXECUTOR = ThreadPoolExecutor(max_workers=4, thread_name_prefix="llm")


class LlmBook(BaseModel):
    """Schema que o Gemini preenche: os mesmos campos do Book da API (api/schemas.py)"""

    nome: str | None
    volume: str | None
    autores: list[str] | None
    editora: str | None


class GeminiLlm:
    """Pede ao Gemini os campos do livro a partir do texto do OCR

    O cliente vem de fora (genai.Client() na API, um falso nos testes), assim como
    o run_pipeline recebe o detector pronto.
    """

    def __init__(self, client):
        self.client = client

    def extract_book(self, raw_text):
        """Faz uma chamada ao Gemini e devolve o livro que ele achou

        ARGS:
            raw_text: Texto lido pelo OCR, uma região por linha

        RETURNS:
            Dicionário com nome, volume, autores e editora, ou None se o modelo
            não devolveu um JSON no schema

        RAISES:
            Erros do SDK (rede, cota, chave inválida); quem trata é o structure
        """
        response = self.client.models.generate_content(
            model=MODEL,
            contents=PROMPT + raw_text,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=LlmBook,
                http_options=types.HttpOptions(timeout=SERVER_DEADLINE_MS),
            ),
        )
        if response.parsed is None:
            return None
        return response.parsed.model_dump()


def clean_text(value):
    """Tira os espaços das pontas; texto vazio vira None (o contrato não usa "")"""
    if value is None:
        return None
    return value.strip() or None


def clean_book(book):
    """Deixa o livro do LLM no formato do contrato: só os 4 campos, sem "" nem lista vazia"""
    autores = [autor.strip() for autor in (book.get("autores") or []) if autor.strip()]
    return {
        "nome": clean_text(book.get("nome")),
        "volume": clean_text(book.get("volume")),
        "autores": autores or None,
        "editora": clean_text(book.get("editora")),
    }


def structure(result, llm, timeout_s=LLM_TIMEOUT_S):
    """Preenche o livro da resposta do pipeline com o que o LLM extrair do texto bruto

    ARGS:
        result: Resposta do run_pipeline (não é alterada)
        llm: Objeto com extract_book(raw_text), como a GeminiLlm; None quando não há chave
        timeout_s: Quanto esperar o LLM antes de seguir sem ele

    RETURNS:
        Nova resposta no formato do contrato:
            - "structured" com o livro, se o LLM achou o título
            - "unstructured" com o livro vazio, se não achou, falhou ou demorou
            - a própria resposta recebida, se não há llm ou se o OCR não leu nada
        Quando o LLM é chamado, meta.elapsed_ms passa a incluir o tempo dele.
    """
    if llm is None or result["result"] == "no_text":
        return result

    start = time.perf_counter()
    try:
        future = EXECUTOR.submit(llm.extract_book, result["raw_text"])
        book = future.result(timeout=timeout_s)
    except Exception as exc:
        # A chave vai num cabeçalho da requisição, então não aparece na mensagem do erro
        logger.warning("agente estruturador falhou (%r); a foto segue sem estrutura", exc)
        book = None
    agent_ms = round((time.perf_counter() - start) * 1000)

    meta = {**result["meta"], "elapsed_ms": result["meta"]["elapsed_ms"] + agent_ms}
    book = clean_book(book) if book else None
    if book and book["nome"]:
        return {**result, "books": [book], "result": "structured", "meta": meta}
    return {**result, "books": [dict(EMPTY_BOOK)], "result": "unstructured", "meta": meta}
