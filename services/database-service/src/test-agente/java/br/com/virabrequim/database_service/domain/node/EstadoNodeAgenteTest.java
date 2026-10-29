package br.com.virabrequim.database_service.domain.node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EstadoNodeAgenteTest {

    @Test
    @DisplayName("E1 (S1, S2): cria estado com código e sigla")
    void criaEstado() {
        EstadoNode mg = new EstadoNode(31, "MG");
        assertAll(
                () -> assertEquals(31, mg.getCodigoIbge()),
                () -> assertEquals("MG", mg.getSigla()));
    }

    @Test
    @DisplayName("E2 (S2): sigla com espaços e minúsculas é guardada sem espaços e em maiúsculas")
    void siglaNormalizada() {
        assertEquals("MG", new EstadoNode(31, " mg ").getSigla());
    }

    @ParameterizedTest(name = "E3: código [{0}] lança IllegalArgumentException")
    @DisplayName("E3 (S1): código inválido")
    @CsvSource(value = {"NULL", "9", "100"}, nullValues = "NULL")
    void codigoInvalido(Integer codigo) {
        assertThrows(IllegalArgumentException.class, () -> new EstadoNode(codigo, "MG"));
    }

    @ParameterizedTest(name = "E4: sigla [{0}] lança IllegalArgumentException")
    @DisplayName("E4 (S2): sigla inválida")
    @CsvSource(value = {"NULL", "''", "M", "MGS", "M1"}, nullValues = "NULL")
    void siglaInvalida(String sigla) {
        assertThrows(IllegalArgumentException.class, () -> new EstadoNode(31, sigla));
    }

    @ParameterizedTest(name = "S1 (derivado): código limite [{0}] é aceito")
    @DisplayName("S1 (derivado): códigos nos limites 10 e 99 são aceitos")
    @Tag("derivado")
    @CsvSource({"10", "99"})
    void codigoNosLimites(Integer codigo) {
        assertEquals(codigo, new EstadoNode(codigo, "MG").getCodigoIbge());
    }
}
