classe: br.com.virabrequim.database_service.agente.AgenteEnriquecedor

# Spec: AgenteEnriquecedor

## Contexto

Agente de IA que **completa** um livro já identificado (pelo OCR e pelo usuário): ele não decide qual é o livro. O LLM
recebe ferramentas para consultar catálogos, escolhe uma edição e conclui com um JSON de formato fixo. O código então
**confere** a edição e só aproveita os dados que vieram do catálogo. O agente não grava nada: quem decide o que entra no
livro é o `EnriquecimentoService`.

Nos testes, o `ClienteLlm` é um falso (que pode chamar as ferramentas recebidas), o `CatalogoLivros` é um mock e o
`JsonMapper` é real.

JSON da conclusão: `{"isbn": "...", "conheceObra": true, "sinopse": "...", "autores": ["..."], "tags": ["..."]}`

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `AgenteEnriquecedor(ClienteLlm clienteLlm, CatalogoLivros catalogo, JsonMapper jsonMapper)` | Injeção | — |
| `SugestaoEnriquecimento sugerir(LivroNode livro, String textoBruto)` | Sugestão já conferida | A1–A13 |
| `String modelo()` | Nome do modelo usado | A14 |

| Tipo | Uso |
| --- | --- |
| `ClienteLlm.gerarJson(String prompt, Map<String, Object> schema, List<Ferramenta> ferramentas)` | JSON da conclusão |
| `record Ferramenta(String nome, String descricao, Map<String, Object> parametros, Function<Map<String, Object>, Map<String, Object>> executar)` | Função que o LLM chama |
| `CatalogoLivros.buscaPorTituloLigada()` | `boolean`: há chave do Google Books |
| `CatalogoLivros.buscar(String titulo, String autor, String editora)` | `List<Edicao>` (Google Books) |
| `CatalogoLivros.porIsbn(String isbn)` | `Optional<Edicao>` (BrasilAPI) |
| `CatalogoIndisponivelException` | O catálogo não respondeu |
| `record SugestaoEnriquecimento(boolean encontradoNoCatalogo, boolean edicaoDigital, String sinopse, String isbn, Integer paginas, String editora, List<String> autores, Fonte fonteAutores, List<String> tags, List<String> consultas)` | Resultado |

`SugestaoEnriquecimento.vazia(consultas)`: `false` nos dois `boolean`, `null`s, listas vazias e `fonteAutores` = `IA`. `Fonte` é `CATALOGO` ou
`IA`. A conferência de uma edição com o livro é da `ConferenciaEdicao`.

## Regras

**Prompt e ferramentas**

- **A1.** `gerarJson` é chamado **uma vez**. O prompt contém título, autores, volume, editora e ISBN do livro e, se
  `textoBruto` não for `null` nem em branco, o texto sem espaços nas pontas, cortado em **2000** caracteres.
- **A2.** Ferramentas: `buscarEdicoes` (argumentos `titulo` e, opcionais, `autor` e `editora`; só se `buscaPorTituloLigada()`) e
  `consultarIsbn` (argumento `isbn`).
- **A3.** `buscarEdicoes` devolve ao LLM no máximo 10 edições, as que conferem primeiro e, entre elas,
  as não digitais antes. Cada edição traz `confereComOLivro` (resultado da
  `ConferenciaEdicao` com tudo que já se sabe do ISBN), `isbn`, `titulo`,
  `subtitulo`, `autores`, `editora`, `paginas`, `digital` e `sinopse`.
- **A4.** ISBN inválido, não encontrado ou catálogo indisponível: a ferramenta devolve `erro` e a conversa continua.
- **A5.** O LLM pode consultar no máximo **2** ISBNs de memória, isto é, que não vieram de uma consulta anterior nem são o
  do livro. A partir do terceiro, a ferramenta devolve `erro` sem consultar o catálogo.

**Resposta**

- **A6.** Texto que não é JSON lança `AgenteIndisponivelException`. Exceções do `ClienteLlm` não são capturadas.
- **A7.** O ISBN conferido é o já cadastrado no livro, se for válido; senão, o da conclusão. Se ele ainda não foi
  consultado na BrasilAPI, o agente consulta. A edição é aceita se a `ConferenciaEdicao.mesmoLivro` aceita o conjunto das
  edições encontradas com esse ISBN.
