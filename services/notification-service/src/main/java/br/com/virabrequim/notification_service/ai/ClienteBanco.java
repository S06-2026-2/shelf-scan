package br.com.virabrequim.notification_service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Fala com o database-service: guarda o livro lido da foto e pede o enriquecimento.
 * Contrato: docs/api/database-service.md.
 */
public class ClienteBanco {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NovoLivro(String titulo, List<String> autores, String volume, String editora, Leitura leitura) {
    }

    public record Leitura(String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LivroCriado(Long id) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PedidoEnriquecimento(String textoBruto) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Enriquecimento(List<String> camposPreenchidos) {
    }

    // O banco recusa mais que isso no textoBruto
    static final int MAXIMO_DE_CARACTERES_DO_TEXTO = 5000;

    private final RestClient restClient;

    public ClienteBanco(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Guarda o livro na estante padrão do usuário (como "quero ler") e devolve o id criado. */
    public Long salvar(Long usuarioId, String titulo, List<String> autores, String volume, String editora) {
        LivroCriado criado = restClient.post()
                .uri("/api/v1/usuario/{usuarioId}/livro", usuarioId)
                .body(new NovoLivro(titulo, autores, volume, editora, new Leitura("quero-ler")))
                .retrieve()
                .body(LivroCriado.class);
        if (criado == null || criado.id() == null) {
            throw new IllegalStateException("O banco não devolveu o id do livro.");
        }
        return criado.id();
    }

    /** Pede à IA para completar o livro. Devolve os campos que ela preencheu (vazio = nada mudou). */
    public List<String> enriquecer(Long usuarioId, Long livroId, String textoBruto) {
        String texto = textoBruto == null ? null
                : textoBruto.substring(0, Math.min(textoBruto.length(), MAXIMO_DE_CARACTERES_DO_TEXTO));
        Enriquecimento resposta = restClient.post()
                .uri("/api/v1/usuario/{usuarioId}/livro/{livroId}/enriquecimento", usuarioId, livroId)
                .body(new PedidoEnriquecimento(texto))
                .retrieve()
                .body(Enriquecimento.class);
        return resposta == null || resposta.camposPreenchidos() == null ? List.of() : resposta.camposPreenchidos();
    }
}
