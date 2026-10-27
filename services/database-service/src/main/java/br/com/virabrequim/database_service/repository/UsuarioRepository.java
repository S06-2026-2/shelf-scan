package br.com.virabrequim.database_service.repository;

import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRepository extends Neo4jRepository<UsuarioNode, Long> {

    // Query derivada do nome do método: MATCH (u:Usuario {email: $email}) RETURN count(u) > 0
    boolean existsByEmail(String email);
}
