package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;

import java.time.LocalDate;
import java.util.List;

public record EmprestimoResposta(
        Long id,
        Long livroId,
        Referencia mutuario,
        LocalDate dataEmprestimo,
        LocalDate dataPrevistaDevolucao,
        LocalDate dataDevolucao,
        String estado,
        Integer diasParaVencimento,
        boolean vencendo,
        boolean vencido,
        LeituraResposta leitura
) {

    // Recebe o livro porque a relação não conhece o id nem o total de páginas dele
    public static EmprestimoResposta de(EmprestimoRelationship emprestimo, LivroNode livro, LocalDate hoje) {
        boolean ativo = emprestimo.isEmprestado();

        return new EmprestimoResposta(
                emprestimo.getId(),
                livro.getId(),
                Referencia.de(emprestimo.getUsuario()),
                emprestimo.getDataEmprestimo(),
                emprestimo.getDataPrevistaDevolucao(),
                emprestimo.getDataDevolucao(),
                ativo ? "ativo" : "devolvido",
                ativo ? emprestimo.diasParaVencimento(hoje) : null,
                // estaVencendo lança exceção em empréstimo devolvido
                ativo && emprestimo.estaVencendo(hoje),
                emprestimo.isVencido(hoje),
                LeituraResposta.doMutuario(emprestimo, livro));
    }

    public static List<EmprestimoResposta> de(List<EmprestimoRelationship> emprestimos, LivroNode livro,
            LocalDate hoje) {
        return emprestimos.stream().map(emprestimo -> de(emprestimo, livro, hoje)).toList();
    }
}
