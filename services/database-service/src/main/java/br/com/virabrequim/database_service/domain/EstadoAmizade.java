package br.com.virabrequim.database_service.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EstadoAmizade {

    PENDENTE("pendente"),
    AMIGOS("amigos");

    private final String valor;

    EstadoAmizade(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }
}
