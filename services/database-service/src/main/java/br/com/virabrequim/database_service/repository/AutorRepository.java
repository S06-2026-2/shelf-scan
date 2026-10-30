package br.com.virabrequim.database_service.repository;

import br.com.virabrequim.database_service.domain.node.AutorNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface AutorRepository extends Neo4jRepository<AutorNode, Long> {

    // Query derivada: compara com toLower nos dois lados. findFirst porque podem existir repetidos de antes da regra
    Optional<AutorNode> findFirstByNomeIgnoreCase(String nome);

    // Apaga, entre os autores informados, os que ficaram sem nenhum livro (WRITTEN_BY)
    @Transactional
    @Query("MATCH (a:Autor) WHERE id(a) IN $ids AND NOT (a)<-[:WRITTEN_BY]-() DETACH DELETE a")
    void apagarOrfaos(Collection<Long> ids);
}
