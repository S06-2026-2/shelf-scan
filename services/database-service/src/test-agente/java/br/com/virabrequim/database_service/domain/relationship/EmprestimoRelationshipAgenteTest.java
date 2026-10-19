package br.com.virabrequim.database_service.domain.relationship;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// O construtor com validação, o de persistência e registrarDevolucao ainda não existem:
// o estado é montado com o construtor padrão + setters.
class EmprestimoRelationshipAgenteTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 10);
    private static final String MSG_SEM_PREVISTA = "Empréstimo sem data prevista de devolução";

    private static EmprestimoRelationship emprestimo(LocalDate dataEmprestimo, LocalDate prevista, LocalDate devolucao) {
        EmprestimoRelationship e = new EmprestimoRelationship();
        e.setDataEmprestimo(dataEmprestimo);
        e.setDataPrevistaDevolucao(prevista);
        e.setDataDevolucao(devolucao);
        return e;
    }

    private static EmprestimoRelationship ativo(LocalDate dataEmprestimo, LocalDate prevista) {
        return emprestimo(dataEmprestimo, prevista, null);
    }

    @Nested
    @DisplayName("Vencimento")
    class Vencimento {

        @ParameterizedTest(name = "{0}: hoje={1} → dias={2}, vencido={3}, vencendo={4}")
        @DisplayName("E12–E16 (R8, R10, R12, R13): empréstimo 01/03 com prevista 12/03")
        @CsvSource({
                "E12, 2026-03-05, 7, false, false",
                "E13, 2026-03-09, 3, false, true",
                "E14, 2026-03-12, 0, false, true",
                "E15, 2026-03-13, -1, true, false",
                "E16, 2026-03-20, -8, true, false"
        })
        void tabelaVencimento(String id, LocalDate hoje, int dias, boolean vencido, boolean vencendo) {
            EmprestimoRelationship e = ativo(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 12));
            assertAll(
                    () -> assertEquals(dias, e.diasParaVencimento(hoje), "diasParaVencimento"),
                    () -> assertEquals(vencido, e.isVencido(hoje), "isVencido"),
                    () -> assertEquals(vencendo, e.estaVencendo(hoje), "estaVencendo")
            );
        }

        @ParameterizedTest(name = "{0}: hoje={1} → dias={2}, vencendo={3}")
        @DisplayName("E17–E18 (R12): regra dos 15%, empréstimo 01/03 com prevista 10/04 (40 dias)")
        @CsvSource({
                "E17, 2026-04-03, 7, false",
                "E18, 2026-04-04, 6, true"
        })
        void regraDos15Porcento(String id, LocalDate hoje, int dias, boolean vencendo) {
            EmprestimoRelationship e = ativo(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 10));
            assertAll(
                    () -> assertEquals(dias, e.diasParaVencimento(hoje), "diasParaVencimento"),
                    () -> assertEquals(vencendo, e.estaVencendo(hoje), "estaVencendo")
            );
        }

        @Test
        @DisplayName("Borda (R12): prazo curto de 2 dias já nasce vencendo")
        void prazoCurtoNasceVencendo() {
            LocalDate hoje = LocalDate.of(2026, 3, 1);
            EmprestimoRelationship e = ativo(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3));
            assertAll(
                    () -> assertEquals(2, e.diasParaVencimento(hoje), "diasParaVencimento"),
                    () -> assertTrue(e.estaVencendo(hoje), "estaVencendo")
            );
        }

        @Test
        @DisplayName("Borda (R12): faltando 4 dias num prazo de 10 dias (40%) não está vencendo")
        void quatroDiasPrazoCurtoNaoVencendo() {
            LocalDate hoje = LocalDate.of(2026, 3, 7);
            EmprestimoRelationship e = ativo(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 11));
            assertAll(
                    () -> assertEquals(4, e.diasParaVencimento(hoje), "diasParaVencimento"),
                    () -> assertFalse(e.estaVencendo(hoje), "estaVencendo")
            );
        }

        @Test
        @Tag("derivado")
        @DisplayName("R12 (derivado): faltando 4 dias num prazo de 40 dias (10%) está vencendo só pela regra dos 15%")
        void quatroDiasPrazoLongoVencendo() {
            // empréstimo 01/03, prevista 10/04 (40 dias); hoje 06/04 → 4 dias, 10%
            EmprestimoRelationship e = ativo(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 10));
            assertTrue(e.estaVencendo(LocalDate.of(2026, 4, 6)));
        }

        @Test
        @Tag("derivado")
        @DisplayName("R12 (derivado): faltando 4 dias num prazo de 20 dias (20%) não está vencendo")
        void quatroDiasPrazoVinteDiasNaoVencendo() {
            // empréstimo 01/03, prevista 21/03 (20 dias); hoje 17/03 → 4 dias, 20%
            EmprestimoRelationship e = ativo(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 21));
            assertFalse(e.estaVencendo(LocalDate.of(2026, 3, 17)));
        }
    }

    @Nested
    @DisplayName("Devolvido")
    class Devolvido {

        private EmprestimoRelationship devolvidoComAtraso() {
            return emprestimo(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 8));
        }

        @Test
        @DisplayName("E19 (R9): diasParaVencimento de empréstimo devolvido lança IllegalStateException")
        void e19() {
            EmprestimoRelationship e = devolvidoComAtraso();
            assertThrows(IllegalStateException.class, () -> e.diasParaVencimento(HOJE));
        }

        @Test
        @DisplayName("E20 (R11): empréstimo devolvido com atraso não está vencido")
        void e20() {
            assertFalse(devolvidoComAtraso().isVencido(HOJE));
        }

        @Test
        @DisplayName("E21 (R13): empréstimo devolvido não está vencendo")
        void e21() {
            assertFalse(devolvidoComAtraso().estaVencendo(HOJE));
        }

        @Test
        @Tag("derivado")
        @DisplayName("R9 (derivado): diasParaVencimento de empréstimo devolvido no prazo lança IllegalStateException")
        void devolvidoNoPrazoDias() {
            // empréstimo 01/03, prevista 12/03, devolvido 05/03; hoje 10/03 (ainda antes da prevista)
            EmprestimoRelationship e = emprestimo(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 5));
            assertThrows(IllegalStateException.class, () -> e.diasParaVencimento(HOJE));
        }

        @Test
        @Tag("derivado")
        @DisplayName("R13 (derivado): empréstimo devolvido não está vencendo mesmo faltando 2 dias para a prevista")
        void devolvidoDentroDaJanelaDeVencendo() {
            // empréstimo 01/03, prevista 12/03, devolvido 05/03; hoje 10/03 → 2 dias para a prevista
            EmprestimoRelationship e = emprestimo(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 5));
            assertFalse(e.estaVencendo(HOJE));
        }
    }

    @Nested
    @DisplayName("Dados do banco")
    class DadosDoBanco {

        @Test
        @DisplayName("E22 (R14): empréstimo ativo sem data prevista lança IllegalStateException nos três métodos")
        void e22() {
            EmprestimoRelationship e = ativo(LocalDate.of(2026, 3, 1), null);
            assertAll(
                    () -> assertEquals(MSG_SEM_PREVISTA,
                            assertThrows(IllegalStateException.class, () -> e.diasParaVencimento(HOJE)).getMessage(),
                            "diasParaVencimento"),
                    () -> assertEquals(MSG_SEM_PREVISTA,
                            assertThrows(IllegalStateException.class, () -> e.isVencido(HOJE)).getMessage(),
                            "isVencido"),
                    () -> assertEquals(MSG_SEM_PREVISTA,
                            assertThrows(IllegalStateException.class, () -> e.estaVencendo(HOJE)).getMessage(),
                            "estaVencendo")
            );
        }

        @Test
        @DisplayName("E23 (R14, R11, R13, R9): devolvido sem data prevista segue as regras de devolvido")
        void e23() {
            EmprestimoRelationship e = emprestimo(LocalDate.of(2026, 3, 1), null, LocalDate.of(2026, 3, 5));
            assertAll(
                    () -> assertFalse(e.isVencido(HOJE), "isVencido"),
                    () -> assertFalse(e.estaVencendo(HOJE), "estaVencendo"),
                    () -> {
                        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> e.diasParaVencimento(HOJE));
                        // Pela R9, não pela falta da data prevista
                        assertNotEquals(MSG_SEM_PREVISTA, ex.getMessage(),
                                "exceção deveria ser pela R9, não pela falta da data prevista");
                    }
            );
        }
    }
}
