package br.com.virabrequim.database_service.agente.catalogo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// Bloco agente.catalogo do application.yaml. Sem googleBooksApiKey não há busca por título, só consulta por ISBN.
@ConfigurationProperties("agente.catalogo")
public record PropriedadesCatalogo(
        String googleBooksUrl,
        String googleBooksApiKey,
        String brasilApiUrl,
        Duration tempoLimite
) {

    public boolean googleBooksLigado() {
        return googleBooksApiKey != null && !googleBooksApiKey.isBlank();
    }
}
