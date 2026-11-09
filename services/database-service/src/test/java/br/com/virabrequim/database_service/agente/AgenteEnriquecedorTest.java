package br.com.virabrequim.database_service.agente;

import br.com.virabrequim.database_service.domain.node.AutorNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgenteEnriquecedorTest {

    // LLM falso: devolve sempre o JSON dado e guarda o último prompt recebido
    static class LlmFalso implements ClienteLlm {
        final String resposta;
        String ultimoPrompt;

        LlmFalso(String resposta) {
            this.resposta = resposta;
        }

        @Override
        public String gerarJson(String prompt, Map<String, Object> schema) {
            ultimoPrompt = prompt;
            return resposta;
        }

        @Override
        public String modelo() {
            return "falso";
        }
    }

    private static LivroNode livro() {
        LivroNode livro = new LivroNode("Memórias do Subsolo");
        livro.setAutores(List.of(new AutorNode("Fiódor Dostoiévski")));
        return livro;
    }

    private static SugestaoEnriquecimento sugerir(String json) {
        return new AgenteEnriquecedor(new LlmFalso(json), new JsonMapper()).sugerir(livro(), null);
    }

    @Test
    @DisplayName("Livro reconhecido: devolve os campos sugeridos")
    void reconhecido() {
        SugestaoEnriquecimento sugestao = sugerir("""
                {"livroIdentificado": true, "sinopse": "Um homem amargurado narra sua vida.",
                 "isbn": "978-85-7326-158-5", "paginas": 152,
                 "autores": ["Fiódor Dostoiévski"], "tags": ["romance", "clássico"]}
                """);

        assertThat(sugestao.livroIdentificado()).isTrue();
        assertThat(sugestao.sinopse()).isEqualTo("Um homem amargurado narra sua vida.");
        assertThat(sugestao.isbn()).isEqualTo("9788573261585");
        assertThat(sugestao.paginas()).isEqualTo(152);
        assertThat(sugestao.autores()).containsExactly("Fiódor Dostoiévski");
        assertThat(sugestao.tags()).containsExactly("romance", "clássico");
    }

    @Test
    @DisplayName("Livro não reconhecido: nada é sugerido, mesmo que o modelo mande campos")
    void naoReconhecido() {
        SugestaoEnriquecimento sugestao = sugerir("""
                {"livroIdentificado": false, "sinopse": "Inventada", "isbn": null, "paginas": 100,
                 "autores": [], "tags": ["romance"]}
                """);

        assertThat(sugestao).isEqualTo(SugestaoEnriquecimento.vazia());
    }

    @Test
    @DisplayName("Travas: ISBN inválido, páginas fora do limite e sinopse longa demais são descartados")
    void travas() {
        SugestaoEnriquecimento sugestao = sugerir("""
                {"livroIdentificado": true, "sinopse": "%s", "isbn": "9788535914848", "paginas": 0,
                 "autores": [], "tags": []}
                """.formatted("a".repeat(AgenteEnriquecedor.MAX_SINOPSE + 1)));

        assertThat(sugestao.sinopse()).isNull();
        assertThat(sugestao.isbn()).isNull();
        assertThat(sugestao.paginas()).isNull();
    }

    @Test
    @DisplayName("Tags repetidas (sem acento/caixa), vazias ou além de 5 são descartadas")
    void tagsFiltradas() {
        SugestaoEnriquecimento sugestao = sugerir("""
                {"livroIdentificado": true, "sinopse": null, "isbn": null, "paginas": null, "autores": [],
                 "tags": ["Ficção", "ficcao", " ", "drama", "romance", "clássico", "russo", "filosofia"]}
                """);

        assertThat(sugestao.tags()).containsExactly("Ficção", "drama", "romance", "clássico", "russo");
    }

    @Test
    @DisplayName("Os dados do livro e o texto do OCR vão no prompt")
    void prompt() {
        LlmFalso llm = new LlmFalso("""
                {"livroIdentificado": false, "sinopse": null, "isbn": null, "paginas": null,
                 "autores": [], "tags": []}
                """);
        new AgenteEnriquecedor(llm, new JsonMapper()).sugerir(livro(), "MEMORIAS DO SUBSOLO editora 34");

        assertThat(llm.ultimoPrompt)
                .contains("Memórias do Subsolo", "Fiódor Dostoiévski", "MEMORIAS DO SUBSOLO editora 34");
    }

    @Test
    @DisplayName("JSON inválido do modelo vira AgenteIndisponivelException")
    void jsonInvalido() {
        assertThatThrownBy(() -> sugerir("isso não é json"))
                .isInstanceOf(AgenteIndisponivelException.class);
    }
}
