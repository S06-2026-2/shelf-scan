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
    // - Autores e tags dos livros apagados que ficarem sem nenhum livro também são apagados. Cidade e estado ficam.
    // - DETACH DELETE remove junto os relacionamentos, inclusive os LENT_TO de livros de outros que apontam para ele.
    // TODO (PERDA DE DADOS): apagar um usuário apaga TODOS os LENT_TO ligados a ele, e com eles o histórico de
    //  empréstimos e as leituras guardadas nesses empréstimos:
    //  1. Empréstimo ATIVO de um livro de outra pessoa para ele: o livro deixa de constar como emprestado para o dono.
    //  2. Empréstimos DEVOLVIDOS (dele ou para ele): o histórico e a leitura de quem pegou o livro somem.
    //  3. Livros dele que estavam emprestados não são apagados, mas ficam sem estante e sem dono.
    //  Decidir: bloquear a deleção (409), registrar a devolução antes de apagar, ou manter os empréstimos apontando
    //  para um usuário "removido" (soft delete).
    // @Transactional: os métodos de query dos repositories rodam em transação somente leitura por padrão, e esta
    // escreve.
    @Transactional
    @Query("""
            MATCH (u:Usuario) WHERE id(u) = $usuarioId
            OPTIONAL MATCH (u)-[:HAS|ESTANTE_PADRAO]->(e:Estante)
            OPTIONAL MATCH (e)<-[:STORED_IN]-(l:Livro)
              WHERE NOT EXISTS { MATCH (l)-[emp:LENT_TO]->() WHERE emp.dataDevolucao IS NULL }
            OPTIONAL MATCH (l)-[:WRITTEN_BY]->(a:Autor)
            OPTIONAL MATCH (l)-[:HAS_TAG]->(t:Tag)
            WITH u, collect(DISTINCT e) AS estantes, collect(DISTINCT l) AS livros,
                 collect(DISTINCT a) AS autores, collect(DISTINCT t) AS tags
            FOREACH (livro IN livros | DETACH DELETE livro)
            FOREACH (estante IN estantes | DETACH DELETE estante)
            DETACH DELETE u
            WITH autores, tags
            FOREACH (autor IN [x IN autores WHERE NOT (x)<-[:WRITTEN_BY]-()] | DELETE autor)
            FOREACH (tag IN [x IN tags WHERE NOT ()-[:HAS_TAG]->(x)] | DELETE tag)
            """)
    void apagarComEstantesELivros(Long usuarioId);
}
