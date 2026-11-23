package br.com.virabrequim.notification_service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Fala com o ocr-service: envia a foto (campo "image") e devolve o que ele leu.
 * Contrato: services/ocr-service/docs/contrato-api.md (POST /v1/extraction).
 */
public class ClienteOcr {

    /** O que o bot usa da resposta. estruturado = o OCR achou o título. */
    public record Leitura(boolean estruturado, String titulo, List<String> autores, String volume, String editora,
            String textoBruto) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LivroDoOcr(String nome, String volume, List<String> autores, String editora) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaDoOcr(List<LivroDoOcr> books, @JsonProperty("raw_text") String rawText, String result) {
    }

    private final RestClient restClient;
    private final String tokenInterno;

    public ClienteOcr(RestClient restClient, String tokenInterno) {
        this.restClient = restClient;
        this.tokenInterno = tokenInterno == null ? "" : tokenInterno;
    }

    public Leitura ler(byte[] foto) {
        HttpHeaders cabecalhoDaParte = new HttpHeaders();
        cabecalhoDaParte.setContentType(MediaType.IMAGE_JPEG);
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("image", new HttpEntity<>(new ByteArrayResource(foto) {
            @Override
            public String getFilename() {
                return "capa.jpg";
            }
        }, cabecalhoDaParte));

        RestClient.RequestBodySpec requisicao = restClient.post()
                .uri("/v1/extraction")
                .contentType(MediaType.MULTIPART_FORM_DATA);
        if (!tokenInterno.isBlank()) {
            requisicao = requisicao.header("X-Internal-Token", tokenInterno);
        }
        RespostaDoOcr resposta = requisicao.body(corpo).retrieve().body(RespostaDoOcr.class);
        if (resposta == null) {
            throw new IllegalStateException("O OCR respondeu sem corpo.");
        }

        LivroDoOcr livro = resposta.books() == null || resposta.books().isEmpty() ? null : resposta.books().get(0);
        String titulo = livro == null ? null : livro.nome();
        boolean estruturado = "structured".equals(resposta.result()) && titulo != null && !titulo.isBlank();
        return new Leitura(
                estruturado,
                titulo,
                livro == null || livro.autores() == null ? List.of() : livro.autores(),
                livro == null ? null : livro.volume(),
                livro == null ? null : livro.editora(),
                resposta.rawText());
    }
}
