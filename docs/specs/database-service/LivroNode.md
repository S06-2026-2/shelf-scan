classe: br.com.virabrequim.database_service.domain.node.LivroNode

# Spec: LivroNode

## Contexto

Representa um **livro físico individual** (um exemplar). Duas cópias da mesma obra são dois nós `Livro` diferentes,
com ids diferentes, mesmo que tenham título, autor e ISBN iguais. Isso é intencional.

No grafo:

```
(Usuario)-[:HAS]->(Estante)<-[:STORED_IN]-(Livro)-[:LENT_TO]->(Usuario)
(Livro)-[:WRITTEN_BY]->(Autor)
(Livro)-[:HAS_TAG]->(Tag)
```

O dono do livro não é guardado no livro: é o usuário dono da estante onde ele está (`STORED_IN`). Os empréstimos
(`LENT_TO`) ficam numa lista com todo o histórico: os devolvidos e no máximo um ativo.

O livro também guarda a **leitura do dono**: status, página atual e data de conclusão. A **porcentagem de leitura não
é guardada**: é calculada a partir da página atual e do número de páginas do livro. A leitura de quem pegou o livro
emprestado fica no próprio empréstimo (`EmprestimoRelationship`), não aqui. Um livro de biblioteca normalmente não
tem leitura do dono, por isso a leitura começa vazia (`null`).

Toda vez que a spec fala em **hoje**, é o parâmetro `hoje` recebido pelo método (data de Brasília, passada pelo
service).

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `LivroNode()` | Livro vazio, sem validação (usado pelo SDN e nos testes) | N1 |
| `LivroNode(String titulo)` | Livro novo com título válido | N1, N11 |
| `void setTitulo(String titulo)` | Altera o título | N11 |
| `void setPaginas(Integer paginas)` | Altera o total de páginas; a leitura acompanha | N12–N14 |
| `Optional<EmprestimoRelationship> emprestimoAtivo()` | O empréstimo em andamento, se houver | N2, N3 |
| `boolean isEmprestado()` | Se o livro está emprestado agora | N4 |
| `void atualizarLeitura(StatusLeitura status, Integer paginaAtual, LocalDate hoje)` | Atualiza a leitura do dono | N5–N9 |
| `StatusLeitura getStatusLeitura()` / `Integer getPaginaAtual()` / `LocalDate getDataConclusaoLeitura()` | Leitura do dono | N1, N5–N9 |
| `Integer getPorcentagemLeitura()` | Porcentagem calculada (não é campo guardado) | N1, N10 |
| `Integer getPaginas()` | Número de páginas do livro (opcional, `null` = não informado) | N7, N8, N10 |
| `List<EmprestimoRelationship> getEmprestimos()` | Histórico de empréstimos | N1–N3 |

`StatusLeitura` é um enum em `br.com.virabrequim.database_service.domain` com os valores `QUERO_LER`, `LENDO` e
`LIDO`. Os campos de leitura não têm setter público: só mudam por `atualizarLeitura`.

## Regras

**Livro novo**

- **N1.** Um livro criado com o construtor sem argumentos tem as listas `tags`, `autores` e `emprestimos`
  **vazias** (nunca `null`), e a leitura vazia: `getStatusLeitura()`, `getPaginaAtual()`,
  `getDataConclusaoLeitura()` e `getPorcentagemLeitura()` retornam `null`.

**Empréstimo ativo**

- **N2.** `emprestimoAtivo()` retorna o empréstimo da lista que está ativo (`isEmprestado()` = `true`). Se a lista
  estiver vazia, ou todos os empréstimos já tiverem sido devolvidos, retorna `Optional.empty()`.
- **N3.** Se a lista tiver **mais de um** empréstimo ativo (dado inconsistente vindo do banco), `emprestimoAtivo()`
  lança `IllegalStateException` com a mensagem "Livro com mais de um empréstimo ativo".
- **N4.** `isEmprestado()` retorna `true` quando existe empréstimo ativo e `false` quando não existe.

**Leitura do dono**

- **N5.** `status` é obrigatório. `atualizarLeitura(null, ...)` lança `IllegalArgumentException`.
- **N6.** `QUERO_LER`: a página atual passa a ser `0` e a data de conclusão `null`. A `paginaAtual` recebida é
  ignorada.
- **N7.** `LENDO`: a página atual recebida é usada se for válida: não negativa (`0` é aceito: começou agora) e, se o
  livro tiver o número de páginas (`paginas` não `null`), não maior que ele. Sem `paginas`, não há limite superior.
  Página **inválida** (`null`, negativa ou maior que `paginas`) **não dá erro**: a página atual passa a ser `0`. A data
  de conclusão passa a ser `null`.
