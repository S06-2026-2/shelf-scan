package br.com.virabrequim.database_service.agente;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

// Liga o agente enriquecedor ao Gemini. Para trocar de LLM, basta outro bean de ClienteLlm.
@Configuration
@EnableConfigurationProperties(PropriedadesEnriquecedor.class)
public class AgentConfig {

    @Bean
    public ClienteLlm clienteLlm(PropriedadesEnriquecedor propriedades, JsonMapper jsonMapper) {
        return new GeminiCliente(propriedades, jsonMapper);
    }
}
