package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.node.TagNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

// Detalhe do livro. `leitura` é a do dono; a de quem está com ele vem em emprestimoAtivo.leitura
public record LivroResposta(
        Long id,
        String titulo,
        List<String> autores,
        List<String> tags,
        String isbn,
        String volume,
        Integer paginas,
        String sinopse,
        ZonedDateTime dataDeAdicao,
        Referencia dono,
        Referencia estante,
        LeituraResposta leitura,
        EmprestimoResposta emprestimoAtivo
) {

    public static LivroResposta de(LivroNode livro, UsuarioNode dono, LocalDate hoje) {
        List<String> tags = livro.getTags() == null ? List.of()
                : livro.getTags().stream().map(TagNode::getNomeExibicao).sorted().toList();

        return new LivroResposta(
                livro.getId(),
                livro.getTitulo(),
                LivroResumo.nomesDosAutores(livro),
                tags,
                livro.getIsbn(),
                livro.getVolume(),
                livro.getPaginas(),
                livro.getSinopse(),
                livro.getDataDeAdicao(),
                Referencia.de(dono),
                Referencia.de(livro.getEstante()),
                LeituraResposta.doDono(livro),
                livro.emprestimoAtivo().map(emprestimo -> EmprestimoResposta.de(emprestimo, livro, hoje)).orElse(null));
    }
}
