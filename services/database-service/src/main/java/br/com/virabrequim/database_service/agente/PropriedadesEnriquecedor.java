package br.com.virabrequim.database_service.agente;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// Configuração do agente enriquecedor (bloco agente.enriquecedor do application.yaml).
// apiKey vazia = agente desligado: a rota responde 503 e o resto da API funciona normalmente.
@ConfigurationProperties("agente.enriquecedor")
public record PropriedadesEnriquecedor(
        String apiKey,
        String modelo,
        String url,
        Duration tempoLimite
) {

    public boolean ligado() {
        return apiKey != null && !apiKey.isBlank();
    }
}
