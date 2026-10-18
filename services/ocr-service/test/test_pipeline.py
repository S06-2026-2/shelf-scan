import json
from pathlib import Path

import cv2
import numpy as np
import pytest

from ocr.exceptions import UnsupportedFormatError
from ocr.pipeline import run_pipeline

SAMPLE_PATH = Path(__file__).resolve().parent.parent / "samples" / "memorias_subsolo.jpg"


class DetectorWithoutRegions:
    """Detector falso que nunca acha caixas, para forçar o fallback

    O run_pipeline recebe o detector como parâmetro (injeção de dependência);
    por isso o teste consegue trocar o EAST por esta versão controlada.
    """

    def detect_text(self, img):
        return []


@pytest.fixture(scope="module")
def cover_result(text_detector):
    """Resposta do pipeline para a capa de amostra, calculada uma vez só"""
    return run_pipeline(SAMPLE_PATH.read_bytes(), text_detector)


def white_png():
    ok, buffer = cv2.imencode(".png", np.full((400, 600, 3), 255, dtype=np.uint8))
    assert ok
    return buffer.tobytes()


class TestContractFormat:
    """
        A resposta segue o contrato: books, raw_text, result e meta
    """

    def test_top_level_fields(self, cover_result):
        assert set(cover_result) == {"books", "raw_text", "result", "meta"}

    def test_books_is_a_list_with_one_empty_book(self, cover_result):
        """MVP: sempre um livro; sem o agente, todos os campos são null"""
        assert cover_result["books"] == [
            {"nome": None, "volume": None, "autores": None, "editora": None}
        ]

    def test_meta(self, cover_result):
        assert cover_result["meta"]["detector"] == "east"
        assert type(cover_result["meta"]["elapsed_ms"]) is int
        assert cover_result["meta"]["elapsed_ms"] > 0

    def test_is_json_serializable(self, cover_result):
        """A API devolve o dict direto: nada de tipos do numpy"""
        json.dumps(cover_result)


class TestCoverReading:
    """
        Leitura da capa de amostra, de ponta a ponta
    """

    def test_result_is_unstructured(self, cover_result):
        """Algo foi lido, mas sem o agente nada foi estruturado em campos"""
        assert cover_result["result"] == "unstructured"

    def test_raw_text_has_one_region_per_line_top_to_bottom(self, cover_result):
        """
        Cada linha do raw_text é uma região, de cima para baixo: "TRADUÇÃO"
        fica acima de "BORIS", que fica acima de "editora" na capa
        """
        lines = cover_result["raw_text"].upper().splitlines()
        position = {word: next(i for i, line in enumerate(lines) if word in line)
                    for word in ("TRADUÇÃO", "BORIS", "EDITORA")}

        assert position["TRADUÇÃO"] < position["BORIS"] < position["EDITORA"]


class TestFallbackAndRefusal:
    """
        Quando o EAST não acha caixas, e quando a imagem é recusada
    """

    def test_fallback_reads_whole_image(self):
        """Sem caixas, o ocr_image da imagem inteira ainda lê o título da capa"""
        result = run_pipeline(SAMPLE_PATH.read_bytes(), DetectorWithoutRegions())

        assert result["meta"]["detector"] == "fallback"
        assert result["result"] == "unstructured"
        assert "SUBSOLO" in result["raw_text"].upper()

    def test_blank_image_is_no_text(self, text_detector):
        """
        Imagem válida sem nada escrito: o EAST não acha caixas, o fallback não lê
        nada, e a resposta é no_text com um livro vazio para o cadastro manual
        """
        result = run_pipeline(white_png(), text_detector)

        assert result["result"] == "no_text"
        assert result["raw_text"] == ""
        assert result["meta"]["detector"] == "fallback"
        assert result["books"] == [{"nome": None, "volume": None, "autores": None, "editora": None}]

    def test_invalid_file_raises_domain_error(self, text_detector):
        """A validação acontece antes do detector: o erro sobe para a API traduzir"""
        with pytest.raises(UnsupportedFormatError):
            run_pipeline(b"%PDF-1.7 isto nao e uma imagem", text_detector)
