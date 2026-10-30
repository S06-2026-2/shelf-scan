"""Operações sobre listas de regiões detectadas pelo EastTextDetector.

Uma região é o dict devolvido pelo detect_text: {"box": [[x, y] x 4], "confidence"}.
"""

import numpy as np


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
