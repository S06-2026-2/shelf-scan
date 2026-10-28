package br.com.virabrequim.database_service.domain.relationship;

import br.com.virabrequim.database_service.domain.StatusLeitura;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@RelationshipProperties
@Getter
@Setter
@ToString
public class EmprestimoRelationship {
    @Id
    @GeneratedValue
    private Long id;

    private LocalDate dataEmprestimo;
    private LocalDate dataPrevistaDevolucao;
    private LocalDate dataDevolucao;

    @TargetNode
    @ToString.Exclude
    private UsuarioNode usuario;

    // Leitura de quem pegou o livro emprestado (spec R15–R20)
    private StatusLeitura statusLeitura;
    private Integer paginaAtual;
    private LocalDate dataConclusaoLeitura;

    public boolean isEmprestado(){
        if(this.dataDevolucao == null) return true;
        return false;
    }

    public boolean isDevolvido(){
        return !isEmprestado();
    }

    public int diasParaVencimento(LocalDate hoje){
        long dias = ChronoUnit.DAYS.between(hoje, this.dataPrevistaDevolucao);
        int diasInt = Math.toIntExact(dias);
        return diasInt;
    }

    public boolean isVencido(LocalDate hoje){
        if(isEmprestado() && diasParaVencimento(hoje) < 0) return true;
        return false;
    }

    public boolean estaVencendo(LocalDate hoje){
        if(this.isDevolvido()) throw new IllegalStateException("Emprestimo já está encerrado");
        if(this.isVencido(hoje)) return false;

        double PORCENTAGEM_PARA_VENCENDO = 0.15D;
        int DIAS_PARA_VENCIMENTO = 3;

        int tempoMaximoDeEmprestimo = Math.toIntExact(
                ChronoUnit.DAYS.between(this.dataEmprestimo, this.dataPrevistaDevolucao)
        );

        int diasParaVencimento = diasParaVencimento(hoje);

        double porcentagemTempoRestante = (double) diasParaVencimento/tempoMaximoDeEmprestimo;

        if(diasParaVencimento <= DIAS_PARA_VENCIMENTO || porcentagemTempoRestante <= PORCENTAGEM_PARA_VENCENDO){
            return true;
        }
        return false;
    }

    // Calculada, não é campo guardado; a relação não acessa o livro, por isso recebe o total de páginas
    public Integer porcentagemLeitura(Integer totalPaginas) {
        //TODO: criar (R21)
        throw new UnsupportedOperationException();
    }
}
