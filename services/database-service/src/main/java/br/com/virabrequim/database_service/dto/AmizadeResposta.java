package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.EstadoAmizade;

// Resultado de solicitar ou aceitar uma amizade. `usuario` é a outra pessoa
public record AmizadeResposta(
        EstadoAmizade estado,
        Referencia usuario
) {
}
