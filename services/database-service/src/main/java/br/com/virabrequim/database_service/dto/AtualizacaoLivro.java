package br.com.virabrequim.database_service.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

// Corpo de PATCH /usuario/{usuarioId}/livro/{livroId}. Campo ausente (null) = não mexer.
// - autores e tags: a lista enviada substitui a lista inteira ([] remove todos)
// - paginas: 0 remove o total de páginas
// - isbn, volume e sinopse: "" apaga o campo
// Estante e leitura têm rotas próprias (PATCH .../estante e PUT .../leitura).
public record AtualizacaoLivro(
        @Size(max = 300) String titulo,
        List<String> autores,
        List<String> tags,
        String isbn,
        String volume,
        @PositiveOrZero Integer paginas,
        String sinopse
) {
}
