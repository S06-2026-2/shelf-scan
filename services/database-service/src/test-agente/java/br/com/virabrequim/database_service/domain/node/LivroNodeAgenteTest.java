package br.com.virabrequim.database_service.domain.node;

import br.com.virabrequim.database_service.domain.StatusLeitura;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LivroNodeAgenteTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 10);

    // Construtor de sistema do EmprestimoRelationship ainda não existe: estado montado por setters.
    private static EmprestimoRelationship ativo() {
        EmprestimoRelationship e = new EmprestimoRelationship();
        e.setDataEmprestimo(LocalDate.of(2026, 3, 1));
        e.setDataPrevistaDevolucao(LocalDate.of(2026, 3, 12));
        return e;
    }

    private static EmprestimoRelationship devolvido() {
        EmprestimoRelationship e = new EmprestimoRelationship();
        e.setDataEmprestimo(LocalDate.of(2026, 2, 1));
        e.setDataPrevistaDevolucao(LocalDate.of(2026, 2, 10));
        e.setDataDevolucao(LocalDate.of(2026, 2, 8));
        return e;
    }

    private static LivroNode livroCom(EmprestimoRelationship... emprestimos) {
        LivroNode livro = new LivroNode();
        livro.setEmprestimos(new ArrayList<>(List.of(emprestimos)));
        return livro;
    }

    private static LivroNode livroComPaginas(Integer paginas) {
        LivroNode livro = new LivroNode();
        livro.setPaginas(paginas);
        return livro;
    }

    @Test
    @DisplayName("E1 (N1): livro novo tem listas vazias e leitura vazia")
    void livroNovo() {
        LivroNode livro = new LivroNode();
        assertAll(
                () -> assertThat(livro.getTags()).isNotNull().isEmpty(),
                () -> assertThat(livro.getAutores()).isNotNull().isEmpty(),
                () -> assertThat(livro.getEmprestimos()).isNotNull().isEmpty(),
                () -> assertNull(livro.getStatusLeitura()),
                () -> assertNull(livro.getPaginaAtual()),
                () -> assertNull(livro.getDataConclusaoLeitura()),
                () -> assertNull(livro.getPorcentagemLeitura()));
    }

    @Nested
    @DisplayName("Empréstimo ativo")
    class EmprestimoAtivo {

        @Test
        @DisplayName("E2 (N2, N4): livro sem empréstimos não está emprestado")
        void semEmprestimos() {
            LivroNode livro = new LivroNode();
            assertAll(
                    () -> assertThat(livro.emprestimoAtivo()).isEmpty(),
                    () -> assertFalse(livro.isEmprestado()));
        }

        @Test
        @DisplayName("E3 (N2, N4): com um devolvido e um ativo, retorna o ativo")
        void umDevolvidoUmAtivo() {
            EmprestimoRelationship ativo = ativo();
            LivroNode livro = livroCom(devolvido(), ativo);
            assertAll(
                    () -> assertThat(livro.emprestimoAtivo()).containsSame(ativo),
                    () -> assertTrue(livro.isEmprestado()));
        }

        @Test
        @DisplayName("E4 (N2, N4): com dois devolvidos, não há empréstimo ativo")
        void doisDevolvidos() {
            LivroNode livro = livroCom(devolvido(), devolvido());
            assertAll(
                    () -> assertThat(livro.emprestimoAtivo()).isEmpty(),
                    () -> assertFalse(livro.isEmprestado()));
        }

        @Test
        @DisplayName("E5 (N3): dois empréstimos ativos lança IllegalStateException com mensagem")
        void doisAtivos() {
            LivroNode livro = livroCom(ativo(), ativo());
            IllegalStateException ex = assertThrows(IllegalStateException.class, livro::emprestimoAtivo);
            assertEquals("Livro com mais de um empréstimo ativo", ex.getMessage());
        }

        @Test
        @Tag("derivado")
        @DisplayName("N2 (derivado): lista só com um empréstimo ativo retorna esse empréstimo")
        void soUmAtivo() {
            EmprestimoRelationship ativo = ativo();
            LivroNode livro = livroCom(ativo);
            assertThat(livro.emprestimoAtivo()).containsSame(ativo);
        }
    }

    @Nested
    @DisplayName("Leitura do dono")
    class Leitura {

        @ParameterizedTest(name = "{0}: atualizarLeitura({1}, {2}) → {3}, página {4}, {5}%, conclusão {6}")
        @DisplayName("E6–E11 (N6, N7, N8, N10): livro de 200 páginas")
        @CsvSource({
                "E6, QUERO_LER, 50, QUERO_LER, 0, 0, ",
                "E7, LENDO, 0, LENDO, 0, 0, ",
                "E8, LENDO, 80, LENDO, 80, 40, ",
                "E9, LENDO, 133, LENDO, 133, 66, ",
                "E10, LENDO, 200, LENDO, 200, 100, ",
                "E11, LIDO, 30, LIDO, 200, 100, 2026-03-10"
        })
        void livroDe200Paginas(String id, StatusLeitura status, Integer pagina, StatusLeitura statusEsperado,
                Integer paginaEsperada, Integer porcentagem, LocalDate conclusao) {
            LivroNode livro = livroComPaginas(200);
            livro.atualizarLeitura(status, pagina, HOJE);
            assertAll(
                    () -> assertEquals(statusEsperado, livro.getStatusLeitura()),
                    () -> assertEquals(paginaEsperada, livro.getPaginaAtual()),
                    () -> assertEquals(porcentagem, livro.getPorcentagemLeitura()),
                    () -> assertEquals(conclusao, livro.getDataConclusaoLeitura()));
        }

        @ParameterizedTest(name = "E12: atualizarLeitura(LENDO, {0}) fica LENDO na página 0, sem exceção")
        @DisplayName("E12 (N7): página inválida em LENDO vira 0, sem exceção")
        @CsvSource(value = {"NULL", "-1", "201"}, nullValues = "NULL")
        void paginaInvalida(Integer pagina) {
            LivroNode livro = livroComPaginas(200);
            assertDoesNotThrow(() -> livro.atualizarLeitura(StatusLeitura.LENDO, pagina, HOJE));
            assertAll(
                    () -> assertEquals(StatusLeitura.LENDO, livro.getStatusLeitura()),
                    () -> assertEquals(0, livro.getPaginaAtual()));
        }

        @Test
        @DisplayName("E13 (N5, N9): status null lança IllegalArgumentException e mantém LENDO na página 80")
        void statusNull() {
            LivroNode livro = livroComPaginas(200);
            livro.atualizarLeitura(StatusLeitura.LENDO, 80, HOJE);
            assertThrows(IllegalArgumentException.class, () -> livro.atualizarLeitura(null, 90, HOJE));
            assertAll(
                    () -> assertEquals(StatusLeitura.LENDO, livro.getStatusLeitura()),
                    () -> assertEquals(80, livro.getPaginaAtual()));
        }

        @Test
        @DisplayName("E14 (N7): sair de LIDO para LENDO apaga a data de conclusão")
        void lidoParaLendo() {
            LivroNode livro = livroComPaginas(200);
            livro.atualizarLeitura(StatusLeitura.LIDO, null, LocalDate.of(2026, 3, 1));
            livro.atualizarLeitura(StatusLeitura.LENDO, 10, HOJE);
            assertAll(
                    () -> assertEquals(StatusLeitura.LENDO, livro.getStatusLeitura()),
                    () -> assertEquals(10, livro.getPaginaAtual()),
                    () -> assertNull(livro.getDataConclusaoLeitura()));
        }

        @Test
        @DisplayName("E15 (N8): marcar LIDO de novo atualiza a data de conclusão")
        void releitura() {
            LivroNode livro = livroComPaginas(200);
            livro.atualizarLeitura(StatusLeitura.LIDO, null, LocalDate.of(2026, 3, 1));
            livro.atualizarLeitura(StatusLeitura.LIDO, null, HOJE);
            assertEquals(HOJE, livro.getDataConclusaoLeitura());
        }

        @ParameterizedTest(name = "{0}: atualizarLeitura({1}, {2}) → {3}, página {4}, {5}%, conclusão {6}")
        @DisplayName("E16–E17 (N7, N8, N10): livro sem número de páginas")
        @CsvSource({
                "E16, LENDO, 500, LENDO, 500, , ",
                "E17, LIDO, 30, LIDO, , 100, 2026-03-10"
        })
        void livroSemPaginas(String id, StatusLeitura status, Integer pagina, StatusLeitura statusEsperado,
                Integer paginaEsperada, Integer porcentagem, LocalDate conclusao) {
            LivroNode livro = livroComPaginas(null);
            livro.atualizarLeitura(status, pagina, HOJE);
            assertAll(
                    () -> assertEquals(statusEsperado, livro.getStatusLeitura()),
                    () -> assertEquals(paginaEsperada, livro.getPaginaAtual()),
                    () -> assertEquals(porcentagem, livro.getPorcentagemLeitura()),
                    () -> assertEquals(conclusao, livro.getDataConclusaoLeitura()));
        }

        @Test
        @DisplayName("E18 (N7): página negativa sem número de páginas fica LENDO na página 0")
        void negativaSemPaginas() {
            LivroNode livro = livroComPaginas(null);
            assertDoesNotThrow(() -> livro.atualizarLeitura(StatusLeitura.LENDO, -1, HOJE));
            assertAll(
                    () -> assertEquals(StatusLeitura.LENDO, livro.getStatusLeitura()),
                    () -> assertEquals(0, livro.getPaginaAtual()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("N5 (derivado): status null num livro novo lança IllegalArgumentException e a leitura continua vazia")
        void statusNullEmLivroNovo() {
            LivroNode livro = livroComPaginas(200);
            assertThrows(IllegalArgumentException.class, () -> livro.atualizarLeitura(null, 10, HOJE));
            assertAll(
                    () -> assertNull(livro.getStatusLeitura()),
                    () -> assertNull(livro.getPaginaAtual()),
                    () -> assertNull(livro.getDataConclusaoLeitura()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("N6 (derivado): sair de LIDO para QUERO_LER zera a página e apaga a conclusão")
        void lidoParaQueroLer() {
            LivroNode livro = livroComPaginas(200);
            livro.atualizarLeitura(StatusLeitura.LIDO, null, LocalDate.of(2026, 3, 1));
            livro.atualizarLeitura(StatusLeitura.QUERO_LER, 50, HOJE);
            assertAll(
                    () -> assertEquals(StatusLeitura.QUERO_LER, livro.getStatusLeitura()),
                    () -> assertEquals(0, livro.getPaginaAtual()),
                    () -> assertNull(livro.getDataConclusaoLeitura()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("N10 (derivado): LENDO na página 199 de 200 é 99%")
        void penultimaPagina() {
            LivroNode livro = livroComPaginas(200);
            livro.atualizarLeitura(StatusLeitura.LENDO, 199, HOJE);
            assertEquals(99, livro.getPorcentagemLeitura());
        }

        @Test
        @Tag("derivado")
        @DisplayName("N10 (derivado): LENDO na página 1 de 200 é 0% (0,5 arredondado para baixo)")
        void primeiraPagina() {
            LivroNode livro = livroComPaginas(200);
            livro.atualizarLeitura(StatusLeitura.LENDO, 1, HOJE);
            assertEquals(0, livro.getPorcentagemLeitura());
        }

        @Test
        @Tag("derivado")
        @DisplayName("N7 (derivado): de LIDO para LENDO com página acima do total fica na página 0 e sem conclusão")
        void lidoParaLendoPaginaInvalida() {
            LivroNode livro = livroComPaginas(200);
            livro.atualizarLeitura(StatusLeitura.LIDO, null, LocalDate.of(2026, 3, 1));
            livro.atualizarLeitura(StatusLeitura.LENDO, 201, HOJE);
            assertAll(
                    () -> assertEquals(StatusLeitura.LENDO, livro.getStatusLeitura()),
                    () -> assertEquals(0, livro.getPaginaAtual()),
                    () -> assertNull(livro.getDataConclusaoLeitura()));
        }
    }

    @Nested
    @DisplayName("Título e número de páginas")
    class TituloEPaginas {

        private LivroNode lendoNaPagina150() {
            LivroNode livro = livroComPaginas(200);
            livro.atualizarLeitura(StatusLeitura.LENDO, 150, HOJE);
            return livro;
        }

        private LivroNode lido() {
            LivroNode livro = livroComPaginas(200);
            livro.atualizarLeitura(StatusLeitura.LIDO, null, HOJE);
            return livro;
        }

        private EmprestimoRelationship comLeitura(EmprestimoRelationship e, StatusLeitura status, Integer pagina) {
            e.setStatusLeitura(status);
            e.setPaginaAtual(pagina);
            return e;
        }

        @Test
        @DisplayName("E19 (N13, N14): LENDO na 150 e setPaginas(100) lança IllegalStateException sem mudar nada")
        void reduzAbaixoDaPaginaLida() {
            LivroNode livro = lendoNaPagina150();
            assertThrows(IllegalStateException.class, () -> livro.setPaginas(100));
            assertAll(
                    () -> assertEquals(200, livro.getPaginas()),
                    () -> assertEquals(StatusLeitura.LENDO, livro.getStatusLeitura()),
                    () -> assertEquals(150, livro.getPaginaAtual()));
        }

        @ParameterizedTest(name = "E20: setPaginas({0}) muda o total e mantém a página 150")
        @DisplayName("E20 (N13): LENDO na 150 aceita total igual ou maior")
        @CsvSource({"150", "300"})
        void totalIgualOuMaior(Integer paginas) {
            LivroNode livro = lendoNaPagina150();
            livro.setPaginas(paginas);
            assertAll(
                    () -> assertEquals(paginas, livro.getPaginas()),
                    () -> assertEquals(150, livro.getPaginaAtual()));
        }

        @Test
        @DisplayName("E21 (N13): LIDO acompanha o novo total; sem total a página fica null e a porcentagem 100")
        void lidoAcompanhaTotal() {
            LivroNode livro = lido();
            livro.setPaginas(180);
            assertEquals(180, livro.getPaginaAtual());
            livro.setPaginas(0);
            assertAll(
                    () -> assertNull(livro.getPaginas()),
                    () -> assertNull(livro.getPaginaAtual()),
                    () -> assertEquals(100, livro.getPorcentagemLeitura()));
        }

        @Test
        @DisplayName("E22 (N12): setPaginas(0) e setPaginas(null) deixam null; negativo lança IllegalArgumentException")
        void setPaginasValores() {
            LivroNode zero = livroComPaginas(200);
            zero.setPaginas(0);
            LivroNode nulo = livroComPaginas(200);
            nulo.setPaginas(null);
            LivroNode negativo = new LivroNode();
            assertAll(
                    () -> assertNull(zero.getPaginas()),
                    () -> assertNull(nulo.getPaginas()),
                    () -> assertThrows(IllegalArgumentException.class, () -> negativo.setPaginas(-5)));
        }

        @Test
        @DisplayName("E23 (N13): empréstimo ativo LENDO na 120 impede total 100 e aceita total 120")
        void emprestimoAtivoLendo() {
            LivroNode livro = livroCom(comLeitura(ativo(), StatusLeitura.LENDO, 120));
            livro.setPaginas(200);
            assertThrows(IllegalStateException.class, () -> livro.setPaginas(100));
            livro.setPaginas(120);
            assertEquals(120, livro.getPaginas());
        }

        @Test
        @DisplayName("E24 (N13): empréstimo devolvido não é verificado nem alterado")
        void emprestimoDevolvidoIgnorado() {
            EmprestimoRelationship devolvido = comLeitura(devolvido(), StatusLeitura.LENDO, 190);
            LivroNode livro = livroCom(devolvido);
            livro.setPaginas(200);
            livro.setPaginas(100);
            assertAll(
                    () -> assertEquals(100, livro.getPaginas()),
                    () -> assertEquals(190, devolvido.getPaginaAtual()));
        }

        @Test
        @DisplayName("E25 (N11): título sem espaços nas pontas; vazio ou null lança IllegalArgumentException")
        void titulo() {
            LivroNode livro = new LivroNode();
            assertAll(
                    () -> assertEquals("Duna", new LivroNode("  Duna ").getTitulo()),
                    () -> assertThrows(IllegalArgumentException.class, () -> new LivroNode(" ")),
                    () -> assertThrows(IllegalArgumentException.class, () -> livro.setTitulo(null)));
        }

        @Test
        @Tag("derivado")
        @DisplayName("N11 (derivado): setTitulo inválido mantém o título atual")
        void tituloInvalidoMantemAtual() {
            LivroNode livro = new LivroNode("Duna");
            assertThrows(IllegalArgumentException.class, () -> livro.setTitulo("   "));
            assertEquals("Duna", livro.getTitulo());
        }

        @Test
        @Tag("derivado")
        @DisplayName("N11 (derivado): construtor com título vazio lança IllegalArgumentException")
        void construtorTituloVazio() {
            assertThrows(IllegalArgumentException.class, () -> new LivroNode(""));
        }

        @Test
        @Tag("derivado")
        @DisplayName("N12 (derivado): setPaginas(1) (menor positivo) é guardado")
        void umaPagina() {
            LivroNode livro = new LivroNode();
            livro.setPaginas(1);
            assertEquals(1, livro.getPaginas());
        }

        @Test
        @Tag("derivado")
        @DisplayName("N12 (derivado): setPaginas(-1) (maior negativo) lança IllegalArgumentException")
        void menosUmaPagina() {
            LivroNode livro = new LivroNode();
            assertThrows(IllegalArgumentException.class, () -> livro.setPaginas(-1));
        }

        @Test
        @Tag("derivado")
        @DisplayName("N13 (derivado): QUERO_LER não muda ao alterar o total")
        void queroLerNaoMuda() {
            LivroNode livro = livroComPaginas(200);
            livro.atualizarLeitura(StatusLeitura.QUERO_LER, null, HOJE);
            livro.setPaginas(100);
            assertAll(
                    () -> assertEquals(100, livro.getPaginas()),
                    () -> assertEquals(StatusLeitura.QUERO_LER, livro.getStatusLeitura()),
                    () -> assertEquals(0, livro.getPaginaAtual()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("N13 (derivado): LENDO na 150 e setPaginas(0) aceita (sem total não há limite)")
        void lendoSemTotal() {
            LivroNode livro = lendoNaPagina150();
            livro.setPaginas(0);
            assertAll(
                    () -> assertNull(livro.getPaginas()),
                    () -> assertEquals(150, livro.getPaginaAtual()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("N13 (derivado): empréstimo ativo LIDO acompanha o novo total")
        void emprestimoAtivoLidoAcompanha() {
            EmprestimoRelationship ativo = comLeitura(ativo(), StatusLeitura.LIDO, 200);
            LivroNode livro = livroCom(ativo);
            livro.setPaginas(200);
            livro.setPaginas(180);
            assertEquals(180, ativo.getPaginaAtual());
        }

        @Test
        @Tag("derivado")
        @DisplayName("N14 (derivado): setPaginas negativo não muda o total nem a leitura LIDO")
        void negativoNaoMudaNada() {
            LivroNode livro = lido();
            assertThrows(IllegalArgumentException.class, () -> livro.setPaginas(-5));
            assertAll(
                    () -> assertEquals(200, livro.getPaginas()),
                    () -> assertEquals(200, livro.getPaginaAtual()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("N14 (derivado): conflito no empréstimo ativo não altera o total nem a leitura LIDO do dono")
        void conflitoNoEmprestimoNaoMudaDono() {
            EmprestimoRelationship ativo = comLeitura(ativo(), StatusLeitura.LENDO, 150);
            LivroNode livro = livroCom(ativo);
            livro.setPaginas(200);
            livro.atualizarLeitura(StatusLeitura.LIDO, null, HOJE);
            assertThrows(IllegalStateException.class, () -> livro.setPaginas(100));
            assertAll(
                    () -> assertEquals(200, livro.getPaginas()),
                    () -> assertEquals(200, livro.getPaginaAtual()),
                    () -> assertEquals(150, ativo.getPaginaAtual()));
        }
    }
}
