package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.node.AutorNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.node.TagNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.HashSet;
import java.util.List;

// Corpo de POST /usuario/{usuarioId}/livro (passo final do fluxo do OCR).
// paginas 0 ou ausente = sem total de páginas.
public record NovoLivro(
        @NotBlank String titulo,
        List<String> autores,
        List<String> tags,
        String isbn,
        String volume,
        @PositiveOrZero Integer paginas,
        String sinopse,
        Long estanteId,
        @Valid Leitura leitura
) {

    // Só monta o livro (as regras de título e páginas ficam no LivroNode). Estante, data de adição, leitura e o
    // reaproveitamento de autores/tags já cadastrados ficam com o service.
    public LivroNode paraLivro() {
        LivroNode livro = new LivroNode(titulo);
        livro.setIsbn(isbn);
        livro.setVolume(volume);
        livro.setPaginas(paginas);
        livro.setSinopse(sinopse);

        if (autores != null) {
            livro.setAutores(autores.stream().map(AutorNode::new).toList());
        }
        if (tags != null) {
            livro.setTags(new HashSet<>(tags.stream().map(TagNode::new).toList()));
        }
        return livro;
    }
}
