package br.com.virabrequim.notification_service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageFormatterAgentTest {

    private final MessageFormatterAgent formatter = new MessageFormatterAgent();

    @Test
    void livroSalvo_citaTituloEAutor() {
        assertEquals(
                "Livro salvo na sua estante: \"Dom Casmurro\", de Machado de Assis.",
                formatter.livroSalvo("Dom Casmurro", "Machado de Assis"));
    }

    @Test
    void livroSalvo_semAutor_citaSoOTitulo() {
        assertEquals("Livro salvo na sua estante: \"Dom Casmurro\".", formatter.livroSalvo("Dom Casmurro", null));
        assertEquals("Livro salvo na sua estante: \"Dom Casmurro\".", formatter.livroSalvo("Dom Casmurro", "  "));
    }
}
