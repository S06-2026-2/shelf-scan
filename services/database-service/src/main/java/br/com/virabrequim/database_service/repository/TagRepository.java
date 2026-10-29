package br.com.virabrequim.database_service.repository;

import br.com.virabrequim.database_service.domain.node.TagNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

// O id é o nome normalizado da tag
@Repository
public interface TagRepository extends Neo4jRepository<TagNode, String> {

    // Apaga, entre as tags informadas, as que ficaram sem nenhum livro (HAS_TAG)
    @Transactional
    @Query("MATCH (t:Tag) WHERE t.nome IN $nomes AND NOT ()-[:HAS_TAG]->(t) DETACH DELETE t")
    void apagarOrfas(Collection<String> nomes);
}
