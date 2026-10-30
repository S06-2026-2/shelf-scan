package br.com.virabrequim.database_service.domain.node;

import br.com.virabrequim.database_service.domain.Textos;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDate;

// O autor é reaproveitado entre livros pelo nome (find-or-create no LivroService): o nome é guardado com os espaços
// normalizados e comparado sem diferenciar maiúsculas de minúsculas (acentos contam).
// TODO: autores diferentes com o mesmo nome (homônimos) viram o mesmo nó. Tratar quando houver como diferenciá-los
//  (ex.: datas, nacionalidade ou um id externo vindo do agente de enriquecimento).
// TODO: autor sem nenhum livro é apagado (limpeza de órfãos). Hoje ele só tem nome; se ganhar dados próprios
//  (nacionalidade, datas), esses dados também serão apagados quando o último livro dele sair. Rever a regra então.
@Node("Autor")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AutorNode {
    @Id
    @GeneratedValue
    private Long id;

    private String nome;
    private String nacionalidade;
    private LocalDate dataDeNascimento;
    private LocalDate dataDeFalecimento;

    public AutorNode(String nome) {
        setNome(nome);
    }

    public void setNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do autor é obrigatório.");
        }
        this.nome = Textos.espacosNormalizados(nome);
    }
}
