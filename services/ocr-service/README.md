# OCR Service — leitura de capas e lombadas

## Sobre o serviço

Recebe a foto da capa ou da lombada de um livro e devolve os dados do livro (título, volume, autores e editora) junto com o texto bruto que o OCR leu. O serviço não grava nada e não conhece o usuário: quem confirma e grava o livro é o cliente (front ou bot).

As rotas, o formato da resposta, o catálogo de erros e os limites estão no [contrato da API](docs/contrato-api.md). Com o serviço no ar, o formato exato aparece no Swagger, em `http://localhost:8301/docs`.

## Como funciona (pipeline)

```
Foto (JPEG ou PNG, campo multipart "image")
      │
      ▼
Validação: 20 MB, magic numbers, decodificação até 55 MP, menor lado de 32 px   (api/main.py, ocr/image_input.py)
      │
      ▼
Detecção do texto com o EAST                                                      (ocr/text_detector.py)
      │
      ▼
Fusão das caixas: os pedaços sobrepostos de uma palavra viram uma caixa só;
as caixas verticais (lombadas) ficam como vieram                                  (ocr/regions.py)
      │
      ▼
OCR por região com o Tesseract; caixas verticais são lidas nos dois sentidos.
Sem nenhuma caixa, o Tesseract lê a imagem inteira (fallback)                     (ocr/text_extractor.py, ocr/pipeline.py)
      │
      ▼
Agente estruturador (Gemini): transforma o texto bruto em título, volume,
autores e editora. É opcional: sem a chave, a resposta sai "unstructured"        (ocr/structurer.py)
      │
      ▼
Resposta: books, raw_text, result e meta                                         (api/schemas.py)
```

## Estrutura

```
services/ocr-service/
├── api/        # FastAPI: rotas, tradução dos erros para HTTP e o formato da resposta
├── ocr/        # núcleo: validação da foto, EAST, fusão das caixas, OCR por região, pipeline e agente
├── scripts/    # download do modelo EAST, imagens de debug das caixas e avaliação das fotos do gabarito
├── test/       # testes (pytest) e conftest.py
├── samples/    # fotos de amostra usadas nos testes e o gabarito.json da avaliação
├── docs/       # contrato da API
├── models/     # modelo EAST baixado pelo script (fora do Git)
├── Dockerfile
├── pytest.ini
├── requirements.txt
└── requirements-dev.txt   # requirements.txt + pytest e httpx2 (o TestClient da API)
```

As pastas `debug/` (saídas dos scripts) e `fotos/` (fotos locais) também ficam fora do Git.

## Como rodar

### Com o Docker Compose (o jeito normal)

1. Opcional: coloque a `GEMINI_API_KEY` no `.env` da raiz do repositório. Sem ela, a API funciona normalmente e devolve só o texto bruto (`"result": "unstructured"`).
2. Na raiz do repositório:

   ```bash
   docker compose up -d --build ocr-service
   ```

3. A API sobe na porta `8301`. `GET /health` responde `200` quando o modelo EAST termina de carregar (cerca de 1 s), e o Swagger fica em `http://localhost:8301/docs`.

O modelo EAST é baixado durante o build, com o hash conferido (`scripts/download_east.py`).

> **Problema conhecido:** dentro de uma pasta sincronizada pelo OneDrive, o `docker build` falha com `invalid file request`. Construa a imagem a partir de uma cópia do repositório fora dessa pasta.

### Sem Docker (Linux)

```bash
sudo apt-get install tesseract-ocr tesseract-ocr-por
pip install -r requirements-dev.txt
python scripts/download_east.py
OPENCV_IO_MAX_IMAGE_PIXELS=55000000 uvicorn api.main:app --port 8301
```

- **O `OPENCV_IO_MAX_IMAGE_PIXELS`** é o limite de 55 MP do contrato. O `Dockerfile` já o define; sem ele, o OpenCV aceita até 2³⁰ pixels, o que pode estourar a memória.
- **O `pytesseract`** só faz a ponte com o programa `tesseract`, que precisa estar instalado no sistema. No Windows, use o contêiner.

## Testes

No contêiner, a partir de `services/ocr-service/`, montando o código atual por cima da imagem (assim não é preciso refazer o build a cada mudança no código; só quando mudar o `requirements.txt`):

```bash
docker run --rm -v "${PWD}:/app" -w /app shelf-scan-ocr-service pytest -p no:cacheprovider
```

`shelf-scan-ocr-service` é o nome que o compose dá à imagem quando o repositório está numa pasta chamada `shelf-scan`.

- **`xfail`:** os testes marcados assim documentam falhas conhecidas, com o motivo escrito no próprio teste. Por exemplo: o EAST não detecta números de volume isolados, nem a lombada de letra dourada do vol. 10 da Frieren. Quando uma delas passar a funcionar, o pytest mostra `XPASS`.
- **Lint bloqueante do CI:** `flake8 . --select=E9,F63,F7,F82` (erros de sintaxe e nomes indefinidos). Os outros avisos do flake8 aparecem no log, mas não quebram o CI.

## Avaliação das fotos e debug

- `samples/gabarito.json`: o título, os autores, a editora, os volumes, o número de livros e as palavras-chave do título de cada foto de amostra.
- `scripts/avaliar_fotos.py`: para cada foto do gabarito, mostra as caixas do EAST, as que sobram depois da fusão, as linhas do texto, o tempo do pipeline e quantas palavras do título saíram inteiras numa linha (sem caixa e sem acento, até o número de livros). O resultado também vai para um JSON em `debug/`.

  ```bash
  docker run --rm -v "${PWD}:/app" -w /app shelf-scan-ocr-service python scripts/avaliar_fotos.py
  ```

- `scripts/debug_detector.py`: desenha as caixas do EAST, numeradas e com a confiança, em `debug/<nome>_boxes.jpg`.

  ```bash
  docker run --rm -v "${PWD}:/app" -w /app shelf-scan-ocr-service python scripts/debug_detector.py samples/
  ```

## CI

`.github/workflows/ocr-service-ci.yml` tem dois jobs:
- **`lint`:** o flake8;
- **`test`:** no Ubuntu, instala o Tesseract pelo apt, baixa o modelo EAST e roda o pytest.

Ele dispara em push e PR para `main`, `dev` e `feat-ocr-service/*` que mexam em `services/ocr-service/`.

## Tecnologias

- **Python 3.13**, com **FastAPI** e **Uvicorn**
- **OpenCV**: o EAST (`cv2.dnn.TextDetectionModel_EAST`) e os recortes das regiões
- **Tesseract OCR** (via `pytesseract`), em português e inglês
- **Gemini** (via `google-genai`), no agente estruturador

## Status

- [x] Validação da foto e catálogo de erros
- [x] Detecção de texto (EAST)
- [x] Fusão das caixas horizontais sobrepostas
- [x] OCR por região, com as lombadas lidas nos dois sentidos
- [x] Agente estruturador (Gemini), opcional
- [x] API (`POST /v1/extraction`, `/health`, `/live`), contêiner e compose
- [ ] Rate limit e `X-Internal-Token`
- [ ] Fila de 3 requisições
- [ ] Vários livros por foto
- [ ] Lombadas mais rápidas: juntar as caixas verticais, o que depende de corrigir a leitura de cabeça para baixo nas caixas longas
