package br.com.virabrequim.database_service.agente;

import br.com.virabrequim.database_service.domain.Textos;
import br.com.virabrequim.database_service.domain.node.AutorNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Agente de IA que enriquece um livro: a partir do título, autores, volume e (opcionalmente) do texto bruto do OCR,
// pede ao LLM sinopse, ISBN, número de páginas, autores e gêneros (tags).
//
// O LLM pode errar ou inventar (alucinação), então a resposta passa por travas antes de virar sugestão:
// - o modelo diz se reconheceu o livro (livroIdentificado); se não reconheceu, nada é sugerido
// - ISBN só com dígito verificador válido (ISBN-10 ou ISBN-13)
// - páginas entre 1 e 10.000; sinopse com tamanho máximo; no máximo 5 autores e 5 tags
// O agente só SUGERE: quem decide o que grava é o EnriquecimentoService (só campos vazios).
@Component
public class AgenteEnriquecedor {

    static final int MAX_SINOPSE = 1500;
    static final int MAX_PAGINAS = 10_000;
    static final int MAX_ITENS = 5;
    static final int MAX_TEXTO_BRUTO = 2000;
    static final int MAX_NOME_AUTOR = 120;
    static final int MAX_NOME_TAG = 40;

    private static final String PROMPT = """
            Você é um bibliotecário que completa o cadastro de livros de um acervo pessoal brasileiro.
            Recebe o que o usuário já cadastrou (e, às vezes, o texto que um OCR leu da capa, com ruído).

            Identifique a obra e devolva:
            - livroIdentificado: true só se você reconhece com segurança esta obra específica; senão false.
            - sinopse: em português do Brasil, 2 a 4 frases, sem spoilers do final.
            - isbn: ISBN-13 de uma edição brasileira, só se tiver certeza; senão null. Nunca invente um ISBN.
            - paginas: número de páginas de uma edição conhecida, só se tiver certeza; senão null.
            - autores: nomes completos de quem escreveu (e, em mangás e HQs, de quem desenhou). Sem tradutores.
            - tags: de 1 a 5 gêneros literários curtos em português, em minúsculas (ex.: "romance", "ficção científica").
            Use null (ou lista vazia) em tudo que não souber. Não invente.

            Livro:
            """;

    // Schema da saída estruturada (subconjunto do OpenAPI aceito pelo Gemini)
    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "livroIdentificado", Map.of("type", "BOOLEAN"),
                    "sinopse", Map.of("type", "STRING", "nullable", true),
                    "isbn", Map.of("type", "STRING", "nullable", true),
                    "paginas", Map.of("type", "INTEGER", "nullable", true),
                    "autores", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                    "tags", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"))),
            "required", List.of("livroIdentificado", "sinopse", "isbn", "paginas", "autores", "tags"),
            "propertyOrdering", List.of("livroIdentificado", "sinopse", "isbn", "paginas", "autores", "tags"));

    private final ClienteLlm clienteLlm;
    private final JsonMapper jsonMapper;

    public AgenteEnriquecedor(ClienteLlm clienteLlm, JsonMapper jsonMapper) {
        this.clienteLlm = clienteLlm;
        this.jsonMapper = jsonMapper;
    }

    public SugestaoEnriquecimento sugerir(LivroNode livro, String textoBruto) {
        String json = clienteLlm.gerarJson(PROMPT + descricao(livro, textoBruto), SCHEMA);

        JsonNode resposta;
        try {
            resposta = jsonMapper.readTree(json);
        } catch (JacksonException ex) {
            throw new AgenteIndisponivelException("O agente devolveu um JSON inválido.", ex);
        }

        if (!resposta.path("livroIdentificado").asBoolean(false)) {
            return SugestaoEnriquecimento.vazia();
        }
        return new SugestaoEnriquecimento(
                true,
                sinopse(resposta.path("sinopse")),
                Isbn.normalizado(texto(resposta.path("isbn"))),
                paginas(resposta.path("paginas")),
                lista(resposta.path("autores"), MAX_NOME_AUTOR),
                lista(resposta.path("tags"), MAX_NOME_TAG));
    }

    public String modelo() {
        return clienteLlm.modelo();
    }

    // Dados do livro como JSON no prompt: separa claramente o que é dado do que é instrução
    private String descricao(LivroNode livro, String textoBruto) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("titulo", livro.getTitulo());
        dados.put("autores", livro.getAutores() == null ? List.of()
                : livro.getAutores().stream().map(AutorNode::getNome).toList());
        dados.put("volume", livro.getVolume());
        dados.put("isbn", livro.getIsbn());
        String bruto = Textos.opcional(textoBruto);
        if (bruto != null) {
            dados.put("textoDoOcr", bruto.length() > MAX_TEXTO_BRUTO ? bruto.substring(0, MAX_TEXTO_BRUTO) : bruto);
        }
        return jsonMapper.writeValueAsString(dados);
    }

    private static String texto(JsonNode no) {
        return no.isString() ? Textos.opcional(no.asString()) : null;
    }

    private static String sinopse(JsonNode no) {
        String sinopse = texto(no);
        return sinopse == null || sinopse.length() > MAX_SINOPSE ? null : sinopse;
    }

    private static Integer paginas(JsonNode no) {
        if (!no.isIntegralNumber()) {
            return null;
        }
        int paginas = no.asInt();
        return paginas >= 1 && paginas <= MAX_PAGINAS ? paginas : null;
    }

    // Sem itens vazios, longos demais ou repetidos (comparando como o banco compara: sem acento e sem caixa)
    private static List<String> lista(JsonNode no, int tamanhoMaximo) {
        List<String> itens = new ArrayList<>();
        List<String> chaves = new ArrayList<>();
        for (JsonNode item : no) {
            String valor = texto(item);
            if (valor == null || valor.length() > tamanhoMaximo || chaves.contains(Textos.chave(valor))) {
                continue;
            }
            itens.add(Textos.espacosNormalizados(valor));
            chaves.add(Textos.chave(valor));
            if (itens.size() == MAX_ITENS) {
                break;
            }
        }
        return List.copyOf(itens);
    }
}
