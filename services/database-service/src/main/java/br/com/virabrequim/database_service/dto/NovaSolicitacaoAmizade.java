package br.com.virabrequim.database_service.dto;

import jakarta.validation.constraints.NotNull;

// Corpo de POST /usuario/{usuarioId}/solicitacao-amizade
public record NovaSolicitacaoAmizade(
        @NotNull Long destinatarioId
) {
}
