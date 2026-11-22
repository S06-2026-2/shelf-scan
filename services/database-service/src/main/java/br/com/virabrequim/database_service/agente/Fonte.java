package br.com.virabrequim.database_service.agente;

import com.fasterxml.jackson.annotation.JsonValue;

// De onde veio cada dado sugerido
public enum Fonte {

    CATALOGO("catalogo"),
    IA("ia");

    private final String valor;

    Fonte(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }
}
