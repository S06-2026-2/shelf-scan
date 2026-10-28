package br.com.virabrequim.database_service.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

// No JSON aparece como "quero-ler", "lendo" e "lido" (os mesmos valores do frontend)
public enum StatusLeitura {
    QUERO_LER("quero-ler"),
    LENDO("lendo"),
    LIDO("lido");

    private final String codigo;

    StatusLeitura(String codigo) {
        this.codigo = codigo;
    }

    @JsonValue
    public String getCodigo() {
        return codigo;
    }

    // Valor desconhecido vira 400 (o Jackson embrulha a exceção num erro de leitura do corpo)
    @JsonCreator
    public static StatusLeitura doCodigo(String codigo) {
        return Arrays.stream(values())
                .filter(status -> status.codigo.equals(codigo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Status de leitura inválido: " + codigo));
    }
}
