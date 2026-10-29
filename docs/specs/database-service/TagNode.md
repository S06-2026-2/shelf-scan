classe: br.com.virabrequim.database_service.domain.node.TagNode

# Spec: TagNode

## Contexto

Tag de um livro (ex.: gênero). O `@Id` é o **nome totalmente normalizado** (sem acentos, em minúsculas, com espaços
normalizados): "Ficção Científica" e "ficcao  cientifica" são a mesma tag. O `nomeExibicao` guarda a forma do
primeiro cadastro, que é a mostrada na API.

```
(Livro)-[:HAS_TAG]->(Tag)
```

Tags sem nenhum livro são apagadas (limpeza de órfãos, feita nos services e no repositório).

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `TagNode()` | Construtor vazio (usado pelo SDN) | T3 |
| `TagNode(String nome)` | Cria uma tag | T1, T2 |
| `String getNome()` | Nome normalizado (o `@Id`) | T2 |
| `String getNomeExibicao()` | Nome para exibir | T2, T3 |

## Regras

- **T1.** O nome é obrigatório: `null`, vazio ou só com espaços lança `IllegalArgumentException`.
- **T2.** `getNome()` é o nome sem espaços nas pontas, com espaços repetidos juntados, **sem acentos** (letras como
  "ç" viram "c") e em **minúsculas**. `getNomeExibicao()` é o nome informado só com os espaços normalizados (mantém
  acentos e maiúsculas).
- **T3.** Se o `nomeExibicao` for `null` (tag gravada antes dele existir), `getNomeExibicao()` retorna o `nome`.

## Exemplos

- **E1 (T2).** `new TagNode("  Ficção   Científica ")` tem `getNome()` = "ficcao cientifica" e `getNomeExibicao()` =
  "Ficção Científica".
- **E2 (T2).** `new TagNode("ROMANCE")` e `new TagNode("romance")` têm o mesmo `getNome()`: "romance".
- **E3 (T2).** `new TagNode("Açaí Ébano")` tem `getNome()` = "acai ebano".
- **E4 (T1).** `new TagNode(null)`, `new TagNode("")` e `new TagNode("  ")` lançam `IllegalArgumentException`.
- **E5 (T3).** Um `new TagNode()` com o campo `nome` = "romance" e sem `nomeExibicao` (o campo não tem setter; no
  teste, preencher com `ReflectionTestUtils.setField`) retorna "romance" em `getNomeExibicao()`.

## Fora do escopo

- Reaproveitar a tag do banco para manter o `nomeExibicao` do primeiro cadastro (feito no `LivroService`).
