package br.com.virabrequim.database_service.dto;

import java.time.LocalDate;

// Corpo opcional de POST .../emprestimo/devolucao; sem data, a devolução é hoje
public record Devolucao(
        LocalDate dataDevolucao
) {
}
