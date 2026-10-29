package br.com.virabrequim.database_service.domain.node;

import br.com.virabrequim.database_service.domain.RegrasLeitura;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.springframework.data.neo4j.core.schema.Relationship.Direction.OUTGOING;

@Node("Livro")
@Getter
@Setter
@ToString
// Construtor vazio sem validação: usado pelo SDN ao carregar do banco e pelos testes
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
    private List<AutorNode> autores = new ArrayList<>();

    @ToString.Exclude
    @Relationship(type = "STORED_IN", direction = OUTGOING)
    private EstanteNode estante;

    @ToString.Exclude
    @Relationship(type = "LENT_TO", direction = OUTGOING)
    private List<EmprestimoRelationship> emprestimos = new ArrayList<>();

    // Mesmo padrão do UsuarioNode e do EstanteNode: o livro já nasce com título válido
    public LivroNode(String titulo) {
        setTitulo(titulo);
    }

    public void setTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("O título é obrigatório.");
        }
        this.titulo = titulo.strip();
    }

    // 0 ou null = sem total de páginas (guardado como null). Negativo é inválido.
    // A leitura do dono e a do empréstimo ativo acompanham o novo total: LIDO passa a ter página atual = novo total,
    // e LENDO com página atual maior que o novo total é conflito (IllegalStateException).
    // Tudo é verificado antes de alterar: se der erro, nada muda.
    // Os empréstimos devolvidos (histórico) não são tocados.
    public void setPaginas(Integer paginas) {
        if (paginas != null && paginas < 0) {
            throw new IllegalArgumentException("O número de páginas não pode ser negativo.");
        }
        Integer novoTotal = paginas == null || paginas == 0 ? null : paginas;

        Integer novaPaginaDoDono = RegrasLeitura.paginaComNovoTotal(statusLeitura, paginaAtual, novoTotal, "do dono");
        Optional<EmprestimoRelationship> ativo = emprestimoAtivo();
        Integer novaPaginaDoEmprestimo = ativo
                .map(emprestimo -> RegrasLeitura.paginaComNovoTotal(emprestimo.getStatusLeitura(),
                        emprestimo.getPaginaAtual(), novoTotal, "de quem está com o livro"))
                .orElse(null);

        this.paginas = novoTotal;
        this.paginaAtual = novaPaginaDoDono;
        ativo.ifPresent(emprestimo -> emprestimo.setPaginaAtual(novaPaginaDoEmprestimo));
    }

    // O empréstimo da lista que ainda não foi devolvido
    public Optional<EmprestimoRelationship> emprestimoAtivo() {
        if (emprestimos == null) {
            return Optional.empty();
        }
        List<EmprestimoRelationship> ativos = emprestimos.stream()
                .filter(EmprestimoRelationship::isEmprestado)
                .toList();
        if (ativos.size() > 1) {
            throw new IllegalStateException("Livro com mais de um empréstimo ativo");
        }
        return ativos.stream().findFirst();
    }

    public boolean isEmprestado() {
        return emprestimoAtivo().isPresent();
    }

    // Leitura do dono. Status é obrigatório; em LENDO, página inválida vira 0 (RegrasLeitura.paginaLendo)
    public void atualizarLeitura(StatusLeitura status, Integer paginaAtual, LocalDate hoje) {
        if (status == null) {
            throw new IllegalArgumentException("O status da leitura é obrigatório.");
        }
        switch (status) {
            case QUERO_LER -> {
                this.paginaAtual = 0;
                this.dataConclusaoLeitura = null;
            }
            case LENDO -> {
                this.paginaAtual = RegrasLeitura.paginaLendo(paginaAtual, paginas);
                this.dataConclusaoLeitura = null;
            }
            case LIDO -> {
                this.paginaAtual = paginas;
                this.dataConclusaoLeitura = hoje;
            }
        }
        this.statusLeitura = status;
    }

    // Calculada, não é campo guardado
    public Integer getPorcentagemLeitura() {
        return RegrasLeitura.porcentagem(statusLeitura, paginaAtual, paginas);
    }
}
