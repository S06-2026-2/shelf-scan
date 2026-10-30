package br.com.virabrequim.notification_service.ai;

import br.com.virabrequim.notification_service.ai.ClienteLlm.Papel;
import br.com.virabrequim.notification_service.ai.ClienteLlm.Turno;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClienteOpenAiCompativelTest {

    private static final String URL = "https://api.exemplo.test/v1/chat/completions";

    private final RestClient.Builder construtor = RestClient.builder().baseUrl("https://api.exemplo.test/v1");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(construtor).build();
    private final RestClient http = construtor.build();

    private static final List<Turno> CONVERSA = List.of(
            new Turno(Papel.USUARIO, "Oi"),
            new Turno(Papel.ASSISTENTE, "Olá!"),
            new Turno(Papel.USUARIO, "Indique um livro"));

    @Test
    void enviaModeloInstrucoesEMensagensNoFormatoDaApi() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("modelo-x"))
                .andExpect(jsonPath("$.max_completion_tokens").value(500))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("Seja breve."))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Oi"))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[3].role").value("user"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"  Leia Dom Casmurro.  \"}}]}",
                        MediaType.APPLICATION_JSON));

        String resposta = new ClienteOpenAiCompativel(http, "modelo-x", 500, "low", false)
                .conversar("Seja breve.", CONVERSA);

        assertEquals("Leia Dom Casmurro.", resposta);
        servidor.verify();
    }

    @Test
    void semEsforcoConfigurado_naoEnviaReasoningEffort() {
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.reasoning_effort").doesNotExist())
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", MediaType.APPLICATION_JSON));

        new ClienteOpenAiCompativel(http, "modelo-x", 500, "", false).conversar("Seja breve.", CONVERSA);

        servidor.verify();
    }

    @Test
    void comBuscaNaWeb_enviaAFerramentaBrowserSearch() {
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.tools[0].type").value("browser_search"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", MediaType.APPLICATION_JSON));

        new ClienteOpenAiCompativel(http, "modelo-x", 500, "low", true).conversar("Seja breve.", CONVERSA);

        servidor.verify();
    }

    @Test
    void semBuscaNaWeb_naoEnviaFerramentas() {
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", MediaType.APPLICATION_JSON));

        new ClienteOpenAiCompativel(http, "modelo-x", 500, "low", false).conversar("Seja breve.", CONVERSA);

        servidor.verify();
    }

    @Test
    void respostaSemTexto_lancaErro() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"\"}}]}", MediaType.APPLICATION_JSON));

        ClienteOpenAiCompativel cliente = new ClienteOpenAiCompativel(http, "modelo-x", 500, "low", false);

        assertThrows(IllegalStateException.class, () -> cliente.conversar("Seja breve.", CONVERSA));
    }

    @Test
    void erroDaApi_lancaExcecao() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ClienteOpenAiCompativel cliente = new ClienteOpenAiCompativel(http, "modelo-x", 500, "low", false);

        assertThrows(RestClientException.class, () -> cliente.conversar("Seja breve.", CONVERSA));
    }
}
