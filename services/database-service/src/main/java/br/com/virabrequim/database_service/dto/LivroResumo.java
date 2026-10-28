package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.node.AutorNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Item das listas de livros. `leitura` é a de quem pediu a lista
public record LivroResumo(
        Long id,
        String titulo,
        List<String> autores,
        LeituraResposta leitura,
        boolean emprestado,
        // Só aparecem quando emprestado é true
        @JsonInclude(JsonInclude.Include.NON_NULL) String emprestadoPara,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer diasParaVencimento
) {

    // Numa estante do dono: leitura do livro
    public static LivroResumo doDono(LivroNode livro, LocalDate hoje) {
        Optional<EmprestimoRelationship> ativo = livro.emprestimoAtivo();

        return new LivroResumo(
                livro.getId(),
                livro.getTitulo(),
                nomesDosAutores(livro),
                LeituraResposta.doDono(livro),
                ativo.isPresent(),
                ativo.map(emprestimo -> emprestimo.getUsuario().getNome()).orElse(null),
                ativo.map(emprestimo -> emprestimo.diasParaVencimento(hoje)).orElse(null));
    }

    // Em "Emprestados comigo": leitura do empréstimo ativo (o livro sempre tem um nessa lista)
    public static LivroResumo doMutuario(LivroNode livro, LocalDate hoje) {
        EmprestimoRelationship ativo = livro.emprestimoAtivo().orElseThrow();

        return new LivroResumo(
                livro.getId(),
                livro.getTitulo(),
                nomesDosAutores(livro),
                LeituraResposta.doMutuario(ativo, livro),
                true,
                ativo.getUsuario().getNome(),
                ativo.diasParaVencimento(hoje));
    }

    public static List<LivroResumo> doDono(List<LivroNode> livros, LocalDate hoje) {
        return livros.stream().map(livro -> doDono(livro, hoje)).toList();
    }

    public static List<LivroResumo> doMutuario(List<LivroNode> livros, LocalDate hoje) {
        return livros.stream().map(livro -> doMutuario(livro, hoje)).toList();
    }

    static List<String> nomesDosAutores(LivroNode livro) {
        if (livro.getAutores() == null) return List.of();
        return livro.getAutores().stream().map(AutorNode::getNome).toList();
    }
}
