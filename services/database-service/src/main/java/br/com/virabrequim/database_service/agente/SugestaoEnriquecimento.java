package br.com.virabrequim.database_service.agente;

import java.util.List;

// O que o agente sugere para o livro, já filtrado pelas travas do AgenteEnriquecedor.
// null (ou lista vazia) = o agente não tem uma sugestão confiável para o campo.
// - encontradoNoCatalogo: uma edição do catálogo foi conferida e é deste livro
// - edicaoDigital: a edição achada é e-book, então ISBN e páginas podem não ser os do exemplar físico
// - isbn, paginas e editora só vêm do catálogo; sinopse e tags são da IA; autores dependem (fonteAutores)
// - consultas: o que o agente consultou nos catálogos, na ordem
public record SugestaoEnriquecimento(
        boolean encontradoNoCatalogo,
        boolean edicaoDigital,
        String sinopse,
        String isbn,
        Integer paginas,
        String editora,
        List<String> autores,
        Fonte fonteAutores,
        List<String> tags,
        List<String> consultas
) {

    public static SugestaoEnriquecimento vazia(List<String> consultas) {
        return new SugestaoEnriquecimento(false, false, null, null, null, null, List.of(), Fonte.IA, List.of(),
                List.copyOf(consultas));
    }
}
