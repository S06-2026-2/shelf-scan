package br.com.virabrequim.database_service.domain.node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BibliotecaNodeAgenteTest {

    private BibliotecaNode inatel() {
        return new BibliotecaNode("Biblioteca Inatel", "contato@inatel.br");
    }

    @Nested
    @DisplayName("Criação")
    class Criacao {

        @Test
        @DisplayName("E1 (B1): cria biblioteca com regras do UsuarioNode e é um UsuarioNode")
        void criaBiblioteca() {
            BibliotecaNode biblioteca = new BibliotecaNode(" Biblioteca Inatel ", "Contato@Inatel.br");
            assertAll(
                    () -> assertEquals("Biblioteca Inatel", biblioteca.getNome()),
                    () -> assertEquals("contato@inatel.br", biblioteca.getEmail()),
                    () -> assertThat(biblioteca.getEstantes()).hasSize(1),
                    () -> assertEquals("Minha estante", biblioteca.getEstantes().get(0).getNome()),
                    () -> assertSame(biblioteca.getEstantes().get(0), biblioteca.getEstantePadrao()),
                    () -> assertThat(biblioteca).isInstanceOf(UsuarioNode.class));
        }

        @Test
        @DisplayName("E2 (B1): nome em branco lança IllegalArgumentException")
        void nomeEmBranco() {
            assertThrows(IllegalArgumentException.class, () -> new BibliotecaNode("  ", "contato@inatel.br"));
        }

        @Test
        @DisplayName("B1 (derivado): email null lança IllegalArgumentException")
        @Tag("derivado")
        void emailNull() {
            assertThrows(IllegalArgumentException.class, () -> new BibliotecaNode("Biblioteca Inatel", null));
        }
    }

    @Nested
    @DisplayName("Horários")
    class Horarios {

        @Test
        @DisplayName("E3 (B3): horários válidos são guardados como informados")
        void horariosValidos() {
            BibliotecaNode b1 = inatel();
            b1.setHorarioAbertura("08:00");
            BibliotecaNode b2 = inatel();
            b2.setHorarioAbertura("00:00");
            BibliotecaNode b3 = inatel();
            b3.setHorarioFechamento("23:59");
            assertAll(
                    () -> assertEquals("08:00", b1.getHorarioAbertura()),
                    () -> assertEquals("00:00", b2.getHorarioAbertura()),
                    () -> assertEquals("23:59", b3.getHorarioFechamento()));
        }

        @Test
        @DisplayName("E4 (B3): horário com espaços nas pontas é guardado sem eles")
        void horarioComEspacos() {
            BibliotecaNode biblioteca = inatel();
            biblioteca.setHorarioAbertura(" 08:30 ");
            assertEquals("08:30", biblioteca.getHorarioAbertura());
        }

        @Test
        @DisplayName("E5 (B2): horário de fechamento null é aceito")
        void fechamentoNull() {
            BibliotecaNode biblioteca = inatel();
            biblioteca.setHorarioFechamento(null);
            assertNull(biblioteca.getHorarioFechamento());
        }

        @ParameterizedTest(name = "E6: abertura [{0}] lança IllegalArgumentException")
        @DisplayName("E6 (B3): horário de abertura inválido")
        @CsvSource({"24:00", "25:00", "08:60", "8:00", "8h", "''", "08:00:00"})
        void aberturaInvalida(String horario) {
            BibliotecaNode biblioteca = inatel();
            assertThrows(IllegalArgumentException.class, () -> biblioteca.setHorarioAbertura(horario));
        }

        @Test
        @DisplayName("E7 (B3): horário inválido mantém o anterior")
        void invalidoMantemAnterior() {
            BibliotecaNode biblioteca = inatel();
            biblioteca.setHorarioAbertura("08:00");
            assertThrows(IllegalArgumentException.class, () -> biblioteca.setHorarioAbertura("99:99"));
            assertEquals("08:00", biblioteca.getHorarioAbertura());
        }

        @Test
        @DisplayName("Caso de borda (B3): abertura depois do fechamento é aceita")
        void aberturaDepoisDoFechamento() {
            BibliotecaNode biblioteca = inatel();
            biblioteca.setHorarioAbertura("22:00");
            biblioteca.setHorarioFechamento("06:00");
            assertAll(
                    () -> assertEquals("22:00", biblioteca.getHorarioAbertura()),
                    () -> assertEquals("06:00", biblioteca.getHorarioFechamento()));
        }

        @Test
        @DisplayName("B3 (derivado): fechamento inválido (24:00) lança exceção e mantém o anterior")
        @Tag("derivado")
        void fechamentoInvalidoMantemAnterior() {
            BibliotecaNode biblioteca = inatel();
            biblioteca.setHorarioFechamento("22:00");
            assertThrows(IllegalArgumentException.class, () -> biblioteca.setHorarioFechamento("24:00"));
            assertEquals("22:00", biblioteca.getHorarioFechamento());
        }

        @Test
        @DisplayName("B3 (derivado): fechamento com espaços nas pontas é guardado sem eles")
        @Tag("derivado")
        void fechamentoComEspacos() {
            BibliotecaNode biblioteca = inatel();
            biblioteca.setHorarioFechamento(" 18:30 ");
            assertEquals("18:30", biblioteca.getHorarioFechamento());
        }

        @Test
        @DisplayName("B2 (derivado): abertura null depois de um horário fica null (não informado)")
        @Tag("derivado")
        void aberturaNullDepoisDeHorario() {
            BibliotecaNode biblioteca = inatel();
            biblioteca.setHorarioAbertura("08:00");
            biblioteca.setHorarioAbertura(null);
            assertNull(biblioteca.getHorarioAbertura());
        }
    }

    @Nested
    @DisplayName("Endereço")
    class EnderecoDaBiblioteca {

        @Test
        @DisplayName("B4 (derivado): logradouro, número e CEP não são validados no node")
        @Tag("derivado")
        void enderecoSemValidacao() {
            BibliotecaNode biblioteca = inatel();
            biblioteca.setLogradouro("");
            biblioteca.setNumero("abc");
            biblioteca.setCep("xyz");
            assertAll(
                    () -> assertEquals("", biblioteca.getLogradouro()),
                    () -> assertEquals("abc", biblioteca.getNumero()),
                    () -> assertEquals("xyz", biblioteca.getCep()));
        }

        @Test
        @DisplayName("B4 (derivado): cidade null é aceita no node")
        @Tag("derivado")
        void cidadeNull() {
            BibliotecaNode biblioteca = inatel();
            biblioteca.setCidade(new CidadeNode(3159605, "Santa Rita do Sapucaí", new EstadoNode(31, "MG")));
            biblioteca.setCidade(null);
            assertNull(biblioteca.getCidade());
        }
    }
}
