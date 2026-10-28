package br.com.virabrequim.database_service.repository;

import br.com.virabrequim.database_service.domain.node.EstanteNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EstanteRepository extends Neo4jRepository<EstanteNode, Long> {
}
