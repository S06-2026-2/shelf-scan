package br.com.virabrequim.database_service.domain.node;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Node("Livro")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LivroNode {
    @Id
    @GeneratedValue
    private long id;

    private String isbn;
    // private Foto capa;
    // private Foto lombada;
    // private Foto contraCapa;
    private String nome;
    private String volume; // possivelmente float
    private int paginas;
    private ZonedDateTime dataDeAdicao;
    private String sinopse;

    @Relationship(type = "HAS_TAG", direction = Relationship.Direction.OUTGOING)
    private Set<TagNode> tags = new HashSet<>();

    @Relationship(type = "WRITTEN_BY", direction = Relationship.Direction.OUTGOING)
    private List<AutorNode> autores;

    @Relationship(type = "STORED_IN", direction = Relationship.Direction.OUTGOING)
    private EstanteNode estante;

    private boolean emprestado;
    @Relationship(type = "LENT_TO", direction = Relationship.Direction.OUTGOING)
    private Set<UsuarioNode> emprestimosAtuais;
    //TODO: Transformar em relacionamentos mais complexos, como este que será adicionado uma
    //TODO: propriedade para o relacionamento, como: satus(em andamento, terminado, datas, e possivelmente mais)
    @Relationship(type = "WAS_LENT_TO", direction = Relationship.Direction.OUTGOING)
    private List<EmprestimoNode> emprestimos;
}
