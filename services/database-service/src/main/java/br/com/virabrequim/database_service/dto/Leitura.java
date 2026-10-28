package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.StatusLeitura;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// Corpo de PUT .../livro/{livroId}/leitura e leitura inicial do NovoLivro.
// O limite superior da página depende do livro, por isso é regra do service (IllegalArgumentException -> 400)
public record Leitura(
        @NotNull StatusLeitura status,
        @PositiveOrZero Integer paginaAtual
) {
}
