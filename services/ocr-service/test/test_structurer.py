import inspect
import logging
import threading
import time
from types import SimpleNamespace

import pytest

from api.schemas import Book
from ocr.structurer import LLM_TIMEOUT_S, MODEL, PROMPT, GeminiLlm, LlmBook, structure

EMPTY_BOOK = {"nome": None, "volume": None, "autores": None, "editora": None}
RAW_TEXT = "MEMÓRIAS\nDO\nSUBSOLO\nDOSTOIÉVSKI\n34\neditoral"
FULL_BOOK = {"nome": "Memórias do Subsolo", "volume": None, "autores": ["Dostoiévski"], "editora": "Editora 34"}


def pipeline_result(raw_text=RAW_TEXT, elapsed_ms=1000):
    """Resposta do run_pipeline antes do agente: livro vazio e "unstructured" ("no_text" sem texto)"""
    return {
        "books": [dict(EMPTY_BOOK)],
        "raw_text": raw_text,
        "result": "unstructured" if raw_text else "no_text",
        "meta": {"detector": "east", "elapsed_ms": elapsed_ms},
    }


def book(**fields):
    """Livro como o LLM devolve: os 4 campos, null nos que não vierem"""
    return {**EMPTY_BOOK, **fields}


def agent_warnings(caplog):
    return [record for record in caplog.records
            if record.name == "ocr.structurer" and record.levelno >= logging.WARNING]


class FakeLlm:
    """LLM falso: devolve o livro combinado ou levanta um erro, e anota o texto que recebeu

    O structure recebe o llm como parâmetro (como o run_pipeline recebe o detector);
    por isso o teste troca o Gemini por esta versão controlada, sem rede nem chave.
    """

    def __init__(self, book=None, error=None, delay_s=0):
        self.book = book
        self.error = error
        self.delay_s = delay_s
        self.calls = []

    def extract_book(self, raw_text):
        self.calls.append(raw_text)
        if self.delay_s:
            time.sleep(self.delay_s)
        if self.error:
            raise self.error
        return self.book


class HangingLlm:
    """LLM falso travado: só responde quando o teste libera (simula o Gemini lento demais)"""

    def __init__(self):
        self.release = threading.Event()

    def extract_book(self, raw_text):
        self.release.wait(timeout=5)
        return dict(FULL_BOOK)


@pytest.fixture
def hanging_llm():
    llm = HangingLlm()
    yield llm
    llm.release.set()  # devolve a thread do executor ao fim do teste


class TestStructured:
    """
        O modelo achou o título: o livro vai preenchido e o resultado vira "structured"
    """

    def test_fills_the_book(self):
        out = structure(pipeline_result(), FakeLlm(dict(FULL_BOOK)))

        assert out["books"] == [FULL_BOOK]
        assert out["result"] == "structured"

    def test_keeps_the_rest_of_the_response(self):
        out = structure(pipeline_result(), FakeLlm(dict(FULL_BOOK)))

        assert set(out) == {"books", "raw_text", "result", "meta"}
        assert out["raw_text"] == RAW_TEXT
        assert out["meta"]["detector"] == "east"

    def test_sends_the_raw_text_to_the_llm(self):
        llm = FakeLlm(dict(FULL_BOOK))

        structure(pipeline_result(), llm)

        assert llm.calls == [RAW_TEXT]

    def test_title_alone_is_enough(self):
        """Só o título já conta; os outros campos podem vir null"""
        out = structure(pipeline_result(), FakeLlm(book(nome="Blue Box")))

        assert out["result"] == "structured"
        assert out["books"] == [book(nome="Blue Box")]

    def test_book_has_only_the_contract_fields(self):
        """Um campo a mais vindo do LLM não passa para a resposta"""
        out = structure(pipeline_result(), FakeLlm({**FULL_BOOK, "isbn": "9788573261851"}))

        assert set(out["books"][0]) == set(EMPTY_BOOK)

    def test_does_not_change_the_pipeline_result(self):
        """O structure monta uma resposta nova; o dict que veio do pipeline fica como estava"""
        result = pipeline_result()

        structure(result, FakeLlm(dict(FULL_BOOK)))

        assert result == pipeline_result()


