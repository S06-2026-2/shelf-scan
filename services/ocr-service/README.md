# OCR Service - Extração de texto de imagens
 
## Sobre o serviço
  
1. Capturar uma foto de uma estante (ou página de livro)
2. Processar a imagem para isolar texto
3. Isolar a ROI (Region of Interest) de cada lombada/livro com EAST 
3. Extrair o texto da região via OCR (Tesseract)
4. Processar os dados extraídos com uso de um agente de IA

## Como funciona (pipeline)
 
```
Imagem (foto da estante)
      │
      ▼
Pré-processamento (escala de cinza, binarização)
      │
      ▼
Segmentação (detecção de contornos/lombadas individuais, via EAST)
      │
      ▼
OCR (Tesseract) em cada região
      │
      ▼
Dados extraídos processados pelo Agente
      │
      ▼
     ...
```

## Estrutura

services/ocr-service/
├── ocr/              # código de produção (preprocessing, text_extractor)
├── test/              # testes + conftest.py
├── samples/           # imagens de amostra usadas nos testes
├── docker/tesseract/  # container + wrapper para dev local no Windows
├── pytest.ini
└── requirements.txt

## Setup do ambiente

- Python 3.13.5
- `pip install -r requirements.txt`
- Requer o binário do Tesseract instalado no sistema (ver seção abaixo)

## Tesseract OCR

O `pytesseract` só faz a ponte com o binário `tesseract` — ele precisa
estar instalado separadamente, não vem via pip.

- **Linux / CI:** `sudo apt-get install tesseract-ocr tesseract-ocr-por`
- **Windows (dev local):** roda em container Docker isolado

## Testes

pytest -v   # a partir de services/ocr-service/

Testes marcados `xfail` documentam limitações conhecidas (ex.: OCR
configurado para inglês — texto em português tem reconhecimento
degradado).

## CI/CD

`.github/workflows/ocr-service-ci.yml` — jobs `lint` (flake8) e `test`
(instala Tesseract via apt + roda pytest). Disparado em push/PR para
`main`/`dev` que toquem em `services/ocr-service/**`.

## Tecnologias
 
- **Python 3.13**
- **OpenCV** — pré-processamento e segmentação de imagem
- **EAST** (via `opencv-python`) — detecção de regiões de interesse (ROI)
- **Tesseract OCR** (via `pytesseract`) — extração de texto

## Status

- [x] Pré-processamento (grayscale, binarização)
- [x] OCR básico (Tesseract)
- [ ] Detecção de ROI (EAST)
- [ ] Integração com agente de IA