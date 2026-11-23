package br.com.virabrequim.notification_service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClienteBancoTest {

    private final RestClient.Builder construtor = RestClient.builder().baseUrl("http://banco.teste");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(construtor).build();
    private final ClienteBanco cliente = new ClienteBanco(construtor.build());

    @Test
    void salvar_mandaOLivroComoQueroLer_eDevolveOId() {
        servidor.expect(requestTo("http://banco.teste/api/v1/usuario/7/livro"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.titulo").value("Memórias do Subsolo"))
                .andExpect(jsonPath("$.autores[0]").value("Fiódor Dostoiévski"))
                .andExpect(jsonPath("$.editora").value("Editora 34"))
                .andExpect(jsonPath("$.leitura.status").value("quero-ler"))
                .andExpect(jsonPath("$.volume").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":42,\"titulo\":\"Memórias do Subsolo\",\"campoQueNaoUsamos\":true}"));

        Long id = cliente.salvar(7L, "Memórias do Subsolo", List.of("Fiódor Dostoiévski"), null, "Editora 34");

        assertEquals(42L, id);
        servidor.verify();
    }

    @Test
    void salvar_comRespostaSemId_lancaExcecao() {
        servidor.expect(requestTo("http://banco.teste/api/v1/usuario/7/livro"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThrows(IllegalStateException.class, () -> cliente.salvar(7L, "X", List.of(), null, null));
    }

    @Test
    void salvar_quandoOBancoRecusa_lancaExcecao() {
        servidor.expect(requestTo("http://banco.teste/api/v1/usuario/7/livro"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(RestClientResponseException.class, () -> cliente.salvar(7L, "X", List.of(), null, null));
    }

    @Test
    void enriquecer_mandaOTextoBruto_eDevolveOsCamposPreenchidos() {
        servidor.expect(requestTo("http://banco.teste/api/v1/usuario/7/livro/42/enriquecimento"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.textoBruto").value("MEMORIAS DO SUBSOLO"))
                .andRespond(withSuccess("""
                        {"livro":{"id":42},"camposPreenchidos":["sinopse","isbn","paginas"],"aviso":"confira"}
                        """, MediaType.APPLICATION_JSON));

        List<String> campos = cliente.enriquecer(7L, 42L, "MEMORIAS DO SUBSOLO");

        assertEquals(List.of("sinopse", "isbn", "paginas"), campos);
        servidor.verify();
    }

    @Test
    void enriquecer_cortaOTextoBrutoEm5000Caracteres() {
        servidor.expect(requestTo("http://banco.teste/api/v1/usuario/7/livro/42/enriquecimento"))
                .andExpect(jsonPath("$.textoBruto").value("a".repeat(5000)))
                .andRespond(withSuccess("{\"camposPreenchidos\":[]}", MediaType.APPLICATION_JSON));

        cliente.enriquecer(7L, 42L, "a".repeat(6000));

        servidor.verify();
    }

    @Test
    void enriquecer_semTextoBruto_naoMandaOCampo() {
        servidor.expect(requestTo("http://banco.teste/api/v1/usuario/7/livro/42/enriquecimento"))
                .andExpect(jsonPath("$.textoBruto").doesNotExist())
                .andRespond(withSuccess("{\"camposPreenchidos\":[]}", MediaType.APPLICATION_JSON));

        assertEquals(List.of(), cliente.enriquecer(7L, 42L, null));
    }

    @Test
    void enriquecer_semChaveDoGemini_lancaExcecao() {
        servidor.expect(requestTo("http://banco.teste/api/v1/usuario/7/livro/42/enriquecimento"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(RestClientResponseException.class, () -> cliente.enriquecer(7L, 42L, "x"));
    }
}
