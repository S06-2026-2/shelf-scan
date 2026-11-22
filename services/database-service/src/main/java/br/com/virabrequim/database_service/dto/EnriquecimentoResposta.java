package br.com.virabrequim.database_service.dto;

import br.com.virabrequim.database_service.agente.Fonte;

import java.util.List;
import java.util.Map;

// Resposta do enriquecimento: o livro como ficou e o que o agente preencheu.
// camposPreenchidos vazio = nada mudou (livro não encontrado ou campos já preenchidos).
// edicaoDigital: a edição achada é e-book; ISBN e páginas podem ser diferentes dos do exemplar físico.
// fontes: "catalogo" (conferido com Google Books/BrasilAPI) ou "ia" (gerado pelo modelo), por campo preenchido.
public record EnriquecimentoResposta(
        LivroResposta livro,
        List<String> camposPreenchidos,
        Map<String, Fonte> fontes,
        boolean encontradoNoCatalogo,
        boolean edicaoDigital,
        List<String> consultas,
        String modelo,
        String aviso
) {

    public static final String AVISO = "Campos com fonte \"ia\" são gerados por IA: confira antes de confiar. "
            + "Corrija pelo PATCH do livro.";
}
