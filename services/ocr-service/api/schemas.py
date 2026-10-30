"""Formato das respostas da API, conforme docs/contrato-api.md (seção 4.2).

O FastAPI usa estes modelos para validar o que a rota devolve e para gerar o
esquema no /docs, que o front e o bot podem consultar.
"""

from typing import Literal

from pydantic import BaseModel


class Book(BaseModel):
    """Um livro; null quando o campo não foi identificado"""

    nome: str | None
    volume: str | None
    autores: list[str] | None
    editora: str | None


class ErrorResponse(BaseModel):
    """Corpo de todos os erros da API: o cliente age pelo code; a message é para o log"""

    code: str
    message: str


class ExtractionMeta(BaseModel):
    """Como a leitura foi feita: qual detector e quanto tempo levou"""

    detector: Literal["east", "fallback"]
    elapsed_ms: int


class ExtractionResponse(BaseModel):
    """Resposta de sucesso de POST /v1/extraction"""

    books: list[Book]
    raw_text: str
    result: Literal["structured", "unstructured", "no_text"]
    meta: ExtractionMeta
