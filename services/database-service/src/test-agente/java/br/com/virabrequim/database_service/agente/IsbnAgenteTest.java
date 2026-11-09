package br.com.virabrequim.database_service.agente;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IsbnAgenteTest {

    @ParameterizedTest(name = "{0}: normalizado(\"{1}\") = \"{2}\"")
    @DisplayName("E1–E4 (I2, I3): ISBN válido volta normalizado")
    @CsvSource({
            "E1, 978-85-359-1484-9, 9788535914849",
            "E2, 9788535914849, 9788535914849",
            "E3, 85 359 1484 6, 8535914846",
            "E4, 0-8044-2957-x, 080442957X"
    })
    void validos(String id, String entrada, String esperado) {
        assertEquals(esperado, Isbn.normalizado(entrada));
    }

    @Test
    @DisplayName("E5 (I4): dígito verificador errado no ISBN-13 retorna null")
    void digitoErrado13() {
        assertNull(Isbn.normalizado("9788535914848"));
    }

    @Test
    @DisplayName("E6 (I4): dígito verificador errado no ISBN-10 retorna null")
    void digitoErrado10() {
        assertNull(Isbn.normalizado("8535914844"));
    }

    @Test
    @DisplayName("E7 (I4): tamanho errado, texto vazio e letras retornam null")
    void formatosInvalidos() {
        assertAll(
                () -> assertNull(Isbn.normalizado("123")),
                () -> assertNull(Isbn.normalizado("")),
                () -> assertNull(Isbn.normalizado("abcdefghij")));
    }

    @Test
    @DisplayName("E8 (I4): X no ISBN-13 retorna null")
    void xNoIsbn13() {
        assertNull(Isbn.normalizado("978853591484X"));
    }

    @Test
    @DisplayName("E9 (I1): null retorna null")
    void nulo() {
        assertNull(Isbn.normalizado(null));
    }

    @Test
    @DisplayName("I2 (caso de borda): separadores misturados são aceitos")
    void separadoresMisturados() {
        assertEquals("9788535914849", Isbn.normalizado("978 85-359 1484-9"));
    }

    @ParameterizedTest(name = "I4: normalizado(\"{0}\") = null")
    @DisplayName("I4 (caso de borda): ponto e barra não são removidos")
    @CsvSource({"978.85.359.1484.9", "978/85/359/1484/9"})
    void outrosSeparadores(String entrada) {
        assertNull(Isbn.normalizado(entrada));
    }

    @Test
    @Tag("derivado")
    @DisplayName("I4 (derivado): X fora da última posição do ISBN-10 retorna null")
    void xForaDaUltimaPosicao() {
        assertNull(Isbn.normalizado("08044X9575"));
    }

    @Test
    @Tag("derivado")
    @DisplayName("I4 (derivado): 12 dígitos (entre 10 e 13) retorna null")
    void dozeDigitos() {
        assertNull(Isbn.normalizado("978853591484"));
    }

    @Test
    @Tag("derivado")
    @DisplayName("I4 (derivado): 14 dígitos (acima de 13) retorna null")
    void quatorzeDigitos() {
        assertNull(Isbn.normalizado("97885359148490"));
    }

    @Test
    @Tag("derivado")
    @DisplayName("I4 (derivado): só hífens e espaços (vazio depois de normalizar) retorna null")
    void soSeparadores() {
        assertNull(Isbn.normalizado(" - - "));
    }
}
