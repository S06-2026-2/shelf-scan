package br.com.virabrequim.database_service.domain.node;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.springframework.data.neo4j.core.schema.Relationship.Direction.OUTGOING;

@Node("Usuario")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class UsuarioNode {

    // Mudança: nome da estante padrão em um lugar só, igual para usuário comum e biblioteca
    public static final String NOME_ESTANTE_PADRAO = "Minha estante";

    @Id
    @GeneratedValue
    private Long id;

    private String nome;
    private String email;

    // Todas as estantes do usuário, inclusive a padrão
    @Relationship(type = "HAS", direction = OUTGOING)
    @ToString.Exclude
    private List<EstanteNode> estantes = new ArrayList<>();

    // Marca qual das estantes é a padrão (a mesma estante também está em `estantes`)
    @Relationship(type = "ESTANTE_PADRAO", direction = OUTGOING)
    @ToString.Exclude
    private EstanteNode estantePadrao;

    public UsuarioNode(String nome, String email) {
        setNome(nome);
        setEmail(email);

        EstanteNode padrao = new EstanteNode(NOME_ESTANTE_PADRAO);
        this.estantes.add(padrao);
        this.estantePadrao = padrao;
    }

    public void setNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome é obrigatório.");
        }
        this.nome = nome.strip();
    }

    public void setEmail(String email) {
        this.email = normalizarEmail(email);
    }

    public static String normalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("O email é obrigatório.");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
