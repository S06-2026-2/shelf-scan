package br.com.virabrequim.database_service.domain.node;

import br.com.virabrequim.database_service.domain.StatusLeitura;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.springframework.data.neo4j.core.schema.Relationship.Direction.OUTGOING;

@Node("Livro")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class LivroNode {
    @Id
    @GeneratedValue
    private Long id;

    private String isbn;
    // private Foto capa;
    // private Foto lombada;
    // private Foto contraCapa;
    private String titulo;
    private String volume; // possivelmente float
    private Integer paginas; // null = não informado
    private ZonedDateTime dataDeAdicao;
    private String sinopse;

    // Leitura do dono (spec LivroNode): sem setter público, só muda por atualizarLeitura
    @Setter(AccessLevel.NONE)
    private StatusLeitura statusLeitura;
    @Setter(AccessLevel.NONE)
    private Integer paginaAtual;
    @Setter(AccessLevel.NONE)
    private LocalDate dataConclusaoLeitura;

    @ToString.Exclude
    @Relationship(type = "HAS_TAG", direction = OUTGOING)
    private Set<TagNode> tags = new HashSet<>();

    @ToString.Exclude
    @Relationship(type = "WRITTEN_BY", direction = OUTGOING)
    private List<AutorNode> autores;

    @ToString.Exclude
    @Relationship(type = "STORED_IN", direction = OUTGOING)
    private EstanteNode estante;

    @ToString.Exclude
    @Relationship(type = "LENT_TO", direction = OUTGOING)
    private List<EmprestimoRelationship> emprestimos;

    public Optional<EmprestimoRelationship> emprestimoAtivo() {
        //TODO: criar (N2, N3)
        throw new UnsupportedOperationException();
    }

    public boolean isEmprestado() {
        //TODO: criar (N4)
        throw new UnsupportedOperationException();
    }

    public void atualizarLeitura(StatusLeitura status, Integer paginaAtual, LocalDate hoje) {
        //TODO: criar (N5–N9)
        throw new UnsupportedOperationException();
    }

    // Calculada, não é campo guardado
    public Integer getPorcentagemLeitura() {
        //TODO: criar (N10)
        throw new UnsupportedOperationException();
    }
}
