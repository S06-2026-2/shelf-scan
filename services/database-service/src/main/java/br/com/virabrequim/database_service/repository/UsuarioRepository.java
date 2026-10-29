package br.com.virabrequim.database_service.repository;

import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    // Apaga o usuário (comum ou biblioteca), as estantes dele e os livros guardados nelas, numa query só (atômica).
    // - Livro com empréstimo ativo (LENT_TO sem dataDevolucao) NÃO é apagado: perde só a estante e fica sem dono.
    // - Autores, tags, cidade e estado são compartilhados com outros nós e não são apagados.
    // - DETACH DELETE remove junto os relacionamentos, inclusive os LENT_TO de livros de outros que apontam para ele.
    // TODO: se o usuário apagado estava com o livro de outra pessoa, o LENT_TO ativo some e o livro deixa de constar
    //  como emprestado para o dono (e o histórico desse empréstimo se perde). Decidir: bloquear a deleção (409),
    //  registrar a devolução antes de apagar, ou manter o empréstimo apontando para um usuário "removido".
    // @Transactional: os métodos de query dos repositories rodam em transação somente leitura por padrão, e esta
    // escreve.
    @Transactional
    @Query("""
            MATCH (u:Usuario) WHERE id(u) = $usuarioId
            OPTIONAL MATCH (u)-[:HAS|ESTANTE_PADRAO]->(e:Estante)
            OPTIONAL MATCH (e)<-[:STORED_IN]-(l:Livro)
              WHERE NOT EXISTS { MATCH (l)-[emp:LENT_TO]->() WHERE emp.dataDevolucao IS NULL }
            WITH u, collect(DISTINCT e) AS estantes, collect(DISTINCT l) AS livros
            FOREACH (livro IN livros | DETACH DELETE livro)
            FOREACH (estante IN estantes | DETACH DELETE estante)
            DETACH DELETE u
            """)
    void apagarComEstantesELivros(Long usuarioId);
}