- **N8.** `LIDO`: a página atual passa a ser o número de páginas do livro (`null` se ele não tiver) e a data de
  conclusão passa a ser `hoje`. A `paginaAtual` recebida é ignorada. Marcar como `LIDO` um livro que já estava `LIDO`
  atualiza a data de conclusão para `hoje` (releitura).
- **N9.** Quando `atualizarLeitura` lança exceção (status `null`), nada muda: status, página atual e data continuam
  como estavam.

**Título e páginas**

- **N11.** O título é obrigatório: `null`, vazio ou só com espaços lança `IllegalArgumentException`, e os espaços nas
  pontas são removidos. Vale no construtor `LivroNode(String titulo)` e no `setTitulo`; com título inválido, o título
  atual não muda.
- **N12.** `setPaginas`: `null` ou `0` = sem total de páginas (guardado como `null`). Negativo lança
  `IllegalArgumentException`. Positivo é guardado.
- **N13.** Ao mudar o total, a leitura do dono acompanha: `LIDO` passa a ter página atual = novo total (ou `null`, se
  ficar sem total); `LENDO` com página atual **maior** que o novo total lança `IllegalStateException` (igual é
  permitido); sem total não há limite. `QUERO_LER` e leitura vazia não mudam. A mesma regra vale para a leitura do
  **empréstimo ativo** (`emprestimoAtivo()`); empréstimos devolvidos não são verificados nem alterados.
- **N14.** `setPaginas` verifica tudo antes de alterar: se lançar exceção, nem o total nem as leituras mudam.

**Porcentagem de leitura**

- **N10.** `getPorcentagemLeitura()` é calculada a cada chamada:
  - leitura vazia (status `null`) → `null`;
  - `QUERO_LER` → `0`;
  - `LIDO` → `100` (mesmo sem `paginas`);
  - `LENDO` com `paginas` → `paginaAtual * 100 / paginas`, **arredondado para baixo** (inteiro), limitado a `100`;
  - `LENDO` sem `paginas` → `null` (não dá para calcular).

## Exemplos

Nos exemplos, **hoje = 10/03/2026**.

**Livro novo**

- **E1 (N1).** Quando crio `new LivroNode()`, Então `getTags()`, `getAutores()` e `getEmprestimos()` estão vazias,
  e status, página atual, data de conclusão e porcentagem são `null`.

**Empréstimo ativo**

- **E2 (N2, N4).** Dado um livro sem empréstimos, Então `emprestimoAtivo()` = `Optional.empty()` e
  `isEmprestado()` = `false`.
- **E3 (N2, N4).** Dado um livro com um empréstimo devolvido e um ativo, Então `emprestimoAtivo()` contém o ativo e
  `isEmprestado()` = `true`.
- **E4 (N2, N4).** Dado um livro com dois empréstimos, ambos devolvidos, Então `emprestimoAtivo()` =
  `Optional.empty()` e `isEmprestado()` = `false`.
- **E5 (N3).** Dado um livro com dois empréstimos ativos, Quando chamo `emprestimoAtivo()`, Então
  `IllegalStateException` com a mensagem "Livro com mais de um empréstimo ativo".

**Leitura do dono** (livro novo com **200 páginas**, sem leitura, a não ser quando estiver escrito outro estado)

| Exemplo | Chamada | Status | Página atual | Porcentagem | Conclusão | Regras |
| --- | --- | --- | --- | --- | --- | --- |
| E6 | `atualizarLeitura(QUERO_LER, 50, hoje)` | `QUERO_LER` | 0 | 0 | `null` | N6, N10 |
| E7 | `atualizarLeitura(LENDO, 0, hoje)` | `LENDO` | 0 | 0 | `null` | N7, N10 |
| E8 | `atualizarLeitura(LENDO, 80, hoje)` | `LENDO` | 80 | 40 | `null` | N7, N10 |
| E9 | `atualizarLeitura(LENDO, 133, hoje)` | `LENDO` | 133 | 66 (66,5 arredondado para baixo) | `null` | N7, N10 |
| E10 | `atualizarLeitura(LENDO, 200, hoje)` | `LENDO` | 200 | 100 | `null` | N7, N10 |
| E11 | `atualizarLeitura(LIDO, 30, hoje)` | `LIDO` | 200 | 100 | 10/03/2026 | N8, N10 |

