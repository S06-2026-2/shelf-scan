classe: br.com.virabrequim.database_service.agente.AgenteEnriquecedor

# Spec: AgenteEnriquecedor

## Contexto

Agente de IA que **sugere** dados para completar um livro: sinopse, ISBN, número de páginas, autores e tags
(gêneros). Recebe o `LivroNode` (título, autores, volume, ISBN) e, opcionalmente, o texto bruto que o OCR leu da capa,
monta um prompt e pede ao LLM um JSON no formato de um schema fixo (saída estruturada).

O LLM pode errar ou inventar (alucinação), então a resposta passa por **travas** antes de virar
`SugestaoEnriquecimento`. O agente não grava nada: quem decide o que entra no livro é o `EnriquecimentoService`.

O LLM fica atrás da interface `ClienteLlm` (em produção, o `GeminiCliente`). **Nos testes, o `ClienteLlm` é um falso**
(classe de teste ou mock do Mockito) que devolve um JSON fixo e guarda o prompt recebido. O `JsonMapper` é real
(`new JsonMapper()`, do pacote `tools.jackson.databind.json`).

Formato do JSON que o LLM devolve:

```json
{"livroIdentificado": true, "sinopse": "...", "isbn": "...", "paginas": 152,
 "autores": ["..."], "tags": ["..."]}
```

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `AgenteEnriquecedor(ClienteLlm clienteLlm, JsonMapper jsonMapper)` | Injeção pelo construtor | — |
| `SugestaoEnriquecimento sugerir(LivroNode livro, String textoBruto)` | Pede a sugestão ao LLM e aplica as travas | A1–A9 |
| `String modelo()` | Nome do modelo usado | A10 |

Colaboradores e tipos:

| Tipo | Uso |
| --- | --- |
| `ClienteLlm.gerarJson(String prompt, Map<String, Object> schema)` | Devolve o JSON gerado (texto) |
| `ClienteLlm.modelo()` | Nome do modelo |
| `record SugestaoEnriquecimento(boolean livroIdentificado, String sinopse, String isbn, Integer paginas, List<String> autores, List<String> tags)` | Resultado; `SugestaoEnriquecimento.vazia()` = `false`, `null`s e listas vazias |
| `AgenteIndisponivelException` | Falha do agente (vira 503 na API) |

## Regras

**Prompt**

- **A1.** `sugerir` chama `gerarJson` **uma vez**. O prompt contém o título do livro, os nomes dos autores, o volume e o
  ISBN cadastrados. Se `textoBruto` não for `null` nem em branco, o prompt também contém o texto bruto (sem espaços
  nas pontas), cortado nos primeiros **2000** caracteres.

**Resposta**

- **A2.** Se o texto devolvido não for um JSON válido, lança `AgenteIndisponivelException`. Exceções lançadas pelo
  `ClienteLlm` (inclusive `AgenteIndisponivelException`) não são capturadas.
- **A3.** Se `livroIdentificado` for `false` ou estiver ausente, retorna
  `SugestaoEnriquecimento.vazia()`, **mesmo que os outros campos venham preenchidos**.
- **A4.** Com `livroIdentificado` = `true`, a sugestão tem `livroIdentificado` = `true` e os campos de A5–A9.

**Travas**

- **A5. Sinopse:** texto sem espaços nas pontas. `null`, em branco, não texto ou com mais de **1500** caracteres (depois
  de tirar os espaços das pontas) vira `null`.
- **A6. ISBN:** o texto passa por `Isbn.normalizado` (ver a spec `Isbn`): inválido vira `null`; válido vem sem hífens
  e espaços.
- **A7. Páginas:** só número inteiro entre **1 e 10.000** (inclusive). Fora disso, `null`, decimal ou texto
  (ex.: `"152"`) vira `null`.
- **A8. Autores e tags:** cada item da lista vira texto com espaços normalizados (sem espaços nas pontas e com espaços
  repetidos juntados). São descartados: itens que não são texto, em branco, com mais de **120** caracteres (autores) ou
  **40** caracteres (tags), e **repetidos**, comparando sem acentos e sem diferenciar maiúsculas (fica o primeiro).
- **A9.** No máximo **5** autores e **5** tags (os primeiros que passaram por A8). Lista ausente ou `null` vira lista
  vazia.

