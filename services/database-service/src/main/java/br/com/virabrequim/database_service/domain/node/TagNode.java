package br.com.virabrequim.database_service.domain.node;

import br.com.virabrequim.database_service.domain.Textos;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

// O @Id é o nome totalmente normalizado (sem acentos, minúsculas, espaços normalizados): "Ficção Científica" e
// "ficcao  cientifica" são a mesma tag. O nomeExibicao guarda a forma do primeiro cadastro, que é a mostrada na API
// (o LivroService reaproveita a tag do banco para o save não sobrescrever o nomeExibicao).
// Tag sem nenhum livro é apagada (limpeza de órfãos).
@Node("Tag")
@Getter
@ToString
@NoArgsConstructor
public class TagNode {
    @Id
    private String nome;

    private String nomeExibicao;

    public TagNode(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome da tag é obrigatório.");
        }
        this.nome = Textos.chave(nome);
        this.nomeExibicao = Textos.espacosNormalizados(nome);
    }

    // Tags gravadas antes do nomeExibicao existir só têm o nome normalizado
    public String getNomeExibicao() {
        return nomeExibicao != null ? nomeExibicao : nome;
    }
}
