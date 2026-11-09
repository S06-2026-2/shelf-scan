package br.com.virabrequim.database_service.agente;

import java.util.List;

// O que o agente sugere para o livro, já filtrado pelas travas do AgenteEnriquecedor.
// null (ou lista vazia) = o agente não tem uma sugestão confiável para o campo.
public record SugestaoEnriquecimento(
        boolean livroIdentificado,
        String sinopse,
        String isbn,
        Integer paginas,
        List<String> autores,
        List<String> tags
) {

    public static SugestaoEnriquecimento vazia() {
        return new SugestaoEnriquecimento(false, null, null, null, List.of(), List.of());
    }
}
