package br.com.virabrequim.database_service.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// Corpo de POST /usuario/{donoId}/livro/{livroId}/emprestimo. "Depois de hoje" é regra do service (400)
public record NovoEmprestimo(
        @NotNull Long mutuarioId,
        @NotNull LocalDate dataPrevistaDevolucao
) {
}
