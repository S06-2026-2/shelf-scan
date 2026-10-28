package br.com.virabrequim.database_service.domain.relationship;

import br.com.virabrequim.database_service.domain.StatusLeitura;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// O construtor do sistema, o de persistência, registrarDevolucao e atualizarLeitura ainda não existem:
// o estado é montado pelo construtor sem argumentos e pelos setters gerados pelo Lombok.
class EmprestimoRelationshipAgenteTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 10);

    private static LocalDate dia(int dia, int mes) {
        return LocalDate.of(2026, mes, dia);
    }

    private static EmprestimoRelationship emprestimo(LocalDate dataEmprestimo, LocalDate prevista,
            LocalDate devolucao) {
        EmprestimoRelationship e = new EmprestimoRelationship();
        e.setDataEmprestimo(dataEmprestimo);
        e.setDataPrevistaDevolucao(prevista);
        e.setDataDevolucao(devolucao);
        return e;
    }

    @Nested
    @DisplayName("Vencimento")
    class Vencimento {

        @ParameterizedTest(name = "{0}: hoje={1} → dias={2}, vencido={3}, vencendo={4}")
        @DisplayName("E12–E16 (R8, R10, R12, R13): empréstimo ativo de 01/03 a 12/03/2026")
        @CsvSource({
                "E12, 2026-03-05, 7, false, false",
                "E13, 2026-03-09, 3, false, true",
                "E14, 2026-03-12, 0, false, true",
                "E15, 2026-03-13, -1, true, false",
                "E16, 2026-03-20, -8, true, false"
        })
        void tabelaDeVencimento(String id, LocalDate hoje, int dias, boolean vencido, boolean vencendo) {
            EmprestimoRelationship e = emprestimo(dia(1, 3), dia(12, 3), null);
            assertAll(
                    () -> assertEquals(dias, e.diasParaVencimento(hoje)),
                    () -> assertEquals(vencido, e.isVencido(hoje)),
                    () -> assertEquals(vencendo, e.estaVencendo(hoje)));
        }

        @ParameterizedTest(name = "{0}: hoje={1} → dias={2}, vencendo={3}")
        @DisplayName("E17–E18 (R12): regra dos 15% num prazo de 40 dias")
        @CsvSource({
                "E17, 2026-04-03, 7, false",
                "E18, 2026-04-04, 6, true"
        })
        void regraDos15PorCento(String id, LocalDate hoje, int dias, boolean vencendo) {
            EmprestimoRelationship e = emprestimo(dia(1, 3), dia(10, 4), null);
            assertAll(
                    () -> assertEquals(dias, e.diasParaVencimento(hoje)),
                    () -> assertEquals(vencendo, e.estaVencendo(hoje)));
        }

        @Test
        @DisplayName("R8, R12 (caso de borda): prazo curto de 2 dias já nasce vencendo")
        void prazoCurtoJaNasceVencendo() {
            EmprestimoRelationship e = emprestimo(dia(1, 3), dia(3, 3), null);
            assertAll(
                    () -> assertEquals(2, e.diasParaVencimento(dia(1, 3))),
                    () -> assertTrue(e.estaVencendo(dia(1, 3))));
        }

        @Test
        @DisplayName("R12 (caso de borda): 4 dias e 40% do prazo não é vencendo")
        void quatroDiasEmPrazoDeDezNaoEVencendo() {
            EmprestimoRelationship e = emprestimo(dia(1, 3), dia(11, 3), null);
            assertAll(
                    () -> assertEquals(4, e.diasParaVencimento(dia(7, 3))),
                    () -> assertFalse(e.estaVencendo(dia(7, 3))));
        }

        @Test
        @Tag("derivado")
        @DisplayName("R8 (derivado): falta 1 dia na véspera da data prevista")
        void vesperaFaltaUmDia() {
            EmprestimoRelationship e = emprestimo(dia(1, 3), dia(12, 3), null);
            assertEquals(1, e.diasParaVencimento(dia(11, 3)));
        }

        @Test
        @Tag("derivado")
        @DisplayName("R12 (derivado): 4 dias em prazo de 40 (10%) é vencendo pela regra dos 15%")
        void quatroDiasEmPrazoLongoEVencendo() {
            EmprestimoRelationship e = emprestimo(dia(1, 3), dia(10, 4), null);
            assertTrue(e.estaVencendo(dia(6, 4)));
        }
    }

    @Nested
    @DisplayName("Devolvido")
    class Devolvido {

        private EmprestimoRelationship devolvidoComAtraso() {
            return emprestimo(dia(1, 3), dia(5, 3), dia(8, 3));
        }

        @Test
        @DisplayName("E19 (R9): diasParaVencimento de empréstimo devolvido lança IllegalStateException")
        void diasDeDevolvido() {
            EmprestimoRelationship e = devolvidoComAtraso();
            assertThrows(IllegalStateException.class, () -> e.diasParaVencimento(HOJE));
        }

        @Test
        @DisplayName("E20 (R11): empréstimo devolvido com atraso não está vencido")
        void devolvidoNaoEstaVencido() {
            assertFalse(devolvidoComAtraso().isVencido(HOJE));
        }

        @Test
        @DisplayName("E21 (R13): empréstimo devolvido não está vencendo")
        void devolvidoNaoEstaVencendo() {
            assertFalse(devolvidoComAtraso().estaVencendo(HOJE));
        }

        @Test
        @Tag("derivado")
        @DisplayName("R13 (derivado): devolvido dentro do prazo, a 2 dias da data prevista, não está vencendo")
        void devolvidoNoPrazoNaoEstaVencendo() {
            EmprestimoRelationship e = emprestimo(dia(1, 3), dia(12, 3), dia(9, 3));
            assertFalse(e.estaVencendo(HOJE));
        }

        @Test
        @Tag("derivado")
        @DisplayName("R11 (derivado): devolvido no prazo, com hoje depois da data prevista, não está vencido")
        void devolvidoNoPrazoHojeDepoisDaPrevista() {
            EmprestimoRelationship e = emprestimo(dia(1, 3), dia(5, 3), dia(4, 3));
            assertFalse(e.isVencido(HOJE));
        }

        @Test
        @Tag("derivado")
        @DisplayName("R9 (derivado): devolvido no prazo, com hoje antes da data prevista, diasParaVencimento lança IllegalStateException")
        void diasDeDevolvidoNoPrazo() {
            EmprestimoRelationship e = emprestimo(dia(1, 3), dia(12, 3), dia(9, 3));
            assertThrows(IllegalStateException.class, () -> e.diasParaVencimento(HOJE));
        }
    }

    @Nested
    @DisplayName("Dados inconsistentes vindos do banco")
    class DadosDoBanco {

        @Test
        @DisplayName("E22 (R14): empréstimo ativo sem data prevista lança IllegalStateException com mensagem")
        void ativoSemDataPrevista() {
            EmprestimoRelationship e = emprestimo(dia(1, 3), null, null);
            String mensagem = "Empréstimo sem data prevista de devolução";
            assertAll(
                    () -> assertEquals(mensagem,
                            assertThrows(IllegalStateException.class, () -> e.diasParaVencimento(HOJE)).getMessage()),
                    () -> assertEquals(mensagem,
                            assertThrows(IllegalStateException.class, () -> e.isVencido(HOJE)).getMessage()),
                    () -> assertEquals(mensagem,
                            assertThrows(IllegalStateException.class, () -> e.estaVencendo(HOJE)).getMessage()));
        }

        @Test
        @DisplayName("E23 (R14, R11, R13, R9): devolvido sem data prevista segue as regras de devolvido")
        void devolvidoSemDataPrevista() {
            EmprestimoRelationship e = emprestimo(dia(1, 3), null, dia(5, 3));
            assertAll(
                    () -> assertFalse(e.isVencido(HOJE)),
                    () -> assertFalse(e.estaVencendo(HOJE)),
                    () -> assertThrows(IllegalStateException.class, () -> e.diasParaVencimento(HOJE)));
        }
    }

    @Nested
    @DisplayName("Porcentagem de leitura")
    class PorcentagemLeitura {

        @Test
        @DisplayName("E36 (R21): empréstimo antigo com status null tem porcentagem null")
        void statusNull() {
            EmprestimoRelationship e = emprestimo(dia(1, 3), dia(12, 3), null);
            assertNull(e.porcentagemLeitura(200));
        }

        @Test
        @Tag("derivado")
        @DisplayName("R21 (derivado): LENDO na página 199 de 200 é 99 (arredondado para baixo)")
        void penultimaPagina() {
            EmprestimoRelationship e = emprestimo(dia(1, 3), dia(12, 3), null);
            e.setStatusLeitura(StatusLeitura.LENDO);
            e.setPaginaAtual(199);
            assertEquals(99, e.porcentagemLeitura(200));
        }

        @Test
        @Tag("derivado")
        @DisplayName("R21 (derivado): LENDO com página acima do total é limitado a 100")
        void limitadoA100() {
            EmprestimoRelationship e = emprestimo(dia(1, 3), dia(12, 3), null);
            e.setStatusLeitura(StatusLeitura.LENDO);
            e.setPaginaAtual(250);
            assertEquals(100, e.porcentagemLeitura(200));
        }

        @Test
        @Tag("derivado")
        @DisplayName("R21 (derivado): LIDO num empréstimo devolvido, sem total de páginas, é 100")
        void lidoDevolvidoSemTotal() {
            EmprestimoRelationship e = emprestimo(dia(1, 3), dia(12, 3), dia(5, 3));
            e.setStatusLeitura(StatusLeitura.LIDO);
            assertEquals(100, e.porcentagemLeitura(null));
        }
    }
}
