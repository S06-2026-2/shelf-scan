package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.domain.node.AutorNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.node.TagNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.HashSet;
import java.util.List;

// Corpo de POST /usuario/{usuarioId}/livro (passo final do fluxo do OCR)
public record NovoLivro(
        @NotBlank String titulo,
        List<String> autores,
        List<String> tags,
        String isbn,
        String volume,
        @Positive Integer paginas,
        String sinopse,
        Long estanteId,
        @Valid Leitura leitura
) {

    // Só copia os dados; estante, data de adição e leitura ficam com o service
    public LivroNode paraLivro() {
        LivroNode livro = new LivroNode();
        livro.setTitulo(titulo);
        livro.setIsbn(isbn);
        livro.setVolume(volume);
        livro.setPaginas(paginas);
        livro.setSinopse(sinopse);

        if (autores != null) {
            livro.setAutores(autores.stream().map(NovoLivro::autor).toList());
        }
        if (tags != null) {
            livro.setTags(new HashSet<>(tags.stream().map(TagNode::new).toList()));
        }
        return livro;
    }

    private static AutorNode autor(String nome) {
        AutorNode autor = new AutorNode();
        autor.setNome(nome);
        return autor;
    }
}
