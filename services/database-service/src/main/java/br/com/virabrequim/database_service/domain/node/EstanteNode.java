package br.com.virabrequim.database_service.domain.node;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Estante")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class EstanteNode {
    @Id
    @GeneratedValue
    private Long id;

    private String nome;

    public EstanteNode(String nome){
        this.nome = nome;
    }
}
