package br.com.virabrequim.database_service.dto;

import jakarta.validation.constraints.NotNull;

// Corpo de PATCH /usuario/{usuarioId}/livro/{livroId}/estante
public record MoverLivro(
        @NotNull Long estanteId
) {
}
