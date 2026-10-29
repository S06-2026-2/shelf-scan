package br.com.virabrequim.database_service.domain.node;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import static org.springframework.data.neo4j.core.schema.Relationship.Direction.OUTGOING;

// Biblioteca é um usuário: no grafo o nó recebe os labels Biblioteca e Usuario (herdado de UsuarioNode)
// Mudança: campos próprios da biblioteca (endereço e horário). Ficam no mesmo nó do usuário; só a cidade vira
// relacionamento: (Biblioteca)-[:FICA_EM]->(Cidade)-[:PERTENCE_A]->(Estado)
@Node("Biblioteca")
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
public class BibliotecaNode extends UsuarioNode {

    // Formato "HH:mm" (00:00 a 23:59). Provisório: String até decidir migrar para LocalTime
    private static final String FORMATO_HORARIO = "([01]\\d|2[0-3]):[0-5]\\d";

    private String logradouro;
    private String numero;
    private String cep;

    private String horarioAbertura;
    private String horarioFechamento;

    @Relationship(type = "FICA_EM", direction = OUTGOING)
    @ToString.Exclude
    private CidadeNode cidade;

    // Nome, email e estante padrão: regras herdadas do UsuarioNode
    public BibliotecaNode(String nome, String email) {
        super(nome, email);
    }

    // Mudança: setters próprios validam o formato; null = horário não informado
    public void setHorarioAbertura(String horarioAbertura) {
        this.horarioAbertura = validarHorario(horarioAbertura, "abertura");
    }

    public void setHorarioFechamento(String horarioFechamento) {
        this.horarioFechamento = validarHorario(horarioFechamento, "fechamento");
    }

    private static String validarHorario(String horario, String qual) {
        if (horario == null) {
            return null;
        }
        String limpo = horario.strip();
        if (!limpo.matches(FORMATO_HORARIO)) {
            throw new IllegalArgumentException("O horário de " + qual + " deve estar no formato HH:mm.");
        }
        return limpo;
    }
}
