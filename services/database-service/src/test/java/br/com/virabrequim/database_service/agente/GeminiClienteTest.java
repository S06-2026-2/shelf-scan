package br.com.virabrequim.database_service.agente;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiClienteTest {

    private static final Map<String, Object> SCHEMA = Map.of("type", "OBJECT");

    private final ServidorFalso gemini = new ServidorFalso();
    private final JsonMapper jsonMapper = new JsonMapper();
    private final List<Map<String, Object>> argumentosRecebidos = new ArrayList<>();
    private final Ferramenta soma = new Ferramenta("soma", "soma", Map.of("type", "OBJECT"), argumentos -> {
        argumentosRecebidos.add(argumentos);
        return Map.of("total", 3);
    });

    @AfterEach
    void fechar() {
        gemini.close();
    }

    private GeminiCliente cliente(String chave) {
        return new GeminiCliente(new PropriedadesEnriquecedor(chave, "modelo-teste", gemini.url(),
                Duration.ofSeconds(5)), jsonMapper, Duration.ofMillis(1));
    }

    private String gerar() {
        return cliente("chave").gerarJson("oi", SCHEMA, List.of(soma));
    }

    private static String chamada(String nome, String argumentos) {
        return """
                {"candidates": [{"content": {"role": "model", "parts": [
                  {"functionCall": {"name": "%s", "id": "c1", "args": %s}, "thoughtSignature": "assinatura"}]}}]}
                """.formatted(nome, argumentos);
    }

    private JsonNode corpo(int indice) {
        return jsonMapper.readTree(gemini.corpos.get(indice));
    }

    @Test
    @DisplayName("Executa a ferramenta pedida, devolve o resultado na conversa e retorna os argumentos da conclusão")
    void cicloCompleto() {
        gemini.responde(200, chamada("soma", "{\"a\": 1}")).responde(200, chamada("concluir", "{\"isbn\": \"x\"}"));

        assertThat(jsonMapper.readTree(gerar()).path("isbn").asString()).isEqualTo("x");

        assertThat(argumentosRecebidos).containsExactly(Map.of("a", 1));
        JsonNode conversa = corpo(1).path("contents");
        assertThat(conversa.size()).isEqualTo(3);
        assertThat(conversa.path(1).toString()).contains("assinatura");
        JsonNode resultado = conversa.path(2).path("parts").path(0).path("functionResponse");
        assertThat(resultado.path("name").asString()).isEqualTo("soma");
        assertThat(resultado.path("id").asString()).isEqualTo("c1");
        assertThat(resultado.path("response").path("total").asInt()).isEqualTo(3);
        assertThat(gemini.chaves).containsOnly("chave");
    }

    @Test
    @DisplayName("Na última rodada só a conclusão é permitida, e depois dela o agente desiste")
    void limiteDeRodadas() {
        for (int i = 0; i < GeminiCliente.MAX_RODADAS; i++) {
            gemini.responde(200, chamada("soma", "{}"));
        }

        assertThatThrownBy(this::gerar).isInstanceOf(AgenteIndisponivelException.class);

        assertThat(gemini.corpos).hasSize(GeminiCliente.MAX_RODADAS);
        assertThat(corpo(0).toString()).doesNotContain("allowedFunctionNames");
        assertThat(corpo(GeminiCliente.MAX_RODADAS - 1).path("toolConfig").path("functionCallingConfig")
                .path("allowedFunctionNames").path(0).asString()).isEqualTo("concluir");
    }

    @Test
    @DisplayName("Ferramenta que não existe: o modelo recebe um erro e a conversa continua")
    void ferramentaDesconhecida() {
        gemini.responde(200, chamada("inventada", "{}")).responde(200, chamada("concluir", "{}"));

        gerar();

        assertThat(gemini.corpos.get(1)).contains("Ferramenta desconhecida");
    }

    @Test
    @DisplayName("Cota (429) e erro do servidor (5xx): tenta mais uma vez")
    void novaTentativa() {
        gemini.responde(429, "{}").responde(200, chamada("concluir", "{}"));
        assertThat(gerar()).isEqualTo("{}");

        gemini.responde(503, "{}").responde(503, "{}");
        assertThatThrownBy(this::gerar).isInstanceOf(AgenteIndisponivelException.class).hasMessageContaining("503");
        assertThat(gemini.corpos).hasSize(4);
    }

    @Test
    @DisplayName("Erro do pedido (400) não é repetido e traz o motivo do Google")
    void erroDoPedido() {
        gemini.responde(400, "{\"error\": {\"message\": \"API key not valid\"}}");

        assertThatThrownBy(this::gerar).isInstanceOf(AgenteIndisponivelException.class)
                .hasMessageContaining("API key not valid");
        assertThat(gemini.corpos).hasSize(1);
    }

    @Test
    @DisplayName("Resposta vazia ou bloqueada e falta de chave viram AgenteIndisponivelException")
    void semConteudo() {
        gemini.responde(200, "{\"candidates\": []}");

        assertThatThrownBy(this::gerar).isInstanceOf(AgenteIndisponivelException.class);
        assertThatThrownBy(() -> cliente("").gerarJson("oi", SCHEMA, List.of()))
                .isInstanceOf(AgenteIndisponivelException.class).hasMessageContaining("GEMINI_API_KEY");
        assertThat(gemini.corpos).hasSize(1);
    }
}
