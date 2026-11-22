package br.com.virabrequim.database_service.repository;

import br.com.virabrequim.database_service.dto.BibliotecaDoUsuario;
import br.com.virabrequim.database_service.dto.Membro;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// (Usuario)-[:MEMBRO_DE {desde}]->(Biblioteca): o usuário é cliente da biblioteca.
// Fora dos nodes pelo mesmo motivo do AmizadeRepository.
@Repository
public class MembroRepository {

    private final Neo4jClient neo4jClient;

    public MembroRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    // MERGE: quem já é membro continua com a data original
    @Transactional
    public void adicionar(Long usuarioId, Long bibliotecaId, LocalDate desde) {
        neo4jClient.query("""
                        MATCH (u:Usuario), (b:Biblioteca) WHERE id(u) = $usuarioId AND id(b) = $bibliotecaId
                        MERGE (u)-[m:MEMBRO_DE]->(b)
                        ON CREATE SET m.desde = $desde""")
                .bindAll(Map.of("usuarioId", usuarioId, "bibliotecaId", bibliotecaId, "desde", desde))
                .run();
    }

    @Transactional
    public boolean remover(Long usuarioId, Long bibliotecaId) {
        return neo4jClient.query("""
                        MATCH (u:Usuario)-[m:MEMBRO_DE]->(b:Biblioteca)
                        WHERE id(u) = $usuarioId AND id(b) = $bibliotecaId
                        DELETE m""")
                .bindAll(Map.of("usuarioId", usuarioId, "bibliotecaId", bibliotecaId))
                .run().counters().relationshipsDeleted() > 0;
    }

    @Transactional(readOnly = true)
    public List<BibliotecaDoUsuario> listarBibliotecas(Long usuarioId) {
        Collection<BibliotecaDoUsuario> bibliotecas = neo4jClient.query("""
                        MATCH (u:Usuario)-[m:MEMBRO_DE]->(b:Biblioteca) WHERE id(u) = $usuarioId
                        OPTIONAL MATCH (b)-[:FICA_EM]->(c:Cidade)
                        OPTIONAL MATCH (c)-[:PERTENCE_A]->(e:Estado)
                        RETURN id(b) AS id, b.nome AS nome, c.nome AS cidade, e.sigla AS estado, m.desde AS desde
                        ORDER BY toLower(nome), id""")
                .bind(usuarioId).to("usuarioId")
                .fetchAs(BibliotecaDoUsuario.class)
                .mappedBy((tipos, linha) -> new BibliotecaDoUsuario(linha.get("id").asLong(),
                        linha.get("nome").asString(null), linha.get("cidade").asString(null),
                        linha.get("estado").asString(null), linha.get("desde").asLocalDate(null)))
                .all();
        return List.copyOf(bibliotecas);
    }

    @Transactional(readOnly = true)
    public List<Membro> listarMembros(Long bibliotecaId) {
        Collection<Membro> membros = neo4jClient.query("""
                        MATCH (u:Usuario)-[m:MEMBRO_DE]->(b:Biblioteca) WHERE id(b) = $bibliotecaId
                        RETURN id(u) AS id, u.nome AS nome, m.desde AS desde
                        ORDER BY toLower(nome), id""")
                .bind(bibliotecaId).to("bibliotecaId")
                .fetchAs(Membro.class)
                .mappedBy((tipos, linha) -> new Membro(linha.get("id").asLong(),
                        linha.get("nome").asString(null), linha.get("desde").asLocalDate(null)))
                .all();
        return List.copyOf(membros);
    }
}
