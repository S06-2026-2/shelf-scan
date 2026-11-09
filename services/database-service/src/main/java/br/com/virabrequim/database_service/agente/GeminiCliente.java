package br.com.virabrequim.database_service.agente;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

// Chama a API REST do Gemini (generateContent) com saída estruturada: o modelo é obrigado a devolver um JSON no
public class GeminiCliente implements ClienteLlm {

    private final PropriedadesEnriquecedor propriedades;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public GeminiCliente(PropriedadesEnriquecedor propriedades, JsonMapper jsonMapper) {
        this.propriedades = propriedades;
        this.jsonMapper = jsonMapper;

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(httpClient);
        fabrica.setReadTimeout(propriedades.tempoLimite());

        this.restClient = RestClient.builder()
                .baseUrl(propriedades.url())
                .requestFactory(fabrica)
                .build();
    }

    @Override
    public String gerarJson(String prompt, Map<String, Object> schema) {
        if (!propriedades.ligado()) {
            throw new AgenteIndisponivelException(
                    "Agente de enriquecimento desligado: configure a variável GEMINI_API_KEY.");
        }

        Map<String, Object> corpo = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", schema,
                        // Baixa: queremos fatos sobre o livro, não criatividade
                        "temperature", 0.2));

        String resposta;
        try {
            resposta = restClient.post()
                    .uri("/models/{modelo}:generateContent", propriedades.modelo())
                    // Chave no cabeçalho, não na URL: não aparece em logs de acesso
                    .header("x-goog-api-key", propriedades.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonMapper.writeValueAsString(corpo))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new AgenteIndisponivelException(mensagemDoErro(ex), ex);
        } catch (ResourceAccessException ex) {
            throw new AgenteIndisponivelException("O Gemini não respondeu a tempo.", ex);
        } catch (RestClientException ex) {
            throw new AgenteIndisponivelException("Falha ao chamar o Gemini.", ex);
        }

        return textoGerado(resposta);
    }

    @Override
    public String modelo() {
        return propriedades.modelo();
    }

    // A resposta vem em candidates[0].content.parts[0].text (o JSON gerado, como texto)
    private String textoGerado(String resposta) {
        try {
            JsonNode texto = jsonMapper.readTree(resposta)
                    .path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (!texto.isString() || texto.asString().isBlank()) {
                throw new AgenteIndisponivelException("O Gemini não devolveu conteúdo (resposta bloqueada ou vazia).");
            }
            return texto.asString();
        } catch (JacksonException ex) {
            throw new AgenteIndisponivelException("O Gemini devolveu uma resposta ilegível.", ex);
        }
    }

    // Chave inválida volta como 400 (não 401): a mensagem do Google (error.message) diz o motivo exato
    private String mensagemDoErro(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 429) {
            return "Cota do Gemini esgotada (429): tente de novo em instantes.";
        }
        String motivo;
        try {
            motivo = jsonMapper.readTree(ex.getResponseBodyAsString()).path("error").path("message").asString("");
        } catch (JacksonException erroDeLeitura) {
            motivo = "";
        }
        return "O Gemini respondeu com erro " + status + (motivo.isBlank() ? "." : ": " + motivo);
    }
}
