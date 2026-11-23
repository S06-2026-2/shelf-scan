"""Operações sobre listas de regiões detectadas pelo EastTextDetector.

Uma região é o dict devolvido pelo detect_text: {"box": [[x, y] x 4], "confidence"}.
"""

from itertools import combinations

import cv2
import numpy as np

from ocr.text_extractor import VERTICAL_RATIO

# Fração da caixa menor que precisa estar dentro da outra para as duas virarem
# uma só (critério da t4, medido nas fotos de samples/gabarito.json)
MIN_OVERLAP = 0.30


def top_to_bottom(regions):
    """Ordena as regiões pelo topo da caixa; em empate, pela esquerda

    Ainda não é a ordem de leitura: palavras da mesma linha com o topo alguns
    pixels mais baixo podem sair depois das vizinhas da direita. Agrupar por
    linha fica para a junção de fragmentos.

    ARGS:
        regions: Lista de regiões devolvida pelo detect_text

    RETURNS:
        Nova lista ordenada por (y mínimo, x mínimo) de cada caixa
    """
    def top_left(region):
        box = np.array(region["box"])
        return (box[:, 1].min(), box[:, 0].min())

    return sorted(regions, key=top_left)


def is_vertical(box):
    """Diz se a caixa é vertical, como o título numa lombada

    É a regra que o ocr_region usa para girar o recorte (VERTICAL_RATIO, do
    text_extractor): o lado vertical passa de VERTICAL_RATIO vezes o outro.
    Uma caixa quase quadrada não é vertical.

    ARGS:
        box: 4 pontos [x, y] na ordem do EAST: base-esquerda, topo-esquerda,
             topo-direita, base-direita

    RETURNS:
        True se a caixa é vertical
    """
    bl, tl, tr, br = np.array(box, dtype=np.float32)

    # Mesmos lados do crop_region: o maior entre os dois lados opostos
    w = max(np.linalg.norm(tr - tl), np.linalg.norm(br - bl))
    h = max(np.linalg.norm(bl - tl), np.linalg.norm(br - tr))
    return bool(h > VERTICAL_RATIO * w)


def overlap_ratio(box_a, box_b):
    """Fração da caixa menor que está dentro da outra (interseção ÷ área da menor)

    Não é o IoU (interseção ÷ união): uma caixa pequena inteira dentro de uma
    grande dá 1,0 aqui, e o IoU daria a razão entre as áreas. É isso que pega
    os pedaços de uma palavra, que o NMS do EAST (IoU de 0,4) deixa passar.

    ARGS:
        box_a, box_b: 4 pontos [x, y] cada, na ordem do EAST

    RETURNS:
        Float de 0 a 1; 0 se as caixas não se tocam
    """
    a = np.array(box_a, dtype=np.float32)
    b = np.array(box_b, dtype=np.float32)

    smaller = min(cv2.contourArea(a), cv2.contourArea(b))
    if smaller <= 0:
        return 0.0

    # Interseção exata dos dois polígonos: numa caixa inclinada, os retângulos
    # retos em volta das caixas se sobrepõem mais do que elas
    shared, _ = cv2.intersectConvexConvex(a, b)
    return shared / smaller


def merge_boxes(regions, min_overlap=MIN_OVERLAP):
    """Junta os pedaços de uma palavra que o EAST cortou em caixas sobrepostas

    Critério (t4):
        - duas caixas se juntam quando nenhuma é vertical e pelo menos
          min_overlap da menor está dentro da outra;
        - caixas verticais (lombadas) nunca se juntam: unidas, eram lidas de
          cabeça para baixo e atravessavam a lombada vizinha;
        - a junção é transitiva: se A toca B e B toca C, as três viram uma só;
        - a caixa unida é o retângulo reto que contém todas, com a maior
          confiança do grupo.

    ARGS:
        regions: Lista de regiões do detect_text
        min_overlap: Fração mínima da caixa menor dentro da outra

    RETURNS:
        Nova lista de regiões, no mesmo formato {"box", "confidence"}; a lista
        recebida e os dicts dela não são alterados
    """
    # Union-find: cada caixa aponta para um "chefe" de grupo; no começo, cada
    # uma é chefe de si mesma, e juntar dois grupos é trocar o chefe de um deles
    parent = list(range(len(regions)))

    def root(i):
        """Sobe pelos chefes até achar o chefe final do grupo da caixa i"""
        while parent[i] != i:
            i = parent[i]
        return i

    # Calculado uma vez por caixa, e não a cada par
    vertical = [is_vertical(region["box"]) for region in regions]

    for i, j in combinations(range(len(regions)), 2):
        if vertical[i] or vertical[j]:
            continue
        if overlap_ratio(regions[i]["box"], regions[j]["box"]) >= min_overlap:
            parent[root(j)] = root(i)

    # Agrupa pelo chefe, na ordem em que as caixas chegaram do detector
    groups = {}
    for i, region in enumerate(regions):
        groups.setdefault(root(i), []).append(region)

    merged = []
    for group in groups.values():
        if len(group) == 1:
            # Caixa sozinha: sai como veio (uma cópia do dict), com a inclinação original
            merged.append(dict(group[0]))
            continue

        points = np.concatenate([np.array(region["box"]) for region in group])
        x0, y0 = points.min(axis=0)
        x1, y1 = points.max(axis=0)
        merged.append({
            # Ordem do EAST: base-esquerda, topo-esquerda, topo-direita, base-direita;
            # int() troca o inteiro do NumPy pelo do Python, como o detect_text devolve
            "box": [[int(x0), int(y1)], [int(x0), int(y0)], [int(x1), int(y0)], [int(x1), int(y1)]],
            "confidence": max(region["confidence"] for region in group),
        })
    return merged
