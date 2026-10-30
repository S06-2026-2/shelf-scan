package br.com.virabrequim.database_service.domain.node;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import static org.springframework.data.neo4j.core.schema.Relationship.Direction.OUTGOING;

@Node("Cidade")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class CidadeNode {

    @Id
    private Integer codigoIbge;

    private String nome;

    @Relationship(type = "PERTENCE_A", direction = OUTGOING)
    private EstadoNode estado;

    public CidadeNode(Integer codigoIbge, String nome, EstadoNode estado) {
        if (codigoIbge == null || codigoIbge < 1_000_000 || codigoIbge > 9_999_999) {
            throw new IllegalArgumentException("O código IBGE da cidade deve ter 7 dígitos.");
        }
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome da cidade é obrigatório.");
        }
        if (estado == null || !estado.getCodigoIbge().equals(codigoDoEstado(codigoIbge))) {
            throw new IllegalArgumentException("A cidade " + codigoIbge + " não pertence ao estado informado.");
        }
        this.codigoIbge = codigoIbge;
        this.nome = nome.strip();
        this.estado = estado;
    }

    public static int codigoDoEstado(int codigoIbgeCidade) {
        return codigoIbgeCidade / 100_000;
    }
}
