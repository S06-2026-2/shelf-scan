package br.com.virabrequim.database_service.agente.catalogo;

import java.util.List;

// Uma edição de livro como veio de um catálogo (Google Books ou BrasilAPI). isbn é sempre ISBN-13 válido.
// digital: true = e-book, false = impresso, null = o catálogo não informa.
public record Edicao(
        String isbn,
        String titulo,
        String subtitulo,
        List<String> autores,
        String editora,
        Integer paginas,
        String sinopse,
        Boolean digital,
        String origem
) {
}
