"""Baixa o modelo EAST para models/ e confere o SHA-256.

Uso (a partir de services/ocr-service/):
    python scripts/download_east.py
"""

import hashlib
import shutil
import sys
import urllib.request
from pathlib import Path

# Espelho no GitHub do arquivo oficial da documentação do OpenCV
# (frozen_east_text_detection.tar.gz, no Dropbox). Os dois .pb têm o mesmo
# SHA-256: o hash garante a integridade; a URL só afeta a disponibilidade.
EAST_URL = (
    "https://github.com/oyyd/frozen_east_text_detection.pb/"
    "raw/master/frozen_east_text_detection.pb"
)
EAST_SHA256 = "9b486f3c3eee77b4c8cc91a83892c37026cca7d29b79bf3b93772ccd2db58454"

MODELS_DIR = Path(__file__).resolve().parent.parent / "models"
EAST_PATH = MODELS_DIR / "frozen_east_text_detection.pb"


def sha256_of(path):
    """Calcula o SHA-256 de um arquivo, lendo em blocos de 1 MB

    ARGS:
        path: Caminho do arquivo

    RETURNS:
        Hash SHA-256 em hexadecimal
    """
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def download_east():
    """Baixa o modelo EAST, a menos que ele já esteja em models/ com o hash certo

    RETURNS:
        Caminho do modelo verificado

    RAISES:
        RuntimeError: o hash do arquivo baixado não confere
        OSError: falha de rede ou de disco
    """
    if EAST_PATH.exists() and sha256_of(EAST_PATH) == EAST_SHA256:
        print(f"Modelo já presente e íntegro: {EAST_PATH}")
        return EAST_PATH

    MODELS_DIR.mkdir(parents=True, exist_ok=True)

    # Baixa para um arquivo temporário: um download interrompido ou
    # corrompido nunca fica no lugar do modelo
    tmp_path = EAST_PATH.with_suffix(".part")
    print(f"Baixando {EAST_URL}")
    try:
        with urllib.request.urlopen(EAST_URL, timeout=60) as response:
            with open(tmp_path, "wb") as f:
                shutil.copyfileobj(response, f)

        actual = sha256_of(tmp_path)
        if actual != EAST_SHA256:
            raise RuntimeError(
                "o arquivo baixado não é o modelo EAST esperado\n"
                f"  SHA-256 esperado: {EAST_SHA256}\n"
                f"  SHA-256 obtido:   {actual}"
            )
        tmp_path.replace(EAST_PATH)
    finally:
        tmp_path.unlink(missing_ok=True)

    print(f"Modelo salvo em {EAST_PATH}")
    return EAST_PATH


def main():
    try:
        download_east()
    except (OSError, RuntimeError) as exc:
        print(f"Erro ao obter o modelo EAST: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
