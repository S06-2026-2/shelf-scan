package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.node.BibliotecaNode;
import br.com.virabrequim.database_service.domain.node.CidadeNode;
import br.com.virabrequim.database_service.domain.node.EstadoNode;
import br.com.virabrequim.database_service.domain.node.EstanteNode;

public record BibliotecaResposta(
        Long id,
        String nome,
        String email,
        Long estantePadraoId,
        EnderecoResposta endereco,
        String horarioAbertura,
        String horarioFechamento
) {

    public record EnderecoResposta(
            String logradouro,
            String numero,
            String cep,
            Integer cidadeCodigoIbge,
            String cidade,
            String estado
    ) {
    }

    public static BibliotecaResposta de(BibliotecaNode biblioteca) {
        EstanteNode estantePadrao = biblioteca.getEstantePadrao();
        Long estantePadraoId = estantePadrao == null ? null : estantePadrao.getId();
        return new BibliotecaResposta(
                biblioteca.getId(),
                biblioteca.getNome(),
                biblioteca.getEmail(),
                estantePadraoId,
                endereco(biblioteca),
                biblioteca.getHorarioAbertura(),
                biblioteca.getHorarioFechamento());
    }

    private static EnderecoResposta endereco(BibliotecaNode biblioteca) {
        CidadeNode cidade = biblioteca.getCidade();
        EstadoNode estado = cidade == null ? null : cidade.getEstado();
        return new EnderecoResposta(
                biblioteca.getLogradouro(),
                biblioteca.getNumero(),
                biblioteca.getCep(),
                cidade == null ? null : cidade.getCodigoIbge(),
                cidade == null ? null : cidade.getNome(),
                estado == null ? null : estado.getSigla());
    }
}