class TestWithoutTitle:
    """
        O modelo não achou o título: volta o livro vazio e "unstructured", com o texto bruto
    """

    @pytest.mark.parametrize("nome", [None, "", "   "])
    def test_other_fields_are_discarded(self, nome):
        llm = FakeLlm(book(nome=nome, volume="3", autores=["Kouji Miura"], editora="JBC"))

        out = structure(pipeline_result(), llm)

        assert out["result"] == "unstructured"
        assert out["books"] == [EMPTY_BOOK]
        assert out["raw_text"] == RAW_TEXT

    def test_llm_returns_nothing(self):
        """Ex.: o Gemini não devolveu um JSON no schema (GeminiLlm devolve None)"""
        out = structure(pipeline_result(), FakeLlm(None))

        assert out["result"] == "unstructured"
        assert out["books"] == [EMPTY_BOOK]


class TestCleaning:
    """
        O que o modelo devolve é limpo para seguir o contrato: sem espaços nas pontas e sem ""
    """

    def test_strips_the_text_fields(self):
        llm = FakeLlm(book(nome="  Blue Box ", volume=" 3 ", autores=["Kouji Miura"], editora=" JBC  "))

        out = structure(pipeline_result(), llm)

        assert out["books"] == [book(nome="Blue Box", volume="3", autores=["Kouji Miura"], editora="JBC")]

    @pytest.mark.parametrize("blank", ["", "   "])
    def test_blank_text_becomes_null(self, blank):
        out = structure(pipeline_result(), FakeLlm(book(nome="Blue Box", volume=blank, editora=blank)))

        assert out["books"] == [book(nome="Blue Box")]

    def test_cleans_the_authors(self):
        llm = FakeLlm(book(nome="Frieren", autores=[" Kanehito Yamada ", "", "  ", "Tsukasa Abe"]))

        out = structure(pipeline_result(), llm)

        assert out["books"][0]["autores"] == ["Kanehito Yamada", "Tsukasa Abe"]

    @pytest.mark.parametrize("autores", [[], ["", "  "]])
    def test_no_authors_left_becomes_null(self, autores):
        out = structure(pipeline_result(), FakeLlm(book(nome="Frieren", autores=autores)))

        assert out["books"][0]["autores"] is None


class TestAgentSkipped:
    """
        Sem texto lido ou sem chave, o agente nem é chamado: a resposta do pipeline volta igual
    """

    def test_no_text(self):
        llm = FakeLlm(dict(FULL_BOOK))

        out = structure(pipeline_result(raw_text=""), llm)

        assert llm.calls == []
        assert out == pipeline_result(raw_text="")

    def test_without_llm(self, caplog):
        """Sem GEMINI_API_KEY a API passa llm=None; não é falha, então nada de aviso a cada foto"""
        with caplog.at_level(logging.WARNING):
            out = structure(pipeline_result(), None)

        assert out == pipeline_result()
        assert agent_warnings(caplog) == []


class TestFailures:
    """
        Se o Gemini falha ou demora, a foto continua sendo atendida: "unstructured", e o erro vai para o log
    """

    @pytest.mark.parametrize("error", [
        RuntimeError("429 RESOURCE_EXHAUSTED"),
        ValueError("JSON quebrado"),
        ConnectionError("sem rede"),
    ])
    def test_error_becomes_unstructured(self, error):
        out = structure(pipeline_result(), FakeLlm(error=error))

        assert out["result"] == "unstructured"
        assert out["books"] == [EMPTY_BOOK]
        assert out["raw_text"] == RAW_TEXT

    def test_error_goes_to_the_log(self, caplog):
        with caplog.at_level(logging.WARNING):
            structure(pipeline_result(), FakeLlm(error=RuntimeError("429 RESOURCE_EXHAUSTED")))

        assert len(agent_warnings(caplog)) == 1

    def test_slow_llm_is_cut(self, hanging_llm):
        start = time.perf_counter()

        out = structure(pipeline_result(), hanging_llm, timeout_s=0.2)

        assert time.perf_counter() - start < 2
        assert out["result"] == "unstructured"
        assert out["books"] == [EMPTY_BOOK]

    def test_cut_goes_to_the_log(self, hanging_llm, caplog):
        with caplog.at_level(logging.WARNING):
            structure(pipeline_result(), hanging_llm, timeout_s=0.2)

        assert len(agent_warnings(caplog)) == 1

    def test_default_limit_is_5_seconds(self):
        assert LLM_TIMEOUT_S == 5
        assert inspect.signature(structure).parameters["timeout_s"].default == LLM_TIMEOUT_S


