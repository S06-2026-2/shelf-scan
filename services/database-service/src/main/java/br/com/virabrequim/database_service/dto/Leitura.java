package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.StatusLeitura;
import jakarta.validation.constraints.NotNull;

// Corpo de PUT .../livro/{livroId}/leitura e leitura inicial do NovoLivro.
// paginaAtual sem validação aqui: em LENDO, página inválida (negativa, ausente ou maior que o total) vira 0 no domínio
public record Leitura(
        @NotNull StatusLeitura status,
        Integer paginaAtual
) {
}