**Modelo**

- **A10.** `modelo()` retorna o `modelo()` do `ClienteLlm`.

## Exemplos

Livro dos exemplos: `new LivroNode("Memórias do Subsolo")` com autores `[new AutorNode("Fiódor Dostoiévski")]`,
sem volume e sem ISBN. `textoBruto` = `null`, a não ser quando estiver escrito.

- **E1 (A4–A9).** Dado o LLM devolvendo `{"livroIdentificado": true, "sinopse": "Um homem amargurado narra sua vida.",
  "isbn": "978-85-7326-158-5", "paginas": 152, "autores": ["Fiódor Dostoiévski"], "tags": ["romance", "clássico"]}`,
  Então a sugestão tem `livroIdentificado` = `true`, sinopse "Um homem amargurado narra sua vida.", ISBN
  "9788573261585", 152 páginas, autores ["Fiódor Dostoiévski"] e tags ["romance", "clássico"].
- **E2 (A3).** Dado `{"livroIdentificado": false, "sinopse": "Inventada", "isbn": null, "paginas": 100,
  "autores": [], "tags": ["romance"]}`, Então a sugestão é igual a `SugestaoEnriquecimento.vazia()`.
- **E3 (A3).** Dado `{"sinopse": "Algo", "paginas": 100}` (sem `livroIdentificado`), Então a sugestão é igual a
  `SugestaoEnriquecimento.vazia()`.
- **E4 (A5, A6, A7).** Dado `livroIdentificado` = `true`, sinopse com 1501 letras "a", ISBN "9788535914848" (dígito
  errado) e `paginas` = 0, Então sinopse, ISBN e páginas são `null`.
- **E5 (A5).** Dado `livroIdentificado` = `true` e sinopse `"  Texto curto.  "`, Então a sinopse é "Texto curto.".
  Com sinopse `"   "`, Então a sinopse é `null`.
- **E6 (A7).** Dado `livroIdentificado` = `true`: com `paginas` = 10000, Então 10000; com 10001, Então `null`; com
  `"152"` (texto), Então `null`.
- **E7 (A8, A9).** Dado `livroIdentificado` = `true` e tags `["Ficção", "ficcao", " ", "drama", "romance", "clássico",
  "russo", "filosofia"]`, Então as tags são `["Ficção", "drama", "romance", "clássico", "russo"]`.
- **E8 (A8).** Dado `livroIdentificado` = `true` e autores `["  Ana   Maria ", "ANA MARIA", 42]`, Então os autores são
  `["Ana Maria"]`.
- **E9 (A9).** Dado `livroIdentificado` = `true` sem os campos `autores` e `tags`, Então as duas listas são vazias.
- **E10 (A1).** Quando chamo `sugerir(livro, "MEMORIAS DO SUBSOLO editora 34")`, Então o prompt recebido pelo
  `ClienteLlm` contém "Memórias do Subsolo", "Fiódor Dostoiévski" e "MEMORIAS DO SUBSOLO editora 34".
- **E11 (A1).** Quando chamo `sugerir(livro, "   ")`, Então `gerarJson` é chamado uma vez e o prompt não contém
  "textoDoOcr".
- **E12 (A2).** Dado o LLM devolvendo `isso não é json`, Então `sugerir` lança `AgenteIndisponivelException`.
- **E13 (A2).** Dado o `ClienteLlm` lançando `AgenteIndisponivelException("sem chave")`, Então `sugerir` lança a mesma
  exceção.
- **E14 (A10).** Dado o `ClienteLlm` com `modelo()` = "falso", Então `modelo()` retorna "falso".

## Casos de borda

- **O LLM "reconhece" o livro, mas inventa um ISBN com dígito verificador válido:** passa pela trava (A6). Por isso a
  API devolve um aviso de que os dados vêm de IA.
- **Texto bruto enorme** (ex.: OCR de uma página inteira) é cortado em 2000 caracteres (A1), para não gastar cota.

## Fora do escopo

- O texto exato do prompt e do schema (só o que A1 diz que o prompt contém).
- A chamada HTTP ao Gemini (`GeminiCliente`): testada só com a API real.
- Decidir quais sugestões entram no livro (é do `EnriquecimentoService`).
