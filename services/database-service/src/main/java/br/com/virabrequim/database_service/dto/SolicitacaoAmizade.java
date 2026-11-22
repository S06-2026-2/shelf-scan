package br.com.virabrequim.database_service.dto;

import java.time.LocalDate;

// `id` e `nome` são da outra pessoa: quem pediu (recebidas) ou para quem foi pedido (enviadas)
public record SolicitacaoAmizade(
        Long id,
        String nome,
        LocalDate solicitadaEm,
        int amigosEmComum
) {
}
