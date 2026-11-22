package br.com.virabrequim.database_service.agente;

import br.com.virabrequim.database_service.agente.catalogo.CatalogoIndisponivelException;
import br.com.virabrequim.database_service.agente.catalogo.CatalogoLivros;
import br.com.virabrequim.database_service.agente.catalogo.Edicao;
import br.com.virabrequim.database_service.domain.node.AutorNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgenteEnriquecedorAgenteTest {

    private static final String ISBN_CERTO = "9788573261585";
    private static final String OUTRO_ISBN = "9788535914849";
    private static final String TITULO = "Memórias do Subsolo";
    private static final String AUTOR = "Fiódor Dostoiévski";

    private ClienteLlm clienteLlm;
    private CatalogoLivros catalogo;
    private AgenteEnriquecedor agente;
    private LivroNode livro;
    private List<Ferramenta> ferramentasRecebidas;
    private List<Map<String, Object>> respostasDasFerramentas;

    record Chamada(String ferramenta, Map<String, Object> argumentos) {
    }

    @BeforeEach
    void setUp() {
        clienteLlm = mock(ClienteLlm.class);
        catalogo = mock(CatalogoLivros.class);
        agente = new AgenteEnriquecedor(clienteLlm, catalogo, new JsonMapper());
        livro = new LivroNode(TITULO);
        livro.setAutores(new ArrayList<>(List.of(new AutorNode(AUTOR))));
        respostasDasFerramentas = new ArrayList<>();
    }

    // ClienteLlm falso: chama as ferramentas recebidas, na ordem, e depois devolve o JSON da conclusão.
    private void llm(String json, Chamada... chamadas) {
        when(clienteLlm.gerarJson(anyString(), any(), any())).thenAnswer(inv -> {
            List<Ferramenta> ferramentas = inv.getArgument(2);
            ferramentasRecebidas = ferramentas;
            for (Chamada c : chamadas) {
                Ferramenta f = ferramentas.stream().filter(x -> x.nome().equals(c.ferramenta())).findFirst()
                        .orElseThrow();
                respostasDasFerramentas.add(f.executar().apply(c.argumentos()));
            }
            return json;
        });
    }

    private static Chamada buscarEdicoes() {
        return new Chamada("buscarEdicoes", Map.of("titulo", TITULO));
    }

    private static Chamada consultarIsbn(String isbn) {
        return new Chamada("consultarIsbn", Map.of("isbn", isbn));
    }

    private static Edicao edicao(String isbn, String titulo, Integer paginas, Boolean digital, String origem) {
        return new Edicao(isbn, titulo, null, List.of(AUTOR), "Editora 34", paginas, null, digital, origem);
    }

    private static String mesmoIsbn(String isbn) {
        return argThat(s -> s != null && Objects.equals(Isbn.como13(s), Isbn.como13(isbn)));
    }

    private void brasilApiTem(Edicao e) {
        when(catalogo.porIsbn(mesmoIsbn(e.isbn()))).thenReturn(Optional.of(e));
    }

    private void googleBooksTem(Edicao... edicoes) {
        when(catalogo.buscaPorTituloLigada()).thenReturn(true);
        when(catalogo.buscar(any(), any(), any())).thenReturn(List.of(edicoes));
    }

    private SugestaoEnriquecimento sugerir(String json, Chamada... chamadas) {
        llm(json, chamadas);
        return agente.sugerir(livro, null);
    }

    private SugestaoEnriquecimento sugerirComPaginas(Integer paginas) {
        brasilApiTem(edicao(ISBN_CERTO, TITULO, paginas, null, CatalogoLivros.BRASIL_API));
        return sugerir("{\"conheceObra\": true, \"isbn\": \"" + ISBN_CERTO + "\"}");
    }

    private String promptRecebido() {
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(clienteLlm, times(1)).gerarJson(prompt.capture(), any(), any());
        return prompt.getValue();
    }

    private static String isbn13(String dozeDigitos) {
        int soma = 0;
        for (int i = 0; i < 12; i++) {
            soma += (dozeDigitos.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return dozeDigitos + (10 - soma % 10) % 10;
    }

    @Nested
    @DisplayName("Conferência da edição")
    class Conferencia {

        @Test
        @DisplayName("E1 (A7, A9, A12): edição certa usa ISBN, páginas da BrasilAPI e editora do catálogo")
        void edicaoCerta() {
            googleBooksTem(edicao(ISBN_CERTO, TITULO, 140, false, CatalogoLivros.GOOGLE_BOOKS));
            brasilApiTem(edicao(ISBN_CERTO, TITULO, 152, false, CatalogoLivros.BRASIL_API));
            SugestaoEnriquecimento s = sugerir("""
                    {"conheceObra": true, "isbn": "978-85-7326-158-5", "autores": ["Fiódor Dostoiévski"]}""",
                    buscarEdicoes());
            assertAll(
                    () -> assertTrue(s.encontradoNoCatalogo()),
                    () -> assertEquals(ISBN_CERTO, s.isbn()),
                    () -> assertEquals(152, s.paginas()),
                    () -> assertEquals("Editora 34", s.editora()),
                    () -> assertEquals(Fonte.CATALOGO, s.fonteAutores()));
        }

        @Test
        @DisplayName("E2 (A8): ISBN de outro livro deixa ISBN, páginas e editora null, mas sinopse e tags ficam")
        void isbnDeOutroLivro() {
            brasilApiTem(edicao(OUTRO_ISBN, "100 receitas de macarrão", 90, null, CatalogoLivros.BRASIL_API));
            SugestaoEnriquecimento s = sugerir("{\"conheceObra\": true, \"isbn\": \"" + OUTRO_ISBN
                    + "\", \"sinopse\": \"Resumo\", \"tags\": [\"romance\"]}");
            assertAll(
                    () -> assertNull(s.isbn()),
                    () -> assertNull(s.paginas()),
                    () -> assertNull(s.editora()),
                    () -> assertEquals("Resumo", s.sinopse()),
                    () -> assertEquals(List.of("romance"), s.tags()));
        }

        @Test
        @DisplayName("E3 (A7): ISBN que nenhum catálogo conhece fica null")
        void isbnDesconhecido() {
            assertNull(sugerir("{\"conheceObra\": true, \"isbn\": \"" + OUTRO_ISBN + "\"}").isbn());
        }

        @Test
        @DisplayName("E4 (A7): BrasilAPI com outro título para o mesmo ISBN recusa a edição")
        void catalogosDiscordam() {
            googleBooksTem(edicao(ISBN_CERTO, TITULO, 140, false, CatalogoLivros.GOOGLE_BOOKS));
            brasilApiTem(edicao(ISBN_CERTO, "100 receitas de macarrão", 152, false, CatalogoLivros.BRASIL_API));
            SugestaoEnriquecimento s = sugerir("{\"conheceObra\": true, \"isbn\": \"" + ISBN_CERTO + "\"}",
                    buscarEdicoes());
            assertFalse(s.encontradoNoCatalogo());
        }

        @Test
        @DisplayName("E5 (A7): vale o ISBN do livro e o da conclusão não é consultado")
        void isbnDoLivro() {
            livro.setIsbn(ISBN_CERTO);
            brasilApiTem(edicao(ISBN_CERTO, TITULO, 152, false, CatalogoLivros.BRASIL_API));
            SugestaoEnriquecimento s = sugerir("{\"conheceObra\": true, \"isbn\": \"" + OUTRO_ISBN + "\"}");
            assertAll(
                    () -> assertTrue(s.encontradoNoCatalogo()),
                    () -> assertEquals(ISBN_CERTO, s.isbn()),
                    () -> verify(catalogo, never()).porIsbn(mesmoIsbn(OUTRO_ISBN)));
        }

        @Test
        @DisplayName("E6 (A8): isbn null e conheceObra false devolvem a sugestão vazia")
        void naoConheceAObra() {
            SugestaoEnriquecimento s = sugerir("""
                    {"conheceObra": false, "isbn": null, "sinopse": "Inventada", "tags": ["romance"]}""");
            assertEquals(SugestaoEnriquecimento.vazia(s.consultas()), s);
        }

        @Test
        @Tag("derivado")
        @DisplayName("A8 (derivado): conheceObra ausente e sem edição aceita devolve a sugestão vazia")
        void conheceObraAusente() {
            SugestaoEnriquecimento s = sugerir("{\"sinopse\": \"Algo\", \"tags\": [\"romance\"]}");
            assertEquals(SugestaoEnriquecimento.vazia(s.consultas()), s);
        }

        @Test
        @Tag("derivado")
        @DisplayName("A8 (derivado): ISBN com dígito verificador errado não é aceito, mas a sinopse fica")
        void isbnInvalidoNaConclusao() {
            SugestaoEnriquecimento s = sugerir(
                    "{\"conheceObra\": true, \"isbn\": \"9788535914848\", \"sinopse\": \"Resumo\"}");
            assertAll(
                    () -> assertFalse(s.encontradoNoCatalogo()),
                    () -> assertNull(s.isbn()),
                    () -> assertNull(s.paginas()),
                    () -> assertNull(s.editora()),
                    () -> assertEquals("Resumo", s.sinopse()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("A9 (derivado): edição aceita que um catálogo diz ser digital marca edicaoDigital")
        void edicaoDigital() {
            googleBooksTem(edicao(ISBN_CERTO, TITULO, 140, true, CatalogoLivros.GOOGLE_BOOKS));
            brasilApiTem(edicao(ISBN_CERTO, TITULO, 152, null, CatalogoLivros.BRASIL_API));
            SugestaoEnriquecimento s = sugerir("{\"conheceObra\": true, \"isbn\": \"" + ISBN_CERTO + "\"}",
                    buscarEdicoes());
            assertAll(
                    () -> assertTrue(s.encontradoNoCatalogo()),
                    () -> assertTrue(s.edicaoDigital()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("A12 (derivado): autor que não aparece na edição aceita deixa fonteAutores = IA")
        void autorForaDaEdicao() {
            brasilApiTem(edicao(ISBN_CERTO, TITULO, 152, null, CatalogoLivros.BRASIL_API));
            SugestaoEnriquecimento s = sugerir("{\"conheceObra\": true, \"isbn\": \"" + ISBN_CERTO
                    + "\", \"autores\": [\"Outro Autor\"]}");
            assertEquals(Fonte.IA, s.fonteAutores());
        }

        @ParameterizedTest(name = "E11: {0} páginas no catálogo → {1}")
        @DisplayName("E11 (A9): páginas de 1 a 10.000 são aceitas; 10.001 ou null viram null")
        @CsvSource(value = {"10000, 10000", "1, 1", "10001, NULL", "NULL, NULL"}, nullValues = "NULL")
        void paginas(Integer doCatalogo, Integer esperado) {
            assertEquals(esperado, sugerirComPaginas(doCatalogo).paginas());
        }
    }

    @Nested
    @DisplayName("Ferramentas")
    class Ferramentas {

        @Test
        @DisplayName("E7 (A4): catálogo indisponível faz a ferramenta devolver erro, sem lançar, e a sinopse fica")
        void catalogoIndisponivel() {
            when(catalogo.porIsbn(anyString())).thenThrow(new CatalogoIndisponivelException("fora do ar", null));
            llm("{\"conheceObra\": true, \"isbn\": null, \"sinopse\": \"Resumo\"}", consultarIsbn(ISBN_CERTO));
            SugestaoEnriquecimento s = assertDoesNotThrow(() -> agente.sugerir(livro, null));
            assertAll(
                    () -> assertThat(respostasDasFerramentas.get(0)).containsKey("erro"),
                    () -> assertEquals("Resumo", s.sinopse()));
        }

        @Test
        @DisplayName("E8 (A5): o terceiro ISBN de memória não chega ao catálogo")
        void tresIsbnsDeMemoria() {
            String terceiro = "080442957X";
            sugerir("{\"conheceObra\": false}",
                    consultarIsbn(OUTRO_ISBN), consultarIsbn(ISBN_CERTO), consultarIsbn(terceiro));
            assertAll(
                    () -> verify(catalogo, never()).porIsbn(mesmoIsbn(terceiro)),
                    () -> assertThat(respostasDasFerramentas.get(2)).containsKey("erro"));
        }

        @Test
        @DisplayName("E9 (A2): sem busca por título, a única ferramenta é consultarIsbn")
        void semBuscaPorTitulo() {
            when(catalogo.buscaPorTituloLigada()).thenReturn(false);
            sugerir("{\"conheceObra\": false}");
            assertThat(ferramentasRecebidas).extracting(Ferramenta::nome).containsExactly("consultarIsbn");
        }

        @Test
        @Tag("derivado")
        @DisplayName("A2 (derivado): com busca por título, as ferramentas são buscarEdicoes e consultarIsbn")
        void comBuscaPorTitulo() {
            when(catalogo.buscaPorTituloLigada()).thenReturn(true);
            sugerir("{\"conheceObra\": false}");
            assertThat(ferramentasRecebidas).extracting(Ferramenta::nome)
                    .containsExactlyInAnyOrder("buscarEdicoes", "consultarIsbn");
        }

        @Test
        @Tag("derivado")
        @DisplayName("A3 (derivado): 12 edições viram no máximo 10, com a que confere primeiro")
        void dezEdicoesConferidasPrimeiro() {
            List<Edicao> edicoes = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                edicoes.add(edicao(isbn13("97800000000" + i), "100 receitas de macarrão", 90, false,
                        CatalogoLivros.GOOGLE_BOOKS));
            }
            edicoes.add(edicao(ISBN_CERTO, TITULO, 152, false, CatalogoLivros.GOOGLE_BOOKS));
            googleBooksTem(edicoes.toArray(Edicao[]::new));
            sugerir("{\"conheceObra\": false}", buscarEdicoes());
            // A spec não diz em que chave do resultado vem a lista de edições: procura a lista no resultado.
            List<?> devolvidas = respostasDasFerramentas.get(0).values().stream()
                    .filter(List.class::isInstance).map(List.class::cast).findFirst().orElseThrow();
            assertAll(
                    () -> assertThat(devolvidas).hasSize(10),
                    () -> assertEquals(true, ((Map<?, ?>) devolvidas.get(0)).get("confereComOLivro")));
        }

        @Test
        @Tag("derivado")
        @DisplayName("A4 (derivado): ISBN inválido faz a ferramenta devolver erro")
        void isbnInvalido() {
            sugerir("{\"conheceObra\": false}", consultarIsbn("123"));
            assertThat(respostasDasFerramentas.get(0)).containsKey("erro");
        }

        @Test
        @Tag("derivado")
        @DisplayName("A4 (derivado): ISBN não encontrado faz a ferramenta devolver erro")
        void isbnNaoEncontrado() {
            sugerir("{\"conheceObra\": false}", consultarIsbn(OUTRO_ISBN));
            assertThat(respostasDasFerramentas.get(0)).containsKey("erro");
        }

        @Test
        @Tag("derivado")
        @DisplayName("A5 (derivado): os 2 ISBNs de memória chegam ao catálogo e o ISBN que veio da busca não conta")
        void isbnDaBuscaNaoConta() {
            googleBooksTem(edicao(ISBN_CERTO, TITULO, 140, false, CatalogoLivros.GOOGLE_BOOKS));
            brasilApiTem(edicao(ISBN_CERTO, TITULO, 152, false, CatalogoLivros.BRASIL_API));
            sugerir("{\"conheceObra\": false}", buscarEdicoes(), consultarIsbn(OUTRO_ISBN),
                    consultarIsbn("080442957X"), consultarIsbn(ISBN_CERTO));
            assertAll(
                    () -> verify(catalogo).porIsbn(mesmoIsbn(OUTRO_ISBN)),
                    () -> verify(catalogo).porIsbn(mesmoIsbn("080442957X")),
                    () -> verify(catalogo, atLeastOnce()).porIsbn(mesmoIsbn(ISBN_CERTO)),
                    () -> assertThat(respostasDasFerramentas.get(3)).doesNotContainKey("erro"));
        }
    }

    @Nested
    @DisplayName("Resposta do LLM")
    class Resposta {

        @Test
        @DisplayName("E12 (A6): texto que não é JSON lança AgenteIndisponivelException")
        void jsonInvalido() {
            llm("isso não é json");
            assertThrows(AgenteIndisponivelException.class, () -> agente.sugerir(livro, null));
        }

        @Test
        @Tag("derivado")
        @DisplayName("A6 (derivado): exceção do ClienteLlm não é capturada")
        void excecaoDoCliente() {
            AgenteIndisponivelException erro = new AgenteIndisponivelException("sem chave");
            when(clienteLlm.gerarJson(anyString(), any(), any())).thenThrow(erro);
            assertSame(erro, assertThrows(AgenteIndisponivelException.class, () -> agente.sugerir(livro, null)));
        }

        @Test
        @Tag("derivado")
        @DisplayName("A10 (derivado): sinopse com 1501 caracteres vira null")
        void sinopse1501() {
            assertNull(sugerir("{\"conheceObra\": true, \"sinopse\": \"" + "a".repeat(1501) + "\"}").sinopse());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A10 (derivado): 1500 caracteres mais espaços nas pontas é mantida sem os espaços")
        void sinopse1500ComEspacos() {
            String sinopse = "a".repeat(1500);
            assertEquals(sinopse,
                    sugerir("{\"conheceObra\": true, \"sinopse\": \"  " + sinopse + "  \"}").sinopse());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A10 (derivado): sinopse em branco vira null")
        void sinopseEmBranco() {
            assertNull(sugerir("{\"conheceObra\": true, \"sinopse\": \"   \"}").sinopse());
        }

        @Test
        @DisplayName("E10 (A11): tags repetidas sem acento/caixa e em branco são descartadas; no máximo 5")
        void tags() {
            SugestaoEnriquecimento s = sugerir("""
                    {"conheceObra": true, "tags": ["Ficção", "ficcao", " ", "drama", "romance", "clássico",
                     "russo", "filosofia"]}""");
            assertEquals(List.of("Ficção", "drama", "romance", "clássico", "russo"), s.tags());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A11 (derivado): autores com espaços normalizados, sem repetidos e sem itens que não são texto")
        void autores() {
            SugestaoEnriquecimento s = sugerir(
                    "{\"conheceObra\": true, \"autores\": [\"  Ana   Maria \", \"ANA MARIA\", 42]}");
            assertEquals(List.of("Ana Maria"), s.autores());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A11 (derivado): tag com 40 caracteres é mantida e com 41 é descartada")
        void limiteTag() {
            String t40 = "b".repeat(40);
            String t41 = "c".repeat(41);
            SugestaoEnriquecimento s = sugerir("{\"conheceObra\": true, \"tags\": [\"" + t40 + "\", \"" + t41 + "\"]}");
            assertEquals(List.of(t40), s.tags());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A11 (derivado): autor com 120 caracteres é mantido e com 121 é descartado")
        void limiteAutor() {
            String a120 = "b".repeat(120);
            String a121 = "c".repeat(121);
            SugestaoEnriquecimento s = sugerir(
                    "{\"conheceObra\": true, \"autores\": [\"" + a120 + "\", \"" + a121 + "\"]}");
            assertEquals(List.of(a120), s.autores());
        }
    }

    @Nested
    @DisplayName("Prompt")
    class Prompt {

        @BeforeEach
        void naoConhece() {
            llm("{\"conheceObra\": false}");
        }

        @Test
        @Tag("derivado")
        @DisplayName("A1 (derivado): o prompt contém título, autores, volume, editora, ISBN e texto bruto")
        void conteudoDoPrompt() {
            livro.setVolume("Volume-Dois");
            livro.setEditora("Editora-Trinta-e-Quatro");
            livro.setIsbn(ISBN_CERTO);
            agente.sugerir(livro, "MEMORIAS DO SUBSOLO editora 34");
            String prompt = promptRecebido();
            assertAll(
                    () -> assertThat(prompt).contains(TITULO),
                    () -> assertThat(prompt).contains(AUTOR),
                    () -> assertThat(prompt).contains("Volume-Dois"),
                    () -> assertThat(prompt).contains("Editora-Trinta-e-Quatro"),
                    () -> assertThat(prompt).contains(ISBN_CERTO),
                    () -> assertThat(prompt).contains("MEMORIAS DO SUBSOLO editora 34"));
        }

        @Test
        @Tag("derivado")
        @DisplayName("A1 (derivado): texto bruto com mais de 2000 caracteres é cortado nos 2000 primeiros")
        void textoBrutoCortado() {
            String inicio = "q".repeat(2000);
            agente.sugerir(livro, inicio + "ZWK");
            String prompt = promptRecebido();
            assertAll(
                    () -> assertThat(prompt).contains(inicio),
                    () -> assertThat(prompt).doesNotContain("ZWK"));
        }

        @Test
        @Tag("derivado")
        @DisplayName("A1 (derivado): o texto bruto entra sem os espaços das pontas, e o corte vale depois deles")
        void textoBrutoSemEspacos() {
            String texto = "w".repeat(2000);
            agente.sugerir(livro, "     " + texto + "     ");
            String prompt = promptRecebido();
            assertAll(
                    () -> assertThat(prompt).contains(texto),
                    () -> assertThat(prompt).doesNotContain(" " + texto),
                    () -> assertThat(prompt).doesNotContain(texto + " "));
        }
    }

    @Test
    @Tag("derivado")
    @DisplayName("A14 (derivado): modelo() repassa o modelo do ClienteLlm")
    void modelo() {
        when(clienteLlm.modelo()).thenReturn("falso");
        assertEquals("falso", agente.modelo());
    }
}
