package br.com.virabrequim.database_service.domain.node;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Livro")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagNode {
    @Id
    private String id;

}
