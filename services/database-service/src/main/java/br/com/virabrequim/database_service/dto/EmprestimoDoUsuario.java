package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;

import java.time.LocalDate;

// Item de GET /usuario/{usuarioId}/emprestimo (e /vencendo, /vencidos).
// papel = "dono" (o livro é dele e está com outra pessoa) ou "mutuario" (ele pegou o livro de alguém)
public record EmprestimoDoUsuario(
        String papel,
        Long livroId,
        String titulo,
        Referencia dono,
        Referencia mutuario,
        LocalDate dataEmprestimo,
        LocalDate dataPrevistaDevolucao,
        Integer diasParaVencimento,
        boolean vencendo,
        boolean vencido
) {

    // Empréstimo sem data prevista (dado inconsistente do banco) não derruba a lista
    public static EmprestimoDoUsuario de(LivroNode livro, UsuarioNode dono, Long usuarioId, LocalDate hoje) {
        EmprestimoRelationship ativo = livro.emprestimoAtivo().orElseThrow();
        boolean temPrevista = ativo.getDataPrevistaDevolucao() != null;
        boolean ehMutuario = ativo.getUsuario() != null && usuarioId.equals(ativo.getUsuario().getId());

        return new EmprestimoDoUsuario(
                ehMutuario ? "mutuario" : "dono",
                livro.getId(),
                livro.getTitulo(),
                Referencia.de(dono),
                Referencia.de(ativo.getUsuario()),
                ativo.getDataEmprestimo(),
                ativo.getDataPrevistaDevolucao(),
                temPrevista ? ativo.diasParaVencimento(hoje) : null,
                temPrevista && ativo.estaVencendo(hoje),
                temPrevista && ativo.isVencido(hoje));
    }
}
