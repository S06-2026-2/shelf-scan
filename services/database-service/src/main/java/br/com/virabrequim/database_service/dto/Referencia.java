package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.node.EstanteNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;

// Forma curta { id, nome } usada dentro de outras respostas (dono, mutuário, estante)
public record Referencia(
        Long id,
        String nome
) {

    public static Referencia de(UsuarioNode usuario) {
        return usuario == null ? null : new Referencia(usuario.getId(), usuario.getNome());
    }

    public static Referencia de(EstanteNode estante) {
        return estante == null ? null : new Referencia(estante.getId(), estante.getNome());
    }
}
