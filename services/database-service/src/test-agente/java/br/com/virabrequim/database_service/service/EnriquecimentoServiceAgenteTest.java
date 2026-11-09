package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.agente.AgenteEnriquecedor;
import br.com.virabrequim.database_service.agente.AgenteIndisponivelException;
import br.com.virabrequim.database_service.agente.SugestaoEnriquecimento;
import br.com.virabrequim.database_service.domain.StatusLeitura;
import br.com.virabrequim.database_service.domain.node.AutorNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.node.TagNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;
import br.com.virabrequim.database_service.dto.AtualizacaoLivro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnriquecimentoServiceAgenteTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 10);
    private static final SugestaoEnriquecimento COMPLETA = new SugestaoEnriquecimento(true, "Resumo",
            "9788573261585", 152, List.of("Fiódor Dostoiévski"), List.of("romance"));

    private LivroService livroService;
    private AgenteEnriquecedor agente;
    private EnriquecimentoService service;
    private LivroNode livro5;
    private LivroNode atualizado;

    @BeforeEach
    void setUp() {
        livroService = mock(LivroService.class);
        agente = mock(AgenteEnriquecedor.class);
        service = new EnriquecimentoService(livroService, agente);

        livro5 = new LivroNode("Memórias do Subsolo");
        livro5.setId(5L);
        atualizado = new LivroNode("Memórias do Subsolo");

        when(livroService.buscarDoDono(1L, 5L)).thenReturn(livro5);
        when(livroService.atualizar(anyLong(), anyLong(), any())).thenReturn(atualizado);
        when(agente.modelo()).thenReturn("gemini-teste");
    }

    private void agenteSugere(SugestaoEnriquecimento sugestao) {
        when(agente.sugerir(any(), any())).thenReturn(sugestao);
    }

    private AtualizacaoLivro dadosEnviados() {
        ArgumentCaptor<AtualizacaoLivro> captor = ArgumentCaptor.forClass(AtualizacaoLivro.class);
        verify(livroService, times(1)).atualizar(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(5L), captor.capture());
        return captor.getValue();
    }

    private EmprestimoRelationship emprestimoAtivoNaPagina(int pagina) {
        EmprestimoRelationship e = new EmprestimoRelationship(new UsuarioNode("Bruno", "bruno@exemplo.com"),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 20));
        e.atualizarLeitura(StatusLeitura.LENDO, pagina, null, HOJE);
        return e;
    }

    @Nested
    @DisplayName("Preencher campos vazios")
    class PreencherCampos {

        @Test
        @DisplayName("E1 (N3, N6, N7): livro só com título recebe todos os campos sugeridos")
        void livroVazio() {
            agenteSugere(COMPLETA);

            EnriquecimentoService.Resultado r = service.enriquecer(1L, 5L, null);

            AtualizacaoLivro dados = dadosEnviados();
            assertAll(
                    () -> assertEquals("Resumo", dados.sinopse()),
                    () -> assertEquals("9788573261585", dados.isbn()),
                    () -> assertEquals(152, dados.paginas()),
                    () -> assertEquals(List.of("Fiódor Dostoiévski"), dados.autores()),
                    () -> assertEquals(List.of("romance"), dados.tags()),
                    () -> assertNull(dados.titulo()),
                    () -> assertNull(dados.volume()),
                    () -> assertSame(atualizado, r.livro()),
                    () -> assertEquals(List.of("sinopse", "isbn", "paginas", "autores", "tags"), r.camposPreenchidos()),
                    () -> assertTrue(r.livroIdentificado()),
                    () -> assertEquals("gemini-teste", r.modelo()));
        }

        @Test
        @DisplayName("E2 (N3, N6): campos já preenchidos não são enviados; só ISBN e tags entram")
        void livroParcial() {
            livro5.setSinopse("Minha sinopse");
            livro5.setPaginas(200);
            livro5.setAutores(new ArrayList<>(List.of(new AutorNode("Dostoiévski"))));
            agenteSugere(COMPLETA);

            EnriquecimentoService.Resultado r = service.enriquecer(1L, 5L, null);

            AtualizacaoLivro dados = dadosEnviados();
            assertAll(
                    () -> assertEquals("9788573261585", dados.isbn()),
                    () -> assertEquals(List.of("romance"), dados.tags()),
                    () -> assertNull(dados.sinopse()),
                    () -> assertNull(dados.paginas()),
                    () -> assertNull(dados.autores()),
                    () -> assertEquals(List.of("isbn", "tags"), r.camposPreenchidos()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("N3 (derivado): campo sem valor na sugestão não entra mesmo com o livro vazio")
        void sugestaoParcial() {
            agenteSugere(new SugestaoEnriquecimento(true, null, null, null, List.of("Fiódor Dostoiévski"), List.of()));

            EnriquecimentoService.Resultado r = service.enriquecer(1L, 5L, null);

            AtualizacaoLivro dados = dadosEnviados();
            assertAll(
                    () -> assertNull(dados.sinopse()),
                    () -> assertNull(dados.isbn()),
                    () -> assertNull(dados.paginas()),
                    () -> assertNull(dados.tags()),
                    () -> assertEquals(List.of("Fiódor Dostoiévski"), dados.autores()),
                    () -> assertEquals(List.of("autores"), r.camposPreenchidos()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("N3 (derivado): livro com ISBN e tags e sugestão completa não altera ISBN nem tags")
        void isbnETagsPreenchidos() {
            livro5.setIsbn("123");
            livro5.setTags(new HashSet<>(Set.of(new TagNode("drama"))));
            agenteSugere(COMPLETA);

            EnriquecimentoService.Resultado r = service.enriquecer(1L, 5L, null);

            AtualizacaoLivro dados = dadosEnviados();
            assertAll(
                    () -> assertNull(dados.isbn()),
                    () -> assertNull(dados.tags()),
                    () -> assertEquals(List.of("sinopse", "paginas", "autores"), r.camposPreenchidos()));
        }
    }

    @Nested
    @DisplayName("Nenhum campo entra")
    class NenhumCampo {

        @Test
        @DisplayName("E3 (N5): livro completo não chama atualizar")
        void livroCompleto() {
            livro5.setSinopse("Minha sinopse");
            livro5.setIsbn("123");
            livro5.setPaginas(200);
            livro5.setAutores(new ArrayList<>(List.of(new AutorNode("Dostoiévski"))));
            livro5.setTags(new HashSet<>(Set.of(new TagNode("drama"))));
            agenteSugere(COMPLETA);

            EnriquecimentoService.Resultado r = service.enriquecer(1L, 5L, null);

            assertAll(
                    () -> verify(livroService, never()).atualizar(any(), any(), any()),
                    () -> assertSame(livro5, r.livro()),
                    () -> assertThat(r.camposPreenchidos()).isEmpty(),
                    () -> assertTrue(r.livroIdentificado()),
                    () -> assertEquals("gemini-teste", r.modelo()));
        }

        @Test
        @DisplayName("E4 (N5): sugestão vazia não chama atualizar e livroIdentificado = false")
        void sugestaoVazia() {
            agenteSugere(SugestaoEnriquecimento.vazia());

            EnriquecimentoService.Resultado r = service.enriquecer(1L, 5L, null);

            assertAll(
                    () -> verify(livroService, never()).atualizar(any(), any(), any()),
                    () -> assertThat(r.camposPreenchidos()).isEmpty(),
                    () -> assertFalse(r.livroIdentificado()));
        }
    }

    @Nested
    @DisplayName("Páginas e leitura")
    class PaginasELeitura {

        @Test
        @DisplayName("E5 (N4): página atual do dono maior que a sugestão descarta as páginas")
        void donoNaPagina180() {
            livro5.atualizarLeitura(StatusLeitura.LENDO, 180, HOJE);
            agenteSugere(COMPLETA);

            EnriquecimentoService.Resultado r = service.enriquecer(1L, 5L, null);

            AtualizacaoLivro dados = dadosEnviados();
            assertAll(
                    () -> assertThat(r.camposPreenchidos()).doesNotContain("paginas"),
                    () -> assertNull(dados.paginas()));
        }

        @Test
        @DisplayName("E6 (N4): página atual do empréstimo ativo igual à sugestão é aceita")
        void emprestimoNaPagina152() {
            livro5.setEmprestimos(new ArrayList<>(List.of(emprestimoAtivoNaPagina(152))));
            agenteSugere(COMPLETA);

            EnriquecimentoService.Resultado r = service.enriquecer(1L, 5L, null);

            assertThat(r.camposPreenchidos()).contains("paginas");
        }

        @Test
        @Tag("derivado")
        @DisplayName("N4 (derivado): página atual do dono igual à sugestão (152) é aceita")
        void donoNaPagina152() {
            livro5.atualizarLeitura(StatusLeitura.LENDO, 152, HOJE);
            agenteSugere(COMPLETA);

            EnriquecimentoService.Resultado r = service.enriquecer(1L, 5L, null);

            assertAll(
                    () -> assertThat(r.camposPreenchidos()).contains("paginas"),
                    () -> assertEquals(152, dadosEnviados().paginas()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("N4 (derivado): página atual do dono uma acima da sugestão (153) descarta as páginas")
        void donoNaPagina153() {
            livro5.atualizarLeitura(StatusLeitura.LENDO, 153, HOJE);
            agenteSugere(COMPLETA);

            EnriquecimentoService.Resultado r = service.enriquecer(1L, 5L, null);

            assertAll(
                    () -> assertThat(r.camposPreenchidos()).doesNotContain("paginas"),
                    () -> assertNull(dadosEnviados().paginas()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("N4 (derivado): página atual do empréstimo ativo uma acima da sugestão (153) descarta as páginas")
        void emprestimoNaPagina153() {
            livro5.setEmprestimos(new ArrayList<>(List.of(emprestimoAtivoNaPagina(153))));
            agenteSugere(COMPLETA);

            EnriquecimentoService.Resultado r = service.enriquecer(1L, 5L, null);

            assertAll(
                    () -> assertThat(r.camposPreenchidos()).doesNotContain("paginas"),
                    () -> assertNull(dadosEnviados().paginas()));
        }
    }

    @Nested
    @DisplayName("Dono e agente")
    class DonoEAgente {

        @Test
        @DisplayName("E7 (N1): livro de outro usuário lança NoSuchElementException sem chamar o agente")
        void naoDono() {
            when(livroService.buscarDoDono(2L, 5L)).thenThrow(new NoSuchElementException());

            assertThrows(NoSuchElementException.class, () -> service.enriquecer(2L, 5L, null));
            assertAll(
                    () -> verify(agente, never()).sugerir(any(), any()),
                    () -> verify(livroService, never()).atualizar(any(), any(), any()));
        }

        @Test
        @DisplayName("E8 (N2): exceção do agente não é capturada e atualizar não é chamado")
        void agenteIndisponivel() {
            when(agente.sugerir(any(), any())).thenThrow(new AgenteIndisponivelException("fora do ar"));

            assertThrows(AgenteIndisponivelException.class, () -> service.enriquecer(1L, 5L, null));
            verify(livroService, never()).atualizar(any(), any(), any());
        }

        @Test
        @DisplayName("E9 (N2): o agente recebe o livro 5 e o texto bruto")
        void repassaTextoBruto() {
            agenteSugere(SugestaoEnriquecimento.vazia());

            service.enriquecer(1L, 5L, "MEMORIAS DO SUBSOLO");

            verify(agente, times(1)).sugerir(livro5, "MEMORIAS DO SUBSOLO");
        }

        @Test
        @Tag("derivado")
        @DisplayName("N2 (derivado): texto bruto null é repassado como null")
        void textoBrutoNull() {
            agenteSugere(SugestaoEnriquecimento.vazia());

            service.enriquecer(1L, 5L, null);

            verify(agente, times(1)).sugerir(livro5, null);
        }
    }
}
