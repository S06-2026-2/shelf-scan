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

        @ParameterizedTest(name = "E12: atualizarLeitura(LENDO, {0}) lança IllegalArgumentException")
        @DisplayName("E12 (N7, N9): página inválida em LENDO não altera a leitura vazia")
        @CsvSource(value = {"NULL", "-1", "201"}, nullValues = "NULL")
        void paginaInvalida(Integer pagina) {
            LivroNode livro = livroComPaginas(200);
            assertThrows(IllegalArgumentException.class,
                    () -> livro.atualizarLeitura(StatusLeitura.LENDO, pagina, HOJE));
            assertAll(
                    () -> assertNull(livro.getStatusLeitura()),
                    () -> assertNull(livro.getPaginaAtual()),
                    () -> assertNull(livro.getDataConclusaoLeitura()));
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
        @DisplayName("E18 (N7): página negativa sem número de páginas lança IllegalArgumentException")
        void negativaSemPaginas() {
            LivroNode livro = livroComPaginas(null);
            assertThrows(IllegalArgumentException.class,
                    () -> livro.atualizarLeitura(StatusLeitura.LENDO, -1, HOJE));
        }

        @Test
        @DisplayName("E19 (N10): páginas reduzidas depois limitam a porcentagem a 100")
        void paginasAlteradasDepois() {
            LivroNode livro = livroComPaginas(200);
            livro.atualizarLeitura(StatusLeitura.LENDO, 150, HOJE);
            livro.setPaginas(100);
            assertEquals(100, livro.getPorcentagemLeitura());
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
    }
}
