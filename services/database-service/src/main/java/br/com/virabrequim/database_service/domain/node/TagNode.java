package br.com.virabrequim.database_service.domain.node;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Tag")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TagNode {
    @Id
    private String nome;

}
