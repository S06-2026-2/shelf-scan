package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.StatusLeitura;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;

import java.time.LocalDate;

public record LeituraResposta(
        StatusLeitura status,
        Integer paginaAtual,
        Integer porcentagem,
        LocalDate dataConclusao
) {

    // Leitura do dono, guardada no livro
    public static LeituraResposta doDono(LivroNode livro) {
        return new LeituraResposta(
                livro.getStatusLeitura(),
                livro.getPaginaAtual(),
                livro.getPorcentagemLeitura(),
                livro.getDataConclusaoLeitura());
    }

    // Leitura de quem pegou o livro emprestado, guardada no LENT_TO
    public static LeituraResposta doMutuario(EmprestimoRelationship emprestimo, LivroNode livro) {
        return new LeituraResposta(
                emprestimo.getStatusLeitura(),
                emprestimo.getPaginaAtual(),
                emprestimo.porcentagemLeitura(livro.getPaginas()),
                emprestimo.getDataConclusaoLeitura());
    }
}
