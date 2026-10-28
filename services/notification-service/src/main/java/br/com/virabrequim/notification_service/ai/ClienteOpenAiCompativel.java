package br.com.virabrequim.notification_service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClienteOpenAiCompativel implements ClienteLlm {

    private final RestClient http;
    private final String modelo;
    private final int maxTokens;
    private final String esforcoDeRaciocinio;
    private final boolean buscaNaWeb;

    public ClienteOpenAiCompativel(
            RestClient http, String modelo, int maxTokens, String esforcoDeRaciocinio, boolean buscaNaWeb) {
        this.http = http;
        this.modelo = modelo;
        this.maxTokens = maxTokens;
        this.esforcoDeRaciocinio = esforcoDeRaciocinio;
        this.buscaNaWeb = buscaNaWeb;
    }

    @Override
    public String conversar(String instrucoes, List<Turno> turnos) {
        List<Map<String, String>> mensagens = new ArrayList<>();
        mensagens.add(Map.of("role", "system", "content", instrucoes));
        for (Turno turno : turnos) {
            mensagens.add(Map.of("role", papel(turno.papel()), "content", turno.texto()));
        }

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("model", modelo);
        corpo.put("max_completion_tokens", maxTokens);
        corpo.put("messages", mensagens);
        if (!esforcoDeRaciocinio.isBlank()) {
            corpo.put("reasoning_effort", esforcoDeRaciocinio);
        }
        if (buscaNaWeb) {
            corpo.put("tools", List.of(Map.of("type", "browser_search")));
        }

        JsonNode resposta = http.post().uri("/chat/completions").body(corpo).retrieve().body(JsonNode.class);

        String texto = resposta == null
                ? ""
                : resposta.path("choices").path(0).path("message").path("content").asText("");
        if (texto.isBlank()) {
            throw new IllegalStateException("A resposta do modelo veio sem texto.");
        }
        return texto.strip();
    }

    private static String papel(Papel papel) {
        return papel == Papel.USUARIO ? "user" : "assistant";
    }
}
