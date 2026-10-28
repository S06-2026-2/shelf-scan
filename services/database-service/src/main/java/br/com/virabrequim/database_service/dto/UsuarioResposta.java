package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.node.BibliotecaNode;
import br.com.virabrequim.database_service.domain.node.EstanteNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;

public record UsuarioResposta(
        Long id,
        String nome,
        String email,
        String tipo,
        Long estantePadraoId
) {

    public static UsuarioResposta de(UsuarioNode usuario) {
        String tipo = usuario instanceof BibliotecaNode ? "biblioteca" : "usuario";
        EstanteNode estantePadrao = usuario.getEstantePadrao();
        Long estantePadraoId = estantePadrao == null ? null : estantePadrao.getId();

        return new UsuarioResposta(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                tipo,
                estantePadraoId);
    }
}
