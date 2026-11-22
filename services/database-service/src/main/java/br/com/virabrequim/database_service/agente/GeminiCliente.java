package br.com.virabrequim.database_service.agente;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Chama a API REST do Gemini (generateContent) com function calling. Cada rodada: o modelo pede uma ou mais
// ferramentas, o código executa e devolve os resultados na conversa. A conclusão também é uma função ("concluir"), cujos
// parâmetros são o schema da resposta: assim o modelo é obrigado a devolver um JSON no formato pedido.
public class GeminiCliente implements ClienteLlm {

    static final String CONCLUIR = "concluir";
    // Na última rodada (ou depois do prazo) só a conclusão é permitida: o agente nunca fica em laço
    static final int MAX_RODADAS = 6;
    private static final Duration PRAZO_DAS_CONSULTAS = Duration.ofSeconds(25);

    private final PropriedadesEnriquecedor propriedades;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;
    private final Duration esperaEntreTentativas;

    public GeminiCliente(PropriedadesEnriquecedor propriedades, JsonMapper jsonMapper) {
        this(propriedades, jsonMapper, Duration.ofSeconds(1));
    }

    // Só para testes: espera curta entre as tentativas
    GeminiCliente(PropriedadesEnriquecedor propriedades, JsonMapper jsonMapper, Duration esperaEntreTentativas) {
        this.propriedades = propriedades;
        this.jsonMapper = jsonMapper;
        this.esperaEntreTentativas = esperaEntreTentativas;

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(httpClient);
        fabrica.setReadTimeout(propriedades.tempoLimite());

        this.restClient = RestClient.builder()
                .baseUrl(propriedades.url())
                .requestFactory(fabrica)
                .build();
    }

    @Override
    public String gerarJson(String prompt, Map<String, Object> schema, List<Ferramenta> ferramentas) {
        if (!propriedades.ligado()) {
            throw new AgenteIndisponivelException(
                    "Agente de enriquecimento desligado: configure a variável GEMINI_API_KEY.");
        }

        List<Object> conversa = new ArrayList<>();
        conversa.add(Map.of("role", "user", "parts", List.of(Map.of("text", prompt))));
        long limite = System.nanoTime() + PRAZO_DAS_CONSULTAS.toNanos();

        for (int rodada = 1; rodada <= MAX_RODADAS; rodada++) {
            boolean soConcluir = rodada == MAX_RODADAS || System.nanoTime() > limite;
            JsonNode conteudo = conteudoGerado(chamarComNovaTentativa(corpo(conversa, schema, ferramentas, soConcluir)));

            List<JsonNode> chamadas = new ArrayList<>();
            for (JsonNode parte : conteudo.path("parts")) {
                if (parte.path("functionCall").isObject()) {
                    chamadas.add(parte.path("functionCall"));
                }
            }
            for (JsonNode chamada : chamadas) {
                if (CONCLUIR.equals(chamada.path("name").asString(""))) {
                    return jsonMapper.writeValueAsString(chamada.path("args"));
                }
            }
            if (chamadas.isEmpty() || soConcluir) {
                break;
            }
            // O conteúdo do modelo volta como veio: ele carrega as assinaturas de raciocínio que o Gemini exige
            conversa.add(conteudo);
            conversa.add(Map.of("role", "user", "parts",
                    chamadas.stream().map(chamada -> resultado(chamada, ferramentas)).toList()));
        }
        throw new AgenteIndisponivelException("O Gemini não concluiu o enriquecimento.");
    }

    private Map<String, Object> corpo(List<Object> conversa, Map<String, Object> schema, List<Ferramenta> ferramentas,
            boolean soConcluir) {
        List<Object> funcoes = new ArrayList<>();
        for (Ferramenta ferramenta : ferramentas) {
            funcoes.add(Map.of("name", ferramenta.nome(), "description", ferramenta.descricao(),
                    "parameters", ferramenta.parametros()));
        }
        funcoes.add(Map.of("name", CONCLUIR, "description", "Encerra o trabalho e devolve o resultado.",
                "parameters", schema));

        // ANY = responder sempre com uma chamada de função, nunca com texto solto
        Map<String, Object> modo = new LinkedHashMap<>();
        modo.put("mode", "ANY");
        if (soConcluir) {
            modo.put("allowedFunctionNames", List.of(CONCLUIR));
        }
        return Map.of(
                "contents", conversa,
                "tools", List.of(Map.of("functionDeclarations", funcoes)),
                "toolConfig", Map.of("functionCallingConfig", modo),
                // Baixa: queremos fatos sobre o livro, não criatividade
                "generationConfig", Map.of("temperature", 0.2));
    }

    private Map<String, Object> resultado(JsonNode chamada, List<Ferramenta> ferramentas) {
        String nome = chamada.path("name").asString("");
        Map<String, Object> argumentos = chamada.path("args").isObject()
                ? jsonMapper.convertValue(chamada.path("args"), new TypeReference<Map<String, Object>>() { })
                : Map.of();
        Map<String, Object> retorno = ferramentas.stream()
                .filter(ferramenta -> ferramenta.nome().equals(nome))
                .findFirst()
                .map(ferramenta -> ferramenta.executar().apply(argumentos))
                .orElse(Map.of("erro", "Ferramenta desconhecida."));

        Map<String, Object> resposta = new LinkedHashMap<>();
        if (chamada.path("id").isString()) {
            resposta.put("id", chamada.path("id").asString());
        }
        resposta.put("name", nome);
        resposta.put("response", retorno);
        return Map.of("functionResponse", resposta);
    }

    // Cota (429) e falha do servidor (5xx) costumam ser passageiras: tenta mais uma vez antes de desistir
    private String chamarComNovaTentativa(Map<String, Object> corpo) {
        try {
            return chamar(corpo);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status != 429 && status < 500) {
                throw new AgenteIndisponivelException(mensagemDoErro(ex), ex);
            }
        } catch (ResourceAccessException ex) {
            throw new AgenteIndisponivelException("O Gemini não respondeu a tempo.", ex);
        } catch (RestClientException ex) {
            throw new AgenteIndisponivelException("Falha ao chamar o Gemini.", ex);
        }

        try {
            Thread.sleep(esperaEntreTentativas);
            return chamar(corpo);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AgenteIndisponivelException("O enriquecimento foi interrompido.", ex);
        } catch (RestClientResponseException ex) {
            throw new AgenteIndisponivelException(mensagemDoErro(ex), ex);
        } catch (RestClientException ex) {
            throw new AgenteIndisponivelException("Falha ao chamar o Gemini.", ex);
        }
    }

    private String chamar(Map<String, Object> corpo) {
        return restClient.post()
                .uri("/models/{modelo}:generateContent", propriedades.modelo())
                // Chave no cabeçalho, não na URL: não aparece em logs de acesso
                .header("x-goog-api-key", propriedades.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonMapper.writeValueAsString(corpo))
                .retrieve()
                .body(String.class);
    }

    @Override
    public String modelo() {
        return propriedades.modelo();
    }

    // A resposta vem em candidates[0].content
    private JsonNode conteudoGerado(String resposta) {
        try {
            JsonNode conteudo = jsonMapper.readTree(resposta).path("candidates").path(0).path("content");
            if (!conteudo.path("parts").isArray()) {
                throw new AgenteIndisponivelException("O Gemini não devolveu conteúdo (resposta bloqueada ou vazia).");
            }
            return conteudo;
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
