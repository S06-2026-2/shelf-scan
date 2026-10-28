package br.com.virabrequim.notification_service.config;

import br.com.virabrequim.notification_service.ai.ClienteLlm;
import br.com.virabrequim.notification_service.ai.ClienteOpenAiCompativel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class LlmConfig {

    // Só existe quando há chave: sem ela o agente conversacional fica desativado e o serviço sobe normalmente.
    @Bean
    @ConditionalOnExpression("!'${llm.api-key:}'.isBlank()")
    public ClienteLlm clienteLlm(
            @Value("${llm.api-key}") String chave,
            @Value("${llm.url-base}") String urlBase,
            @Value("${llm.modelo-conversa}") String modelo,
            @Value("${llm.esforco-de-raciocinio:}") String esforcoDeRaciocinio,
            @Value("${llm.conversa.max-tokens}") int maxTokens,
            @Value("${llm.busca-na-web:false}") boolean buscaNaWeb) {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(Duration.ofSeconds(30));

        RestClient http = RestClient.builder()
                .baseUrl(urlBase)
                .requestFactory(fabrica)
                .defaultHeader("Authorization", "Bearer " + chave)
                .build();
        return new ClienteOpenAiCompativel(http, modelo, maxTokens, esforcoDeRaciocinio, buscaNaWeb);
    }
}