- **A8.** Sem edição aceita: `encontradoNoCatalogo` = `false` e ISBN, páginas e editora `null`. Se além disso `conheceObra`
  for `false` ou ausente, a sugestão é `vazia(consultas)`.
- **A9.** Com edição aceita: ISBN-13, páginas e editora saem do **catálogo**, da BrasilAPI antes do Google Books.
  Páginas fora de 1 a 10.000 são ignoradas. `edicaoDigital` = `true` se algum catálogo diz que esse ISBN é digital (a edição
  digital é aceita, só é avisada); sem edição aceita, `false`.
- **A10. Sinopse:** texto sem espaços nas pontas; em branco, não texto ou com mais de **1500** caracteres vira `null`.
- **A11. Autores e tags:** espaços normalizados; descarta itens que não são texto, em branco, com mais de **120** (autores)
  ou **40** (tags) caracteres e repetidos (sem acentos e maiúsculas). No máximo **5** de cada; lista ausente vira vazia.
- **A12.** `fonteAutores` é `CATALOGO` se há autores e todos aparecem na edição aceita; senão `IA`.
- **A13.** `consultas` lista as consultas aos catálogos, na ordem, com o resultado de cada uma.
- **A14.** `modelo()` retorna o `modelo()` do `ClienteLlm`.

## Exemplos

Livro: "Memórias do Subsolo", de Fiódor Dostoiévski. Edição certa: ISBN 9788573261585, Editora 34.

- **E1 (A7, A9, A12).** Dado que a busca traz a edição certa com 140 páginas e a BrasilAPI com 152, e a conclusão com o
  ISBN "978-85-7326-158-5", Então `encontradoNoCatalogo` = `true`, ISBN "9788573261585", 152 páginas, "Editora 34" e
  `fonteAutores` = `CATALOGO`.
- **E2 (A8).** Dada a conclusão com um ISBN que a BrasilAPI diz ser de "100 receitas de macarrão" e `conheceObra` =
  `true`, Então ISBN, páginas e editora são `null`, mas a sinopse e as tags ficam.
- **E3 (A7).** Dada a conclusão com um ISBN que nenhum catálogo conhece, Então o ISBN é `null`.
- **E4 (A7).** Dado que o Google Books confere e a BrasilAPI traz outro título para o mesmo ISBN, Então
  `encontradoNoCatalogo` = `false`.
- **E5 (A7).** Dado o livro com ISBN 9788573261585 e a conclusão com outro, Então vale a edição do ISBN do livro e o outro
  não é consultado.
- **E6 (A8).** Dada a conclusão com `isbn` `null` e `conheceObra` = `false`, Então a sugestão é vazia, mesmo com sinopse e
  tags na conclusão.
- **E7 (A4).** Dado o catálogo lançando `CatalogoIndisponivelException`, Então a ferramenta devolve `erro`, nada é lançado e
  a sinopse fica.
- **E8 (A5).** Dado o LLM consultando 3 ISBNs de memória, Então o terceiro não chega ao catálogo.
- **E9 (A2).** Dado `buscaPorTituloLigada()` = `false`, Então a única ferramenta é `consultarIsbn`.
- **E10 (A11).** Dadas as tags `["Ficção", "ficcao", " ", "drama", "romance", "clássico", "russo", "filosofia"]`, Então as
  tags são `["Ficção", "drama", "romance", "clássico", "russo"]`.
- **E11 (A9).** Com 10000 ou 1 página no catálogo, o valor é aceito; com 10001 ou `null`, as páginas são `null`.
- **E12 (A6).** Dado o LLM devolvendo `isso não é json`, Então `AgenteIndisponivelException`.

## Fora do escopo

- O texto do prompt e a conversa HTTP com o Gemini (`GeminiCliente`) e com os catálogos (`CatalogoLivros`), que têm
  testes próprios com servidor falso.
- Decidir quais sugestões entram no livro (é do `EnriquecimentoService`).
