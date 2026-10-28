# -----------------------------------------------------------------
# Imagem do ocr-service: Python + Tesseract + modelo EAST + dependências + testes.
# O comando padrão sobe a API na porta 8301; os testes rodam trocando o comando.
#   docker build -t shelfscan-ocr services/ocr-service
#   docker run --rm -p 8301:8301 shelfscan-ocr
#   docker run --rm shelfscan-ocr pytest -v -p no:cacheprovider
# -----------------------------------------------------------------
FROM python:3.13-slim-trixie

# PYTHONDONTWRITEBYTECODE: o appuser não pode gravar __pycache__ em /app  -- obrigatória para rodar em 'leitura' (não-root)
# PYTHONUNBUFFERED: a saída aparece na hora no terminal / docker logs
# PIP_NO_CACHE_DIR: não guarda o cache do pip dentro da imagem
# PIP_ROOT_USER_ACTION: silencia o aviso de pip como root (esperado no build)
# OPENCV_IO_MAX_IMAGE_PIXELS: 50 MP + 10% de folga; o padrão (2^30 pixels) não protege o container
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_ROOT_USER_ACTION=ignore \
    OPENCV_IO_MAX_IMAGE_PIXELS=55000000

# Binário do Tesseract + dados de idioma (por e eng (portugues e ingles): o código usa os dois)
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-por \
        tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Modelo EAST (~96 MB) antes das dependências: esta camada só é refeita
# quando o script muda, não a cada mudança no requirements ou no código
COPY scripts/download_east.py ./scripts/
RUN python scripts/download_east.py

# Dependências antes do código para aproveitar o cache do Docker
COPY requirements.txt requirements-dev.txt ./
RUN pip install -r requirements-dev.txt

COPY pytest.ini ./
COPY ocr ./ocr
COPY api ./api
COPY test ./test
COPY samples ./samples
# Os outros scripts (ex.: debug_detector.py) entram com o código, porque têm teste
COPY scripts ./scripts

# Usuário não-root; /app continua do root, então o appuser só lê o código
RUN useradd --system --no-create-home appuser
USER appuser

# 0.0.0.0: aceita conexões de fora do container (o compose mapeia a 8301)
EXPOSE 8301
CMD ["uvicorn", "api.main:app", "--host", "0.0.0.0", "--port", "8301"]
