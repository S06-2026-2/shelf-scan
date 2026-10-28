package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;

import java.time.LocalDate;

// Para o notification-service (GET /emprestimo/vencendo e /vencidos)
public record AlertaEmprestimo(
        Long livroId,
        String titulo,
        Referencia dono,
        Referencia mutuario,
        LocalDate dataPrevistaDevolucao,
        int diasParaVencimento
) {

    // O livro vem das listas de empréstimo ativo, então sempre tem um
    public static AlertaEmprestimo de(LivroNode livro, UsuarioNode dono, LocalDate hoje) {
        EmprestimoRelationship ativo = livro.emprestimoAtivo().orElseThrow();

        return new AlertaEmprestimo(
                livro.getId(),
                livro.getTitulo(),
                Referencia.de(dono),
                Referencia.de(ativo.getUsuario()),
                ativo.getDataPrevistaDevolucao(),
                ativo.diasParaVencimento(hoje));
    }
}
