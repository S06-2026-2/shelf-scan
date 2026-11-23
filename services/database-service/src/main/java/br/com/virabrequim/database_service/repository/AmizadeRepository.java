package br.com.virabrequim.database_service.repository;

import br.com.virabrequim.database_service.dto.Amigo;
import br.com.virabrequim.database_service.dto.SolicitacaoAmizade;
import org.neo4j.driver.Record;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// (Usuario)-[:SOLICITOU_AMIZADE {em}]->(Usuario)   pedido pendente: a direção diz quem pediu
// (Usuario)-[:AMIGO {desde}]-(Usuario)             amizade: gravada num sentido, consultada sem sentido
//
// Os dois relacionamentos NÃO são campos do UsuarioNode de propósito. O livro aponta para o mutuário (LENT_TO), e o
// SDN carrega tudo que está mapeado: com uma lista de amigos no node, abrir um livro emprestado traria os amigos do
// mutuário, os amigos dos amigos e assim por diante. Como não são mapeados, o save do UsuarioNode também não os apaga.
@Repository
public class AmizadeRepository {

    private final Neo4jClient neo4jClient;

    public AmizadeRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Transactional(readOnly = true)
    public boolean saoAmigos(Long usuarioId, Long outroId) {
        return existe("""
                MATCH (a:Usuario), (b:Usuario) WHERE id(a) = $usuarioId AND id(b) = $outroId
                RETURN EXISTS { (a)-[:AMIGO]-(b) }""", usuarioId, outroId);
    }

    @Transactional(readOnly = true)
    public boolean existeSolicitacao(Long solicitanteId, Long destinatarioId) {
        return existe("""
                MATCH (a:Usuario), (b:Usuario) WHERE id(a) = $usuarioId AND id(b) = $outroId
                RETURN EXISTS { (a)-[:SOLICITOU_AMIZADE]->(b) }""", solicitanteId, destinatarioId);
    }

    @Transactional
    public void criarSolicitacao(Long solicitanteId, Long destinatarioId, LocalDate em) {
        neo4jClient.query("""
                        MATCH (a:Usuario), (b:Usuario) WHERE id(a) = $usuarioId AND id(b) = $outroId
                        MERGE (a)-[s:SOLICITOU_AMIZADE]->(b)
                        ON CREATE SET s.em = $em""")
                .bindAll(Map.of("usuarioId", solicitanteId, "outroId", destinatarioId, "em", em))
                .run();
    }

    // Troca o pedido pela amizade numa query só (atômica). Apaga os pedidos dos DOIS sentidos: se as duas pessoas
    // pedirem ao mesmo tempo, podem existir dois. Sem pedido nenhum, não cria nada e devolve false.
    @Transactional
    public boolean trocarSolicitacaoPorAmizade(Long usuarioId, Long outroId, LocalDate desde) {
        return neo4jClient.query("""
                        MATCH (a:Usuario)-[s:SOLICITOU_AMIZADE]-(b:Usuario)
                        WHERE id(a) = $usuarioId AND id(b) = $outroId
                        WITH a, b, collect(s) AS pedidos
                        FOREACH (pedido IN pedidos | DELETE pedido)
                        MERGE (a)-[amizade:AMIGO]-(b)
                        ON CREATE SET amizade.desde = $desde
                        RETURN true""")
                .bindAll(Map.of("usuarioId", usuarioId, "outroId", outroId, "desde", desde))
                .fetchAs(Boolean.class).one().orElse(false);
    }

    @Transactional
    public boolean apagarSolicitacao(Long usuarioId, Long outroId) {
        return apagar("""
                MATCH (a:Usuario)-[r:SOLICITOU_AMIZADE]-(b:Usuario) WHERE id(a) = $usuarioId AND id(b) = $outroId
                DELETE r""", usuarioId, outroId);
    }

    @Transactional
    public boolean apagarAmizade(Long usuarioId, Long outroId) {
        return apagar("""
                MATCH (a:Usuario)-[r:AMIGO]-(b:Usuario) WHERE id(a) = $usuarioId AND id(b) = $outroId
                DELETE r""", usuarioId, outroId);
    }

    @Transactional(readOnly = true)
    public List<Amigo> listarAmigos(Long usuarioId) {
        Collection<Amigo> amigos = neo4jClient.query("""
                        MATCH (u:Usuario)-[r:AMIGO]-(p:Usuario) WHERE id(u) = $usuarioId
                        RETURN id(p) AS id, p.nome AS nome, r.desde AS data,
                               COUNT { (u)-[:AMIGO]-(:Usuario)-[:AMIGO]-(p) } AS emComum
                        ORDER BY toLower(nome), id""")
                .bind(usuarioId).to("usuarioId")
                .fetchAs(Amigo.class)
                .mappedBy((tipos, linha) -> new Amigo(linha.get("id").asLong(), linha.get("nome").asString(null),
                        data(linha), linha.get("emComum").asInt()))
                .all();
        return List.copyOf(amigos);
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoAmizade> listarRecebidas(Long usuarioId) {
        return solicitacoes("MATCH (u:Usuario)<-[r:SOLICITOU_AMIZADE]-(p:Usuario)", usuarioId);
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoAmizade> listarEnviadas(Long usuarioId) {
        return solicitacoes("MATCH (u:Usuario)-[r:SOLICITOU_AMIZADE]->(p:Usuario)", usuarioId);
    }

    private List<SolicitacaoAmizade> solicitacoes(String padrao, Long usuarioId) {
        Collection<SolicitacaoAmizade> solicitacoes = neo4jClient.query(padrao + """
                         WHERE id(u) = $usuarioId
                        RETURN id(p) AS id, p.nome AS nome, r.em AS data,
                               COUNT { (u)-[:AMIGO]-(:Usuario)-[:AMIGO]-(p) } AS emComum
                        ORDER BY data DESC, toLower(nome), id""")
                .bind(usuarioId).to("usuarioId")
                .fetchAs(SolicitacaoAmizade.class)
                .mappedBy((tipos, linha) -> new SolicitacaoAmizade(linha.get("id").asLong(),
                        linha.get("nome").asString(null), data(linha), linha.get("emComum").asInt()))
                .all();
        return List.copyOf(solicitacoes);
    }

    private boolean existe(String cypher, Long usuarioId, Long outroId) {
        return neo4jClient.query(cypher)
                .bindAll(Map.of("usuarioId", usuarioId, "outroId", outroId))
                .fetchAs(Boolean.class).one().orElse(false);
    }

    private boolean apagar(String cypher, Long usuarioId, Long outroId) {
        return neo4jClient.query(cypher)
                .bindAll(Map.of("usuarioId", usuarioId, "outroId", outroId))
                .run().counters().relationshipsDeleted() > 0;
    }

    private static LocalDate data(Record linha) {
        return linha.get("data").asLocalDate(null);
    }
}
