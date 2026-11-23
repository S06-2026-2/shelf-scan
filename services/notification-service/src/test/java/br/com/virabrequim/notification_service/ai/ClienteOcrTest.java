package br.com.virabrequim.notification_service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClienteOcrTest {

    private static final byte[] FOTO = {1, 2, 3, 4};

    private static final String ESTRUTURADO = """
            {"books":[{"nome":"Memórias do Subsolo","volume":null,"autores":["Fiódor Dostoiévski"],
            "editora":"Editora 34"}],"raw_text":"MEMORIAS DO SUBSOLO","result":"structured","meta":{"elapsed_ms":900}}
            """;

    private final RestClient.Builder construtor = RestClient.builder().baseUrl("http://ocr.teste");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(construtor).build();

    private ClienteOcr cliente(String token) {
        return new ClienteOcr(construtor.build(), token);
    }

    @Test
    void ler_enviaAFotoNoCampoImage_comOTokenInterno() {
        servidor.expect(requestTo("http://ocr.teste/v1/extraction"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "segredo"))
                .andExpect(requisicao -> {
                    String corpo = ((MockClientHttpRequest) requisicao).getBodyAsString();
                    assertTrue(corpo.contains("name=\"image\""), "a foto deve ir no campo image");
                    assertTrue(corpo.contains("filename=\"capa.jpg\""));
                })
                .andRespond(withSuccess(ESTRUTURADO, MediaType.APPLICATION_JSON));

        ClienteOcr.Leitura leitura = cliente("segredo").ler(FOTO);

        assertTrue(leitura.estruturado());
        assertEquals("Memórias do Subsolo", leitura.titulo());
        assertEquals(List.of("Fiódor Dostoiévski"), leitura.autores());
        assertEquals("Editora 34", leitura.editora());
        assertNull(leitura.volume());
        assertEquals("MEMORIAS DO SUBSOLO", leitura.textoBruto());
        servidor.verify();
    }

    @Test
    void ler_semTokenConfigurado_naoMandaOCabecalho() {
        servidor.expect(requestTo("http://ocr.teste/v1/extraction"))
                .andExpect(headerDoesNotExist("X-Internal-Token"))
                .andRespond(withSuccess(ESTRUTURADO, MediaType.APPLICATION_JSON));

        cliente("").ler(FOTO);

        servidor.verify();
    }

    @Test
    void ler_quandoOOcrSoDevolveTextoBruto_naoEstruturado() {
        servidor.expect(requestTo("http://ocr.teste/v1/extraction"))
                .andRespond(withSuccess("""
                        {"books":[{"nome":null,"volume":null,"autores":null,"editora":null}],
                        "raw_text":"FIO} DOR","result":"unstructured"}
                        """, MediaType.APPLICATION_JSON));

        ClienteOcr.Leitura leitura = cliente("").ler(FOTO);

        assertFalse(leitura.estruturado());
        assertNull(leitura.titulo());
        assertEquals(List.of(), leitura.autores());
        assertEquals("FIO} DOR", leitura.textoBruto());
    }

    @Test
    void ler_comResultadoEstruturadoMasSemTitulo_naoContaComoEstruturado() {
        servidor.expect(requestTo("http://ocr.teste/v1/extraction"))
                .andRespond(withSuccess("""
                        {"books":[{"nome":"  ","autores":[]}],"raw_text":"x","result":"structured"}
                        """, MediaType.APPLICATION_JSON));

        assertFalse(cliente("").ler(FOTO).estruturado());
    }

    @Test
    void ler_semNenhumLivroNaResposta_devolveLeituraVazia() {
        servidor.expect(requestTo("http://ocr.teste/v1/extraction"))
                .andRespond(withSuccess("""
                        {"books":[],"raw_text":"","result":"no_text"}
                        """, MediaType.APPLICATION_JSON));

        ClienteOcr.Leitura leitura = cliente("").ler(FOTO);

        assertFalse(leitura.estruturado());
        assertEquals(List.of(), leitura.autores());
    }

    @Test
    void ler_comErroDoOcr_lancaExcecao() {
        servidor.expect(requestTo("http://ocr.teste/v1/extraction"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"corrupt_image\",\"message\":\"x\"}"));

        assertThrows(RestClientResponseException.class, () -> cliente("").ler(FOTO));
    }
}
