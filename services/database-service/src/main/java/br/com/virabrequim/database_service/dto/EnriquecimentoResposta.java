package br.com.virabrequim.database_service.dto;

import java.util.List;

// Resposta do enriquecimento: o livro como ficou e o que o agente preencheu.
// camposPreenchidos vazio = nada mudou (livro não reconhecido ou campos já preenchidos).
public record EnriquecimentoResposta(
        LivroResposta livro,
        List<String> camposPreenchidos,
        boolean livroIdentificado,
        String modelo,
        String aviso
) {

    public static final String AVISO = "Dados gerados por IA: confira antes de confiar. Corrija pelo PATCH do livro.";
}
