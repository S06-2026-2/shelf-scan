package br.com.virabrequim.database_service.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

// Mudança: classe nova. Cria as constraints do Neo4j quando a aplicação sobe.
// O existsByEmail dos services dá uma mensagem amigável, mas não garante nada sozinho: entre ele e o save, outra
// requisição pode salvar o mesmo email. A constraint é a garantia de verdade, feita pelo próprio banco.
// Como o email é salvo normalizado (minúsculas), "Ana@x.com" e "ana@x.com" também colidem.
// IF NOT EXISTS deixa rodar a cada inicialização sem erro. Se o banco já tiver emails repetidos, a criação falha e
// a aplicação não sobe (é preciso limpar os repetidos antes).
@Component
public class RestricoesNeo4j implements ApplicationRunner {

    private final Neo4jClient neo4jClient;

    public RestricoesNeo4j(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Vale para usuário comum e biblioteca, porque os dois têm o label Usuario
        neo4jClient.query("CREATE CONSTRAINT usuario_email_unico IF NOT EXISTS "
                + "FOR (u:Usuario) REQUIRE u.email IS UNIQUE").run();
    }
}
