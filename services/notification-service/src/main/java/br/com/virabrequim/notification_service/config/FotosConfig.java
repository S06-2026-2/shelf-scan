package br.com.virabrequim.notification_service.config;

import br.com.virabrequim.notification_service.ai.ClienteBanco;
import br.com.virabrequim.notification_service.ai.ClienteOcr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class FotosConfig {

    // Sem OCR_URL_BASE o bot não lê fotos e o serviço sobe normalmente.
    @Bean
    @ConditionalOnExpression("!'${ocr.url-base:}'.isBlank()")
    public ClienteOcr clienteOcr(
            @Value("${ocr.url-base}") String urlBase,
            @Value("${ocr.token-interno:}") String tokenInterno) {
        // A leitura leva de 4 a ~30 s (capa ou lombada, mais o agente do OCR): 60 s cobre o pior caso
        return new ClienteOcr(restClient(urlBase, Duration.ofSeconds(60)), tokenInterno);
    }

    // Sem DATABASE_SERVICE_URL o bot não guarda livros e o serviço sobe normalmente.
    @Bean
    @ConditionalOnExpression("!'${database.url-base:}'.isBlank()")
    public ClienteBanco clienteBanco(@Value("${database.url-base}") String urlBase) {
        // Salvar é rápido, mas o enriquecimento consulta catálogos e o modelo: até ~90 s
        return new ClienteBanco(restClient(urlBase, Duration.ofSeconds(90)));
    }

    private static RestClient restClient(String urlBase, Duration leitura) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrica.setReadTimeout(leitura);
        return RestClient.builder().baseUrl(urlBase).requestFactory(fabrica).build();
    }
}