- **E12 (N7).** Quando chamo `atualizarLeitura(LENDO, null, hoje)`, `atualizarLeitura(LENDO, -1, hoje)` ou
  `atualizarLeitura(LENDO, 201, hoje)`, Então fica `LENDO` na página `0`, sem exceção.
- **E13 (N5, N9).** Dado livro `LENDO` na página 80, Quando chamo `atualizarLeitura(null, 90, hoje)`, Então
  `IllegalArgumentException`, e o livro continua `LENDO` na página 80.
- **E14 (N7).** Dado livro `LIDO` concluído em 01/03/2026, Quando chamo `atualizarLeitura(LENDO, 10, hoje)`, Então
  fica `LENDO`, página 10 e conclusão `null`.
- **E15 (N8).** Dado livro `LIDO` concluído em 01/03/2026, Quando chamo `atualizarLeitura(LIDO, null, hoje)`, Então a
  conclusão passa a ser 10/03/2026.

**Livro sem número de páginas** (`paginas` = `null`)

| Exemplo | Chamada | Status | Página atual | Porcentagem | Conclusão | Regras |
| --- | --- | --- | --- | --- | --- | --- |
| E16 | `atualizarLeitura(LENDO, 500, hoje)` | `LENDO` | 500 | `null` | `null` | N7, N10 |
| E17 | `atualizarLeitura(LIDO, 30, hoje)` | `LIDO` | `null` | 100 | 10/03/2026 | N8, N10 |

- **E18 (N7).** Quando chamo `atualizarLeitura(LENDO, -1, hoje)`, Então fica `LENDO` na página `0`.

**Título e número de páginas**

- **E19 (N13, N14).** Dado livro de 200 páginas `LENDO` na página 150, Quando chamo `setPaginas(100)`, Então
  `IllegalStateException`, e o livro continua com 200 páginas, `LENDO` na página 150.
- **E20 (N13).** Dado livro de 200 páginas `LENDO` na página 150, Quando chamo `setPaginas(150)` ou
  `setPaginas(300)`, Então o total muda e a página atual continua 150.
- **E21 (N13).** Dado livro de 200 páginas `LIDO` (página 200), Quando chamo `setPaginas(180)`, Então a página atual
  passa a ser 180; Quando chamo `setPaginas(0)`, Então `paginas` e a página atual ficam `null` e a porcentagem `100`.
- **E22 (N12).** `setPaginas(0)` e `setPaginas(null)` deixam `paginas` `null`; `setPaginas(-5)` lança
  `IllegalArgumentException`.
- **E23 (N13).** Dado livro de 200 páginas sem leitura, com empréstimo ativo `LENDO` na página 120, Quando chamo
  `setPaginas(100)`, Então `IllegalStateException`; Quando chamo `setPaginas(120)`, Então o total muda.
- **E24 (N13).** Dado livro de 200 páginas com um empréstimo **devolvido** `LENDO` na página 190, Quando chamo
  `setPaginas(100)`, Então o total muda e a leitura do empréstimo devolvido continua na página 190.
- **E25 (N11).** Quando crio `new LivroNode("  Duna ")`, Então o título é "Duna"; `new LivroNode(" ")` e
  `setTitulo(null)` lançam `IllegalArgumentException`.

## Casos de borda

- **Página inválida não dá erro** em `LENDO`: vira 0 (E12, E18). Essa correção silenciosa pode mudar no futuro.
- **Página 0 e a última página são válidas** em `LENDO` (E7, E10). `LENDO` na última página continua `LENDO`: o livro
  só fica `LIDO` quando isso for pedido explicitamente.
- **A porcentagem é arredondada para baixo** (E9): 99,9% aparece como 99%, então só mostra 100% na última página.
- **Sem número de páginas** a leitura funciona, mas a porcentagem de `LENDO` fica `null` (E16).
- **Sair de `LIDO`** apaga a data de conclusão (E14).
- **Leitura vazia (`null`)** só existe no livro novo. Depois da primeira atualização não há como voltar para `null`.

## Fora do escopo

- Leitura de quem pegou o livro emprestado. Ela fica no `EmprestimoRelationship`.
- Quem é o dono do livro (derivado pelo caminho `Livro → Estante ← Usuario`, consultado no repositório).
- Regra de "um empréstimo ativo por livro" na hora de emprestar: é do `EmprestimoService`. Aqui só se trata o dado
  inconsistente (N3).
- Validação de ISBN e volume.
- Ordem da lista de empréstimos.
- Serialização para JSON / DTOs da API.
