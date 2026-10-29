package br.com.virabrequim.database_service.domain.node;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class UsuarioNode {
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

    public UsuarioNode(String nome, String email){
        this.nome = nome;
        this.email = email.toLowerCase();
    }

    public void setEstantePadrao(EstanteNode estantePadrao) {
        this.estantePadrao = estantePadrao;
    }
}
