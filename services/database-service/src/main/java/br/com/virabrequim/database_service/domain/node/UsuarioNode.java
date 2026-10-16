package br.com.virabrequim.database_service.domain.node;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Usuario")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioNode {
    @Id
    @GeneratedValue
    private Long id;

    private String nome;
    private String email;
}
