package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.node.EstanteNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;

import java.util.Objects;

public record EstanteResposta(
        Long id,
        String nome,
        boolean padrao,
        boolean virtual
) {

    public static final String NOME_EMPRESTADOS_COMIGO = "Emprestados comigo";

    // A padrão é identificada pelo id da estantePadrao do dono, nunca pelo nome
    public static EstanteResposta de(EstanteNode estante, UsuarioNode dono) {
        EstanteNode estantePadrao = dono.getEstantePadrao();
        boolean padrao = estantePadrao != null && Objects.equals(estantePadrao.getId(), estante.getId());

        return new EstanteResposta(estante.getId(), estante.getNome(), padrao, false);
    }

    // Só existe na API: o conteúdo vem dos empréstimos ativos para o usuário
    public static EstanteResposta emprestadosComigo() {
        return new EstanteResposta(null, NOME_EMPRESTADOS_COMIGO, false, true);
    }
}
