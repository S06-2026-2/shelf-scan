classe: br.com.virabrequim.database_service.domain.node.AutorNode

# Spec: AutorNode

## Contexto

Autor de um livro. O autor é **reaproveitado** entre livros pelo nome (*find-or-create* no `LivroService`): o nome é
guardado com os espaços normalizados e comparado sem diferenciar maiúsculas de minúsculas (acentos contam). A regra do
nome fica no próprio node.

```
(Livro)-[:WRITTEN_BY]->(Autor)
```

Autores sem nenhum livro são apagados (limpeza de órfãos, feita nos services e no repositório).

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `AutorNode()` | Construtor vazio (usado pelo SDN) | — |
| `AutorNode(String nome)` | Cria um autor com nome válido | A1, A2 |
| `void setNome(String nome)` | Altera o nome | A1, A2 |
| `String getNome()` | Nome guardado | A1, A2 |

## Regras

- **A1.** O nome é obrigatório: `null`, vazio ou só com espaços lança `IllegalArgumentException`. Com nome inválido, o
  `setNome` não altera o nome atual.
- **A2.** O nome é guardado sem espaços nas pontas e com espaços repetidos no meio juntados em um só. Maiúsculas e
  acentos são mantidos.

## Exemplos

- **E1 (A2).** `new AutorNode("  Fiódor   Dostoiévski ")` tem o nome "Fiódor Dostoiévski".
- **E2 (A2).** `new AutorNode("machado de ASSIS")` tem o nome "machado de ASSIS" (maiúsculas mantidas).
- **E3 (A1).** `new AutorNode(null)`, `new AutorNode("")` e `new AutorNode("   ")` lançam `IllegalArgumentException`.
- **E4 (A1).** Dado o autor "Ana", Quando chamo `setNome(" ")`, Então `IllegalArgumentException` e o nome continua
  "Ana".

## Fora do escopo

- Reaproveitar o autor do banco (feito no `LivroService`).
- Autores homônimos: o mesmo nome é o mesmo autor (TODO).
- Nacionalidade e datas (sem regras por enquanto).
