package br.com.virabrequim.database_service.repository;

import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends Neo4jRepository<UsuarioNode, Long> {

    // Query derivada do nome do métódo: MATCH (u:Usuario {email: $email}) RETURN count(u) > 0
    boolean existsByEmail(String email);

    // O dono é derivado do caminho Livro -> Estante <- Usuario
    @Query("MATCH (u:Usuario)-[:HAS]->(:Estante)<-[:STORED_IN]-(l:Livro) WHERE id(l) = $livroId RETURN id(u)")
    Optional<Long> findIdDoDonoDoLivro(Long livroId);

    // A query customizada devolve só o id; o findById carrega o usuário com todos os relacionamentos
    default Optional<UsuarioNode> findDonoDoLivro(Long livroId) {
        return findIdDoDonoDoLivro(livroId).flatMap(this::findById);
    }
}
