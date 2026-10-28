package br.com.virabrequim.database_service.repository;

import br.com.virabrequim.database_service.domain.node.LivroNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

// As queries customizadas devolvem só ids e os métodos default carregam os livros pelo findAllById.
// Assim o livro vem com TODOS os relacionamentos: um livro carregado pela metade apagaria arestas no próximo save.
@Repository
public interface LivroRepository extends Neo4jRepository<LivroNode, Long> {

    @Query("MATCH (l:Livro)-[:STORED_IN]->(e:Estante) WHERE id(e) = $estanteId RETURN id(l)")
    List<Long> findIdsByEstanteId(Long estanteId);

    // Empréstimo ativo = LENT_TO sem data de devolução
    @Query("MATCH (l:Livro)-[e:LENT_TO]->(:Usuario) WHERE e.dataDevolucao IS NULL RETURN DISTINCT id(l)")
    List<Long> findIdsComEmprestimoAtivo();

    @Query("""
            MATCH (l:Livro)-[e:LENT_TO]->(u:Usuario)
            WHERE id(u) = $usuarioId AND e.dataDevolucao IS NULL
            RETURN DISTINCT id(l)""")
    List<Long> findIdsComEmprestimoAtivoPara(Long usuarioId);

    default List<LivroNode> findAllByEstanteId(Long estanteId) {
        return findAllById(findIdsByEstanteId(estanteId));
    }

    default List<LivroNode> findAllComEmprestimoAtivo() {
        return findAllById(findIdsComEmprestimoAtivo());
    }

    default List<LivroNode> findAllComEmprestimoAtivoPara(Long usuarioId) {
        return findAllById(findIdsComEmprestimoAtivoPara(usuarioId));
    }
}
