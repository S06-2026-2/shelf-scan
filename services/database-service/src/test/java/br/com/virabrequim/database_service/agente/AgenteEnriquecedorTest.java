package br.com.virabrequim.database_service.agente;

import br.com.virabrequim.database_service.agente.catalogo.CatalogoIndisponivelException;
import br.com.virabrequim.database_service.agente.catalogo.CatalogoLivros;
import br.com.virabrequim.database_service.agente.catalogo.Edicao;
import br.com.virabrequim.database_service.domain.node.AutorNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgenteEnriquecedorTest {

    private static final String ISBN = "9788573261585";
    private static final String OUTRO_ISBN = "9788535914849";

    // Um passo do LLM: chama a ferramenta com os argumentos
    record Chamada(String ferramenta, Map<String, Object> argumentos) {
    }

    // LLM falso: executa as chamadas do roteiro nas ferramentas reais do agente e conclui com o JSON dado
    static class LlmFalso implements ClienteLlm {
        final String conclusao;
        final List<Chamada> roteiro;
        final List<Map<String, Object>> resultados = new ArrayList<>();
        List<String> ferramentas;
        String ultimoPrompt;

        LlmFalso(String conclusao, Chamada... roteiro) {
            this.conclusao = conclusao;
            this.roteiro = List.of(roteiro);
        }

        @Override
        public String gerarJson(String prompt, Map<String, Object> schema, List<Ferramenta> disponiveis) {
            ultimoPrompt = prompt;
            ferramentas = disponiveis.stream().map(Ferramenta::nome).toList();
            for (Chamada chamada : roteiro) {
                resultados.add(disponiveis.stream().filter(f -> f.nome().equals(chamada.ferramenta())).findFirst()
                        .orElseThrow().executar().apply(chamada.argumentos()));
            }
            return conclusao;
        }

        @Override
        public String modelo() {
            return "falso";
        }
    }

    private CatalogoLivros catalogo;
    private LivroNode livro;

    @BeforeEach
    void preparar() {
        catalogo = mock(CatalogoLivros.class);
        when(catalogo.buscaPorTituloLigada()).thenReturn(true);
        livro = new LivroNode("Memórias do Subsolo");
        livro.setAutores(List.of(new AutorNode("Fiódor Dostoiévski")));
    }

    private static Edicao edicao(String isbn, String titulo, String editora, Integer paginas, String origem) {
        return new Edicao(isbn, titulo, null, List.of("Fiódor Dostoiévski", "Boris Schnaiderman"), editora, paginas,
                null, null, origem);
    }

    private static Edicao certa(String origem, Integer paginas) {
        return edicao(ISBN, "Memórias do subsolo", "Editora 34", paginas, origem);
    }

    private static String conclusao(String isbn) {
        return """
                {"isbn": "%s", "conheceObra": true, "sinopse": "Resumo.", "autores": ["Fiódor Dostoiévski"],
                 "tags": ["romance"]}""".formatted(isbn);
    }

    private SugestaoEnriquecimento sugerir(LlmFalso llm) {
        return new AgenteEnriquecedor(llm, catalogo, new JsonMapper()).sugerir(livro, null);
    }

    @Test
    @DisplayName("Edição achada na busca e confirmada na BrasilAPI: os dados saem do catálogo, BrasilAPI primeiro")
    void encontrada() {
        when(catalogo.buscar("Memórias do Subsolo", "Dostoiévski", "Editora 34"))
                .thenReturn(List.of(certa(CatalogoLivros.GOOGLE_BOOKS, 140)));
        when(catalogo.porIsbn(ISBN)).thenReturn(Optional.of(certa(CatalogoLivros.BRASIL_API, 152)));

        SugestaoEnriquecimento s = sugerir(new LlmFalso(conclusao("978-85-7326-158-5"),
                new Chamada("buscarEdicoes", Map.of("titulo", "Memórias do Subsolo", "autor", "Dostoiévski",
                        "editora", "Editora 34"))));

        assertThat(s.encontradoNoCatalogo()).isTrue();
        assertThat(s.isbn()).isEqualTo(ISBN);
        assertThat(s.paginas()).isEqualTo(152);
        assertThat(s.editora()).isEqualTo("Editora 34");
        assertThat(s.fonteAutores()).isEqualTo(Fonte.CATALOGO);
        assertThat(s.consultas()).containsExactly("Google Books: \"Memórias do Subsolo\", Dostoiévski, Editora 34 -> 1 edições",
                "BrasilAPI: ISBN " + ISBN + " -> Memórias do subsolo");
    }

    @Test
    @DisplayName("ISBN inventado que é de outro livro: recusado, mas a sinopse e as tags da IA ficam")
    void isbnDeOutroLivro() {
        when(catalogo.porIsbn(OUTRO_ISBN)).thenReturn(Optional.of(
                edicao(OUTRO_ISBN, "100 receitas de macarrão", "Outra", 90, CatalogoLivros.BRASIL_API)));

        SugestaoEnriquecimento s = sugerir(new LlmFalso(conclusao(OUTRO_ISBN)));

        assertThat(s.encontradoNoCatalogo()).isFalse();
        assertThat(s.isbn()).isNull();
        assertThat(s.paginas()).isNull();
        assertThat(s.editora()).isNull();
        assertThat(s.sinopse()).isEqualTo("Resumo.");
        assertThat(s.tags()).containsExactly("romance");
        assertThat(s.fonteAutores()).isEqualTo(Fonte.IA);
    }

    @Test
    @DisplayName("ISBN que nenhum catálogo conhece é recusado")
    void isbnDesconhecido() {
        assertThat(sugerir(new LlmFalso(conclusao(ISBN))).isbn()).isNull();
    }

    @Test
    @DisplayName("Se um catálogo diz que o ISBN é de outro livro, recusa mesmo que o outro confira")
    void catalogosDiscordam() {
        when(catalogo.buscar(anyString(), any(), any())).thenReturn(List.of(certa(CatalogoLivros.GOOGLE_BOOKS, 152)));
        when(catalogo.porIsbn(ISBN)).thenReturn(Optional.of(
                edicao(ISBN, "Noites brancas", "Editora 34", 96, CatalogoLivros.BRASIL_API)));

        assertThat(sugerir(new LlmFalso(conclusao(ISBN),
                new Chamada("buscarEdicoes", Map.of("titulo", "x")))).encontradoNoCatalogo()).isFalse();
    }

    @Test
    @DisplayName("Edição de outra editora que a lida pelo OCR é recusada")
    void outraEditora() {
        livro.setEditora("Companhia das Letras");
        when(catalogo.porIsbn(ISBN)).thenReturn(Optional.of(certa(CatalogoLivros.BRASIL_API, 152)));

        assertThat(sugerir(new LlmFalso(conclusao(ISBN))).isbn()).isNull();
    }

    @Test
    @DisplayName("Catálogo incompleto não impede se o outro confirma; sem nenhuma confirmação, recusa")
    void catalogosSeCompletam() {
        livro.setEditora("Editora 34");
        Edicao semEditora = edicao(ISBN, "Memórias do subsolo", null, 147, CatalogoLivros.GOOGLE_BOOKS);
        Edicao semAutores = new Edicao(ISBN, "MEMORIAS DO SUBSOLO", null, List.of(), "Editora 34", null, null, null,
                CatalogoLivros.BRASIL_API);
        when(catalogo.buscar(anyString(), any(), any())).thenReturn(List.of(semEditora));
        LlmFalso llm = new LlmFalso(conclusao(ISBN), new Chamada("buscarEdicoes", Map.of("titulo", "x")));

        assertThat(sugerir(llm).isbn()).isNull();

        when(catalogo.porIsbn(ISBN)).thenReturn(Optional.of(semAutores));
        SugestaoEnriquecimento s = sugerir(llm);
        assertThat(s.isbn()).isEqualTo(ISBN);
        assertThat(s.paginas()).isEqualTo(147);
        assertThat(s.editora()).isEqualTo("Editora 34");
    }

    @Test
    @DisplayName("Edições impressas vêm antes das digitais para o LLM; escolher a digital é aceito, mas avisado")
    void edicaoDigital() {
        Edicao digital = new Edicao(ISBN, "Memórias do subsolo", null, List.of("Fiódor Dostoiévski"), "Editora 34", 159,
                null, true, CatalogoLivros.GOOGLE_BOOKS);
        Edicao impressa = new Edicao(OUTRO_ISBN, "Memórias do subsolo", null, List.of("Fiódor Dostoiévski"),
                "Editora 34", 147, null, false, CatalogoLivros.GOOGLE_BOOKS);
        when(catalogo.buscar(anyString(), any(), any())).thenReturn(List.of(digital, impressa));
        Chamada busca = new Chamada("buscarEdicoes", Map.of("titulo", "x"));
        LlmFalso llm = new LlmFalso(conclusao(ISBN), busca);

        SugestaoEnriquecimento escolheuDigital = sugerir(llm);
        SugestaoEnriquecimento escolheuImpressa = sugerir(new LlmFalso(conclusao(OUTRO_ISBN), busca));

        assertThat(llm.resultados.getFirst().get("edicoes").toString())
                .containsSubsequence(OUTRO_ISBN, ISBN);
        assertThat(escolheuDigital.edicaoDigital()).isTrue();
        assertThat(escolheuDigital.isbn()).isEqualTo(ISBN);
        assertThat(escolheuImpressa.edicaoDigital()).isFalse();
    }

    @Test
    @DisplayName("Livro que já tem ISBN: vale a edição dele, não a escolhida pelo LLM")
    void isbnJaCadastrado() {
        livro.setIsbn("978-85-7326-158-5");
        when(catalogo.porIsbn(ISBN)).thenReturn(Optional.of(certa(CatalogoLivros.BRASIL_API, 152)));

        SugestaoEnriquecimento s = sugerir(new LlmFalso(conclusao(OUTRO_ISBN)));

        assertThat(s.isbn()).isEqualTo(ISBN);
        verify(catalogo, never()).porIsbn(OUTRO_ISBN);
    }

    @Test
    @DisplayName("Sem catálogo e sem conhecer a obra: nada é sugerido")
    void desconhecida() {
        SugestaoEnriquecimento s = sugerir(new LlmFalso("""
                {"isbn": null, "conheceObra": false, "sinopse": "Inventada", "autores": [], "tags": ["romance"]}"""));

        assertThat(s).isEqualTo(SugestaoEnriquecimento.vazia(List.of()));
    }

    @Test
    @DisplayName("Catálogo fora do ar não derruba o agente: o LLM recebe o erro e o resto segue")
    void catalogoForaDoAr() {
        when(catalogo.buscar(anyString(), any(), any())).thenThrow(new CatalogoIndisponivelException("fora", null));
        when(catalogo.porIsbn(anyString())).thenThrow(new CatalogoIndisponivelException("fora", null));
        LlmFalso llm = new LlmFalso(conclusao(ISBN), new Chamada("buscarEdicoes", Map.of("titulo", "x")));

        SugestaoEnriquecimento s = sugerir(llm);

        assertThat(llm.resultados.getFirst()).containsKey("erro");
        assertThat(s.isbn()).isNull();
        assertThat(s.sinopse()).isEqualTo("Resumo.");
        assertThat(s.consultas()).allMatch(consulta -> consulta.endsWith("indisponível"));
    }

    @Test
    @DisplayName("ISBNs de memória são limitados; o mesmo ISBN não é consultado de novo no fim")
    void limiteDeChutes() {
        LlmFalso llm = new LlmFalso(conclusao(OUTRO_ISBN),
                new Chamada("consultarIsbn", Map.of("isbn", OUTRO_ISBN)),
                new Chamada("consultarIsbn", Map.of("isbn", "9788572323215")),
                new Chamada("consultarIsbn", Map.of("isbn", "9788508154944")));

        sugerir(llm);

        verify(catalogo, never()).porIsbn("9788508154944");
        verify(catalogo, times(1)).porIsbn(OUTRO_ISBN);
        assertThat(llm.resultados.getLast().get("erro").toString()).contains("conclua");
    }

    @Test
    @DisplayName("Cada edição vai para o LLM dizendo se confere com o livro")
    void confereComOLivro() {
        when(catalogo.porIsbn(ISBN)).thenReturn(Optional.of(certa(CatalogoLivros.BRASIL_API, 152)));
        LlmFalso llm = new LlmFalso(conclusao(ISBN), new Chamada("consultarIsbn", Map.of("isbn", ISBN)));

        sugerir(llm);

        assertThat(llm.resultados.getFirst()).containsEntry("confereComOLivro", true).containsEntry("paginas", 152);
    }

    @Test
    @DisplayName("Sem chave do Google Books, só a consulta por ISBN é oferecida")
    void semBuscaPorTitulo() {
        LlmFalso ligado = new LlmFalso(conclusao(ISBN));
        sugerir(ligado);
        when(catalogo.buscaPorTituloLigada()).thenReturn(false);
        LlmFalso desligado = new LlmFalso(conclusao(ISBN));
        sugerir(desligado);

        assertThat(ligado.ferramentas).containsExactly("buscarEdicoes", "consultarIsbn");
        assertThat(desligado.ferramentas).containsExactly("consultarIsbn");
    }

    @Test
    @DisplayName("Autores e tags: sem repetidos (acento/caixa), vazios ou além de 5; sinopse longa demais é descartada")
    void travas() {
        SugestaoEnriquecimento s = sugerir(new LlmFalso("""
                {"isbn": null, "conheceObra": true, "sinopse": "%s", "autores": ["Ana  Maria", "ANA MARIA"],
                 "tags": ["Ficção", "ficcao", " ", "drama", "romance", "clássico", "russo", "filosofia"]}
                """.formatted("a".repeat(AgenteEnriquecedor.MAX_SINOPSE + 1))));

        assertThat(s.sinopse()).isNull();
        assertThat(s.autores()).containsExactly("Ana Maria");
        assertThat(s.tags()).containsExactly("Ficção", "drama", "romance", "clássico", "russo");
    }

    @Test
    @DisplayName("Os dados do livro (inclusive a editora) e o texto do OCR vão no prompt")
    void prompt() {
        livro.setEditora("Editora Trinta e Quatro");
        LlmFalso llm = new LlmFalso(conclusao(ISBN));
        new AgenteEnriquecedor(llm, catalogo, new JsonMapper()).sugerir(livro, "COLECAO LESTE");

        assertThat(llm.ultimoPrompt).contains("Memórias do Subsolo", "Fiódor Dostoiévski", "Editora Trinta e Quatro",
                "COLECAO LESTE");
    }

    @Test
    @DisplayName("JSON inválido do modelo vira AgenteIndisponivelException")
    void jsonInvalido() {
        assertThatThrownBy(() -> sugerir(new LlmFalso("isso não é json")))
                .isInstanceOf(AgenteIndisponivelException.class);
    }
}
