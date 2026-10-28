package br.com.virabrequim.notification_service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LimpezaDeTextoTest {

    @Test
    void removeNegritoEItalico() {
        assertEquals("Dom Casmurro é um clássico.", LimpezaDeTexto.limpar("**Dom Casmurro** é um *clássico*."));
    }

    @Test
    void trocaMarcadoresDeListaPorTraco() {
        assertEquals("- um\n- dois\n- três", LimpezaDeTexto.limpar("* um\n* dois\n+ três"));
    }

    @Test
    void removeTitulosMarkdown() {
        assertEquals("Resumo\nTexto", LimpezaDeTexto.limpar("## Resumo\nTexto"));
    }

    @Test
    void removeMarcadoresDeCitacaoDaBusca() {
        assertEquals("Livro de Andy Weir.", LimpezaDeTexto.limpar("Livro de Andy Weir【1†L8-L16】【1†L22-L27】."));
    }

    @Test
    void removeCrasesESublinhadosDuplos() {
        assertEquals("código e forte", LimpezaDeTexto.limpar("`código` e __forte__"));
    }

    @Test
    void reduzLinhasEmBrancoEmExcesso() {
        assertEquals("a\n\nb", LimpezaDeTexto.limpar("a\n\n\n\nb"));
    }

    @Test
    void textoSimplesFicaIgual() {
        String texto = "Olá! Recomendo \"Dom Casmurro\" - de Machado de Assis (1899). Gosta de snake_case?";

        assertEquals(texto, LimpezaDeTexto.limpar(texto));
    }
}
