# -----------------------------------------------------------------
# Imagem do ocr-service: Python + Tesseract + dependências + testes.
# Por enquanto o comando padrão roda o pytest (a API entra depois).
#   docker build -t shelfscan-ocr services/ocr-service
#   docker run --rm shelfscan-ocr
# -----------------------------------------------------------------
FROM python:3.13-slim-trixie

# PYTHONDONTWRITEBYTECODE: o appuser não pode gravar __pycache__ em /app  -- obrigatória para rodar em 'leitura' (não-root)
# PYTHONUNBUFFERED: a saída aparece na hora no terminal / docker logs
# PIP_NO_CACHE_DIR: não guarda o cache do pip dentro da imagem
# PIP_ROOT_USER_ACTION: silencia o aviso de pip como root (esperado no build)
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_ROOT_USER_ACTION=ignore

# Binário do Tesseract + dados de idioma (por e eng (portugues e ingles): o código usa os dois)
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-por \
        tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Dependências antes do código para aproveitar o cache do Docker
COPY requirements.txt requirements-dev.txt ./
RUN pip install -r requirements-dev.txt

COPY pytest.ini ./
COPY ocr ./ocr
COPY test ./test
COPY samples ./samples

# Usuário não-root; /app continua do root, então o appuser só lê o código
RUN useradd --system --no-create-home appuser
USER appuser

# -p no:cacheprovider: o pytest não tenta gravar .pytest_cache em /app
CMD ["pytest", "-v", "-p", "no:cacheprovider"]