class TestElapsed:
    """
        meta.elapsed_ms passa a somar o tempo do agente ao do OCR
    """

    def test_adds_the_agent_time(self):
        out = structure(pipeline_result(elapsed_ms=1000), FakeLlm(dict(FULL_BOOK), delay_s=0.2))

        assert type(out["meta"]["elapsed_ms"]) is int
        assert 1190 <= out["meta"]["elapsed_ms"] < 3000  # 1000 do OCR + ~200 do agente

    def test_counts_the_wait_until_the_cut(self, hanging_llm):
        out = structure(pipeline_result(elapsed_ms=1000), hanging_llm, timeout_s=0.2)

        assert 1190 <= out["meta"]["elapsed_ms"] < 3000


class FakeModels:
    """Imita client.models do SDK: anota os argumentos e devolve um objeto com .parsed"""

    def __init__(self, parsed):
        self.parsed = parsed
        self.calls = []

    def generate_content(self, **kwargs):
        self.calls.append(kwargs)
        return SimpleNamespace(parsed=self.parsed)


def fake_client(parsed=None):
    return SimpleNamespace(models=FakeModels(parsed))


def sent_to_sdk(parsed=None):
    """Chama a GeminiLlm com um cliente falso e devolve o que ela passou ao generate_content"""
    client = fake_client(parsed)
    GeminiLlm(client).extract_book(RAW_TEXT)
    assert len(client.models.calls) == 1
    return client.models.calls[0]


class TestGeminiLlm:
    """
        GeminiLlm só chama o SDK do jeito combinado (cliente falso, sem rede nem chave)
    """

    def test_uses_the_chosen_model(self):
        assert MODEL == "gemini-3.5-flash-lite"
        assert sent_to_sdk()["model"] == MODEL

    def test_sends_the_prompt_followed_by_the_raw_text(self):
        assert sent_to_sdk()["contents"] == PROMPT + RAW_TEXT

    def test_asks_for_json_in_the_book_schema(self):
        config = sent_to_sdk()["config"]

        assert config.response_mime_type == "application/json"
        assert config.response_schema is LlmBook

    def test_server_deadline_is_10_seconds(self):
        """Em milissegundos; o servidor recusa menos que 10 s (o corte de 5 s é do structure)"""
        assert sent_to_sdk()["config"].http_options.timeout == 10000

    def test_returns_the_book_as_dict(self):
        client = fake_client(parsed=LlmBook(**FULL_BOOK))

        assert GeminiLlm(client).extract_book(RAW_TEXT) == FULL_BOOK

    def test_without_parsed_json_returns_none(self):
        assert GeminiLlm(fake_client(parsed=None)).extract_book(RAW_TEXT) is None


class TestSchemaAndPrompt:
    """
        O schema do LLM bate com o livro do contrato, e o prompt guarda as regras da rodada 3
    """

    def test_llm_book_matches_the_api_book(self):
        llm_fields = {name: field.annotation for name, field in LlmBook.model_fields.items()}
        api_fields = {name: field.annotation for name, field in Book.model_fields.items()}

        assert llm_fields == api_fields

    def test_prompt_rules(self):
        text = PROMPT.lower()

        assert "roteiro e arte" in text  # quem desenha conta como autor
        assert "tradutores" in text      # tradutor fica de fora
        assert '"vol."' in text          # volume só com marca clara
        assert PROMPT.endswith("Texto lido:\n")  # o raw_text entra logo depois
