"""Mede o OCR nas fotos do gabarito: caixas do EAST, tempo e palavras do título inteiras.

Uso (a partir de services/ocr-service/, no container, que tem o Tesseract):
    python scripts/avaliar_fotos.py
    python scripts/avaliar_fotos.py --out debug/avaliacao_fusao.json

Para cada foto de samples/gabarito.json, roda o EAST e o pipeline (sem o agente)
e imprime uma linha com:
    - caixas: quantas regiões o EAST encontrou;
    - fusão: quantas sobram depois da merge_boxes, que são as que o OCR lê;
    - linhas: quantas linhas o raw_text tem;
    - tempo: o tempo do pipeline (decodificação, EAST e OCR), sem o agente;
    - palavras: em quantas linhas do raw_text cada palavra-chave do título saiu
      inteira, até o número de livros da foto.
O mesmo resultado vai para um JSON (padrão: debug/avaliacao.json), para comparar
a linha de base com a fusão das caixas.
"""

import argparse
import json
import sys
import unicodedata
from datetime import datetime
from pathlib import Path

# Rodando como `python scripts/avaliar_fotos.py`, o Python só enxerga a
# pasta scripts/; a raiz do serviço precisa entrar no path para o `import ocr`
SERVICE_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SERVICE_DIR))

from ocr.image_input import decode_image  # noqa: E402
from ocr.pipeline import run_pipeline  # noqa: E402
from ocr.regions import merge_boxes  # noqa: E402
from ocr.text_detector import EastTextDetector  # noqa: E402

EAST_PATH = SERVICE_DIR / "models" / "frozen_east_text_detection.pb"
SAMPLES_DIR = SERVICE_DIR / "samples"
GABARITO_PATH = SAMPLES_DIR / "gabarito.json"
DEFAULT_OUT = SERVICE_DIR / "debug" / "avaliacao.json"


def normalize(text):
    """Passa o texto para maiúsculas e tira os acentos: "Memórias" vira "MEMORIAS"

    A métrica mede se a palavra saiu inteira, e não se o OCR acertou o acento:
    a fusão das caixas não muda o acento que o Tesseract lê.
    """
    # NFKD separa a letra do acento ("ó" vira "o" + acento); o acento é descartado
    decomposed = unicodedata.normalize("NFKD", text.upper())
    return "".join(char for char in decomposed if not unicodedata.combining(char))


def count_keyword(raw_text, keyword, limit):
    """Conta em quantas linhas do raw_text a palavra-chave aparece inteira

    Inteira quer dizer dentro de uma mesma linha: "SUBSOLO," conta, mas "SUB" e
    "SOLO" em linhas separadas não. Cada linha do raw_text é uma região do EAST.

    ARGS:
        raw_text: Texto do pipeline, uma região por linha
        keyword: Palavra do título (a comparação ignora caixa e acento)
        limit: Número de livros da foto; a contagem não passa dele

    RETURNS:
        Inteiro entre 0 e limit
    """
    word = normalize(keyword)
    hits = sum(1 for line in raw_text.splitlines() if word in normalize(line))
    return min(hits, limit)


def load_gabarito(path=GABARITO_PATH):
    """Lê a lista de fotos do gabarito (arquivo, título, livros e palavras-chave)"""
    with open(path, encoding="utf-8") as file:
        return json.load(file)["fotos"]


def count_boxes(detector, img_bytes):
    """Conta as caixas do EAST e as que sobram depois da fusão (merge_boxes)

    O pipeline não expõe as caixas, então a contagem é feita à parte, com a
    mesma fusão que o run_pipeline aplica antes do OCR.

    ARGS:
        detector: EastTextDetector já carregado (ou um detector falso, nos testes)
        img_bytes: Conteúdo do arquivo da foto

    RETURNS:
        (caixas do EAST, caixas depois da fusão)
    """
    regions = detector.detect_text(decode_image(img_bytes))
    return len(regions), len(merge_boxes(regions))


def evaluate_photo(detector, photo, samples_dir=SAMPLES_DIR):
    """Roda o EAST e o pipeline numa foto do gabarito e mede o resultado

    ARGS:
        detector: EastTextDetector já carregado
        photo: Entrada do gabarito
        samples_dir: Pasta onde está o arquivo da foto

    RETURNS:
        Dicionário com arquivo, caixas, caixas_fusao, linhas, tempo_ms, detector,
        palavras ({palavra: linhas em que saiu inteira}), inteiras e possiveis
    """
    img_bytes = (samples_dir / photo["arquivo"]).read_bytes()

    # O EAST roda duas vezes por foto (~1 s a mais), mas a contagem fica fora do tempo medido
    boxes, merged = count_boxes(detector, img_bytes)
    result = run_pipeline(img_bytes, detector)

    raw_text = result["raw_text"]
    words = {word: count_keyword(raw_text, word, photo["livros"]) for word in photo["palavras"]}
    return {
        "arquivo": photo["arquivo"],
        "caixas": boxes,
        "caixas_fusao": merged,
        "linhas": len(raw_text.splitlines()),
        "tempo_ms": result["meta"]["elapsed_ms"],
        "detector": result["meta"]["detector"],
        "palavras": words,
        "inteiras": sum(words.values()),
        "possiveis": len(photo["palavras"]) * photo["livros"],
    }


def print_row(row, livros):
    """Imprime o resultado de uma foto numa linha da tabela"""
    words = ", ".join(f"{word} {hits}/{livros}" for word, hits in row["palavras"].items())
    print(f"{row['arquivo']:<28} {row['caixas']:>6} {row['caixas_fusao']:>6} {row['linhas']:>6}"
          f" {row['tempo_ms'] / 1000:>7.1f} s   {row['inteiras']:>2}/{row['possiveis']:<2}  {words}")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Mede caixas, tempo e palavras do título nas fotos do gabarito")
    parser.add_argument("--gabarito", type=Path, default=GABARITO_PATH,
                        help="JSON do gabarito (padrão: samples/gabarito.json)")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="JSON de saída (padrão: debug/avaliacao.json)")
    args = parser.parse_args(argv)

    if not EAST_PATH.exists():
        print(f"Modelo EAST não encontrado em {EAST_PATH}. "
              "Rode `python scripts/download_east.py` antes.", file=sys.stderr)
        return 1

    photos = load_gabarito(args.gabarito)
    # Um detector só para todas as fotos: carregar o EAST custa ~1 s e ~630 MB
    detector = EastTextDetector(str(EAST_PATH))

    print(f"{'foto':<28} {'caixas':>6} {'fusão':>6} {'linhas':>6} {'tempo':>9}   inteiras  palavras")
    rows = []
    for photo in photos:
        row = evaluate_photo(detector, photo, args.gabarito.parent)
        print_row(row, photo["livros"])
        rows.append(row)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    report = {"data": datetime.now().isoformat(timespec="seconds"), "fotos": rows}
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nsalvo em {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
