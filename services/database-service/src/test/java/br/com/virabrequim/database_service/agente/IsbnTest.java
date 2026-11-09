package br.com.virabrequim.database_service.agente;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IsbnTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("ISBN válido é normalizado (sem hífens e espaços)")
    @CsvSource({
            "978-85-359-1484-9, 9788535914849",
            "9788535914849, 9788535914849",
            "85 359 1484 6, 8535914846",
            "0-8044-2957-x, 080442957X"
    })
    void valido(String entrada, String esperado) {
        assertThat(Isbn.normalizado(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @DisplayName("ISBN com dígito verificador errado ou formato inválido vira null")
    @ValueSource(strings = {"9788535914848", "8535914844", "123", "978853591484X", "abcdefghij", ""})
    @NullSource
    void invalido(String entrada) {
        assertThat(Isbn.normalizado(entrada)).isNull();
    }
}
