"""Desenha as caixas do EastTextDetector sobre as imagens, para inspeção.

Uso (a partir de services/ocr-service/):
    python scripts/debug_detector.py samples/memorias_subsolo.jpg
    python scripts/debug_detector.py samples/ fotos/

Pastas são expandidas para todas as .jpg/.jpeg/.png dentro delas. Cada
imagem gera debug/<nome>_boxes.jpg, com as caixas numeradas pelo topo
(de cima para baixo) e a confiança; o terminal lista as mesmas caixas, com
os mesmos números. Não é a ordem de leitura: palavras da mesma linha com o
topo alguns pixels mais baixo podem sair depois das vizinhas da direita.
"""

import argparse
import sys
import time
from pathlib import Path

import cv2
import numpy as np

# Rodando como `python scripts/debug_detector.py`, o Python só enxerga a
# pasta scripts/; a raiz do serviço precisa entrar no path para o `import ocr`
SERVICE_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SERVICE_DIR))

from ocr.regions import top_to_bottom  # noqa: E402
from ocr.text_detector import EastTextDetector  # noqa: E402

EAST_PATH = SERVICE_DIR / "models" / "frozen_east_text_detection.pb"
DEFAULT_OUT_DIR = SERVICE_DIR / "debug"
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png"}

BOX_COLOR = (0, 0, 255)       # vermelho (BGR)
LABEL_TEXT_COLOR = (255, 255, 255)


def collect_images(paths):
    """Expande a lista de entradas em arquivos de imagem

    ARGS:
        paths: Caminhos de arquivos ou pastas

    RETURNS:
        Lista de Path das imagens, sem as saídas do próprio script (*_boxes)
    """
    images = []
    for path in map(Path, paths):
        if path.is_dir():
            images.extend(sorted(
                p for p in path.iterdir()
                if p.suffix.lower() in IMAGE_SUFFIXES and not p.stem.endswith("_boxes")
            ))
        else:
            images.append(path)
    return images


def draw_regions(img, regions):
    """Desenha as caixas numeradas, com a confiança, sobre uma cópia da imagem

    ARGS:
        img: Imagem BGR original
        regions: Regiões já na ordem em que devem ser numeradas (a partir de 1)

    RETURNS:
        Cópia da imagem com as caixas desenhadas (a original não é alterada)
    """
    annotated = img.copy()

    # Espessura e fonte proporcionais ao tamanho da imagem, para o desenho
    # ficar legível tanto numa foto de celular quanto numa imagem pequena
    scale = max(img.shape[:2]) / 1000
    thickness = max(1, round(2 * scale))
    font_scale = 0.5 * scale

    for number, region in enumerate(regions, start=1):
        box = np.array(region["box"], dtype=np.int32)
        cv2.polylines(annotated, [box], isClosed=True, color=BOX_COLOR, thickness=thickness)

        label = f"{number} ({region['confidence']:.2f})"
        (text_w, text_h), baseline = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, font_scale, thickness)
        x, y = int(box[:, 0].min()), int(box[:, 1].min())
        x = max(0, min(x, img.shape[1] - text_w))  # nem pela direita nem pela esquerda
        y = max(y, text_h + baseline)              # nem pelo topo da imagem

        # Fundo sólido atrás do rótulo para ele ficar legível sobre qualquer cor
        cv2.rectangle(annotated, (x, y - text_h - baseline), (x + text_w, y), BOX_COLOR, cv2.FILLED)
        cv2.putText(annotated, label, (x, y - baseline), cv2.FONT_HERSHEY_SIMPLEX,
                    font_scale, LABEL_TEXT_COLOR, thickness, cv2.LINE_AA)

    return annotated


def process_image(detector, path, out_dir):
    """Detecta o texto de uma imagem, salva a imagem de debug e imprime o resumo

    ARGS:
        detector: EastTextDetector já carregado
        path: Caminho da imagem
        out_dir: Pasta onde salvar <nome>_boxes.jpg

    RETURNS:
        Caminho da imagem salva, ou None se a imagem não pôde ser lida
    """
    img = cv2.imread(str(path))
    if img is None:
        print(f"[ignorada] {path}: não é uma imagem legível")
        return None

    start = time.perf_counter()
    regions = top_to_bottom(detector.detect_text(img))
    elapsed_ms = (time.perf_counter() - start) * 1000

    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / f"{path.stem}_boxes.jpg"
    cv2.imwrite(str(out_path), draw_regions(img, regions))

    h, w = img.shape[:2]
    print(f"\n{path}  ({w}x{h})  ->  {len(regions)} caixas em {elapsed_ms:.0f} ms")
    for number, region in enumerate(regions, start=1):
        print(f"  {number:>3}  conf {region['confidence']:.2f}  box {region['box']}")
    print(f"  salvo em {out_path}")
    return out_path


def main(argv=None):
    parser = argparse.ArgumentParser(description="Desenha as caixas do EAST sobre imagens")
    parser.add_argument("paths", nargs="+", help="arquivos de imagem ou pastas")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT_DIR,
                        help="pasta de saída (padrão: debug/)")
    args = parser.parse_args(argv)

    if not EAST_PATH.exists():
        print(f"Modelo EAST não encontrado em {EAST_PATH}. "
              "Rode `python scripts/download_east.py` antes.", file=sys.stderr)
        return 1

    images = collect_images(args.paths)
    if not images:
        print("Nenhuma imagem encontrada nas entradas.", file=sys.stderr)
        return 1

    # Um detector só para todas as imagens: carregar o EAST custa ~1 s e ~630 MB
    detector = EastTextDetector(str(EAST_PATH))
    saved = [process_image(detector, path, args.out) for path in images]
    return 0 if any(saved) else 1


if __name__ == "__main__":
    sys.exit(main())
