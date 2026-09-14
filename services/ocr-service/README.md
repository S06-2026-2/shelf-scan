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

## Tecnologias
 
- **Python 3.13**
- **OpenCV** — pré-processamento e segmentação de imagem
- **EAST** (via `opencv-python`) — detecção de regiões de interesse (ROI)
- **Tesseract OCR** (via `pytesseract`) — extração de texto