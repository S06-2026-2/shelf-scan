package br.com.virabrequim.database_service.domain.node;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.Locale;

@Node("Estado")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class EstadoNode {

    @Id
    private Integer codigoIbge;

    private String sigla;

    public EstadoNode(Integer codigoIbge, String sigla) {
        if (codigoIbge == null || codigoIbge < 10 || codigoIbge > 99) {
            throw new IllegalArgumentException("O código IBGE do estado deve ter 2 dígitos.");
        }
        if (sigla == null || !sigla.strip().matches("[A-Za-z]{2}")) {
            throw new IllegalArgumentException("A sigla do estado deve ter 2 letras.");
        }
        this.codigoIbge = codigoIbge;
        this.sigla = sigla.strip().toUpperCase(Locale.ROOT);
    }
}
