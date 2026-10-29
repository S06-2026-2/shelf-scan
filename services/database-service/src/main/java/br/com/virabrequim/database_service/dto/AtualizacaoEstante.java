package br.com.virabrequim.database_service.dto;

import jakarta.validation.constraints.Size;

// Corpo de PATCH /usuario/{usuarioId}/estante/{estanteId}. Campo ausente (null) = não mexer.
// Hoje a estante só tem nome; novos campos editáveis entram aqui, todos opcionais.
public record AtualizacaoEstante(
        @Size(max = 50) String nome
) {
}
