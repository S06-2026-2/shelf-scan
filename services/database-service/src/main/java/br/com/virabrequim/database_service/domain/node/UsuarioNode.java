package br.com.virabrequim.database_service.domain.node;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Usuario")
@Data // gera automaticamente os métodos Getters, Setters, toString(), equals() e hashCode()
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioNode {
    @Id
    @GeneratedValue
    private Long id;

    private String nome;
    private String email;
}
