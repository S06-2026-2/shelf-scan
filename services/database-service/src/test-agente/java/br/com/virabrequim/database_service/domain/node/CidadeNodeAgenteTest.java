package br.com.virabrequim.database_service.domain.node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CidadeNodeAgenteTest {

    private final EstadoNode mg = new EstadoNode(31, "MG");

    @Test
    @DisplayName("E1 (C1–C3): cria cidade com código, nome sem espaços e o mesmo estado")
    void criaCidade() {
        CidadeNode cidade = new CidadeNode(3159605, " Santa Rita do Sapucaí ", mg);
        assertAll(
                () -> assertEquals(3159605, cidade.getCodigoIbge()),
                () -> assertEquals("Santa Rita do Sapucaí", cidade.getNome()),
                () -> assertSame(mg, cidade.getEstado()));
    }

    @ParameterizedTest(name = "E2: código [{0}] lança IllegalArgumentException")
    @DisplayName("E2 (C1): código inválido")
    @CsvSource(value = {"NULL", "999999", "10000000"}, nullValues = "NULL")
    void codigoInvalido(Integer codigo) {
        assertThrows(IllegalArgumentException.class, () -> new CidadeNode(codigo, "Santa Rita do Sapucaí", mg));
    }

    @ParameterizedTest(name = "E3: nome [{0}] lança IllegalArgumentException")
    @DisplayName("E3 (C2): nome inválido")
    @CsvSource(value = {"NULL", "''", "'  '"}, nullValues = "NULL")
    void nomeInvalido(String nome) {
        assertThrows(IllegalArgumentException.class, () -> new CidadeNode(3159605, nome, mg));
    }

    @Test
    @DisplayName("E4 (C3): estado de outro código lança IllegalArgumentException")
    void estadoDeOutroCodigo() {
        EstadoNode sp = new EstadoNode(35, "SP");
        assertThrows(IllegalArgumentException.class, () -> new CidadeNode(3159605, "Santa Rita do Sapucaí", sp));
    }

    @Test
    @DisplayName("E5 (C3): estado null lança IllegalArgumentException")
    void estadoNull() {
        assertThrows(IllegalArgumentException.class, () -> new CidadeNode(3159605, "Santa Rita do Sapucaí", null));
    }

    @Test
    @DisplayName("E6 (C5): codigoDoEstado retorna os 2 primeiros dígitos")
    void codigoDoEstado() {
        assertAll(
                () -> assertEquals(31, CidadeNode.codigoDoEstado(3159605)),
                () -> assertEquals(35, CidadeNode.codigoDoEstado(3550308)));
    }

    @ParameterizedTest(name = "C1 (derivado): código limite [{0}] com estado [{1}] é aceito")
    @DisplayName("C1 (derivado): códigos nos limites 1000000 e 9999999 são aceitos")
    @Tag("derivado")
    @CsvSource({"1000000, 10", "9999999, 99"})
    void codigoNosLimites(Integer codigo, Integer codigoEstado) {
        EstadoNode estado = new EstadoNode(codigoEstado, "XX");
        assertEquals(codigo, new CidadeNode(codigo, "Cidade", estado).getCodigoIbge());
    }

    @ParameterizedTest(name = "C5 (derivado): codigoDoEstado({0}) = {1}")
    @DisplayName("C5 (derivado): codigoDoEstado nos limites do código da cidade")
    @Tag("derivado")
    @CsvSource({"1000000, 10", "9999999, 99"})
    void codigoDoEstadoNosLimites(int codigoCidade, int esperado) {
        assertEquals(esperado, CidadeNode.codigoDoEstado(codigoCidade));
    }
}
