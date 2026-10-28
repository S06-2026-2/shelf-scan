classe: br.com.virabrequim.database_service.service.LivroService

# Spec: LivroService

## Contexto

Gerencia os livros físicos de um usuário: adicionar (ex.: depois de confirmar o reconhecimento do OCR), buscar,
listar por estante, mover entre estantes, remover e atualizar a leitura.

Cada `LivroNode` é um **exemplar físico**. O dono não é guardado: é o usuário dono da estante onde o livro está.

```
(Usuario)-[:HAS]->(Estante)<-[:STORED_IN]-(Livro)-[:LENT_TO]->(Usuario)
```

Um livro **é do usuário** quando `UsuarioRepository.findDonoDoLivro(livroId)` retorna um usuário com o mesmo `id`.
Uma estante **é do usuário** quando está na lista `estantes` dele (comparando pelo `id`).

**Hoje** é `calendario.diaHoje()` (data de Brasília). O momento da adição é
`ZonedDateTime.now(calendario.getClock())`. Nos testes, o `Calendario` e os repositórios são mocks (Mockito).

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `LivroService(UsuarioRepository usuarioRepository, LivroRepository livroRepository, Calendario calendario)` | Injeção pelo construtor | — |
| `LivroNode adicionar(Long usuarioId, LivroNode livro, Long estanteId)` | Adiciona um livro (estante opcional) | L1–L6 |
| `LivroNode buscar(Long livroId)` | Busca pelo id | L1 |
| `List<LivroNode> listarDaEstante(Long usuarioId, Long estanteId)` | Livros de uma estante | L1, L4 |
| `LivroNode mover(Long usuarioId, Long livroId, Long estanteDestinoId)` | Troca a estante | L1, L7, L8 |
| `void remover(Long usuarioId, Long livroId)` | Apaga o livro | L1, L7, L9 |
| `LivroNode atualizarLeitura(Long usuarioId, Long livroId, StatusLeitura status, Integer paginaAtual)` | Leitura do dono ou de quem pegou emprestado | L1, L10 |

Colaboradores usados (mockados nos testes):

| Método | Retorno |
| --- | --- |
| `UsuarioRepository.findById(Long id)` | `Optional<UsuarioNode>` |
| `UsuarioRepository.findDonoDoLivro(Long livroId)` | `Optional<UsuarioNode>` |
| `LivroRepository.findById(Long id)` | `Optional<LivroNode>` |
| `LivroRepository.findAllByEstanteId(Long estanteId)` | `List<LivroNode>` |
| `LivroRepository.save(LivroNode livro)` | o livro salvo |
| `LivroRepository.delete(LivroNode livro)` | `void` |
| `Calendario.diaHoje()` / `Calendario.getClock()` | `LocalDate` / `Clock` |

Campos do `LivroNode` usados aqui: `id`, `titulo`, `isbn`, `paginas` (`Integer`), `dataDeAdicao` (`ZonedDateTime`),
`estante`, `emprestimos`, além de `emprestimoAtivo()` e `atualizarLeitura(...)` (ver spec do `LivroNode`).

## Regras

**Existência**

- **L1.** Usuário inexistente (`findById` vazio) ou livro inexistente (`findById` vazio) lança
  `NoSuchElementException`.

**Adicionar**

- **L2.** `livro` `null` lança `IllegalArgumentException`. O título é obrigatório: `null`, vazio ou só com espaços
  lança `IllegalArgumentException`. Os espaços nas pontas são removidos.
- **L3.** `paginas` é opcional (`null` = não informado). Se informado, tem que ser maior que zero; `0` ou negativo
  lança `IllegalArgumentException`.
- **L4.** `estanteId` `null` coloca o livro na **estante padrão** do usuário. Se informado, a estante tem que ser do
  usuário; caso contrário (inexistente ou de outro usuário) lança `NoSuchElementException`.
- **L5.** O livro adicionado é sempre **novo**: o `id` recebido é ignorado (vira `null`), a lista de empréstimos
  recebida é descartada (fica vazia) e `dataDeAdicao` é o momento atual (`ZonedDateTime.now(calendario.getClock())`),
  ignorando o valor recebido.
- **L6.** O livro é salvo (`save`) e o método retorna o objeto retornado por `save`.

**Dono**

- **L7.** Em `mover` e `remover`, o livro tem que ser do usuário (`findDonoDoLivro`). Se não for, lança
  `NoSuchElementException` (não revela que o livro existe).

**Mover**

- **L8.** A estante de destino tem que ser do usuário; caso contrário lança `NoSuchElementException`. O livro passa
  para a estante de destino, é salvo e o método retorna o livro salvo. Mover um livro emprestado é permitido, e
  mover para a estante em que ele já está não é erro.

**Remover**

- **L9.** Um livro com empréstimo ativo não pode ser removido: lança `IllegalStateException` e `delete` não é
  chamado. Sem empréstimo ativo, o livro é apagado com `LivroRepository.delete`.

**Leitura**

- **L10.** `atualizarLeitura` decide de quem é a leitura:
  - se o usuário é o **dono** do livro → `livro.atualizarLeitura(status, paginaAtual, hoje)`;
  - senão, se o usuário é quem está com o **empréstimo ativo** do livro (o `usuario` do empréstimo ativo tem o mesmo
    `id`) → `emprestimoAtivo.atualizarLeitura(status, paginaAtual, livro.getPaginas(), hoje)` (o número de
    páginas do livro é passado porque o empréstimo não tem acesso ao livro);
  - senão → `NoSuchElementException`.

  Depois o livro é salvo e o método retorna o livro salvo. As exceções de validação da leitura
  (`IllegalArgumentException`) são propagadas, e nesse caso nada é salvo.

## Exemplos

**Hoje = 10/03/2026**; o `Clock` do calendário está fixo em 10/03/2026 às 14:30 de Brasília.
Usuário Ana (id 1): estantes "Minha estante" (id 10, padrão) e "Quarto" (id 11).
Usuário Bruno (id 2): estante "Minha estante" (id 20, padrão).

**Adicionar**

- **E1 (L4, L5, L6).** Quando chamo `adicionar(1L, livro "  Memórias do Subsolo ", null)`, Então o livro salvo tem
  título "Memórias do Subsolo", está na estante 10, `dataDeAdicao` = 10/03/2026 14:30 (-03:00), e o método retorna o
  objeto devolvido por `save`.
- **E2 (L4).** Quando chamo `adicionar(1L, livro, 11L)`, Então o livro salvo está na estante 11.
- **E3 (L4).** Quando chamo `adicionar(1L, livro, 20L)` (estante do Bruno) ou `adicionar(1L, livro, 999L)`, Então
  `NoSuchElementException` e `save` não é chamado.
- **E4 (L5).** Dado um livro recebido com `id` = 50, um empréstimo na lista e `dataDeAdicao` = 01/01/2020, Quando
  adiciono, Então o livro salvo tem `id` `null`, lista de empréstimos vazia e `dataDeAdicao` = 10/03/2026 14:30.
- **E5 (L2).** Quando adiciono com livro `null`, ou com título `null`, `""` ou `"  "`, Então
  `IllegalArgumentException` e `save` não é chamado.
- **E6 (L3).** Quando adiciono com `paginas` = `null`, Então é aceito. Com `paginas` = 0 ou -5, Então
  `IllegalArgumentException`.
- **E7 (L1).** Quando chamo `adicionar(99L, livro, null)` (usuário inexistente), Então `NoSuchElementException`.

**Buscar e listar**

- **E8 (L1).** Dado `LivroRepository.findById(5L)` = vazio, Quando chamo `buscar(5L)`, Então `NoSuchElementException`.
- **E9 (L4).** Dado `findAllByEstanteId(11L)` = livros A e B, Quando chamo `listarDaEstante(1L, 11L)`, Então retorna
  A e B. Quando chamo `listarDaEstante(1L, 20L)`, Então `NoSuchElementException`.

**Mover** (livro 5 de Ana, na estante 10)

- **E10 (L8).** Quando chamo `mover(1L, 5L, 11L)`, Então o livro passa para a estante 11 e é salvo.
- **E11 (L8).** Quando chamo `mover(1L, 5L, 20L)` (estante do Bruno), Então `NoSuchElementException` e nada é salvo.
- **E12 (L7).** Quando chamo `mover(2L, 5L, 20L)` (Bruno tentando mover livro de Ana), Então
  `NoSuchElementException`.
- **E13 (L8).** Dado o livro 5 com empréstimo ativo para Bruno, Quando chamo `mover(1L, 5L, 11L)`, Então é aceito.

**Remover** (livro 5 de Ana)

- **E14 (L9).** Dado o livro 5 sem empréstimos, Quando chamo `remover(1L, 5L)`, Então `delete` é chamado com o livro 5.
- **E15 (L9).** Dado o livro 5 com empréstimo ativo, Quando chamo `remover(1L, 5L)`, Então `IllegalStateException` e
  `delete` não é chamado.
- **E16 (L9).** Dado o livro 5 só com empréstimos devolvidos, Quando chamo `remover(1L, 5L)`, Então `delete` é chamado.
- **E17 (L7).** Quando chamo `remover(2L, 5L)`, Então `NoSuchElementException` e `delete` não é chamado.

**Leitura** (livro 5 de Ana, com 200 páginas, emprestado para Bruno; usuário Carla, id 3, sem relação com o livro)

- **E18 (L10).** Quando chamo `atualizarLeitura(1L, 5L, LENDO, 80)`, Então a leitura **do livro** fica `LENDO` na página 80 (40%), a do
  empréstimo não muda, e o livro é salvo.
- **E19 (L10).** Quando chamo `atualizarLeitura(2L, 5L, LIDO, null)`, Então a leitura **do empréstimo ativo** fica
  `LIDO` (página 200, 100%, concluído em 10/03/2026), a do livro não muda, e o livro é salvo.
- **E20 (L10).** Quando chamo `atualizarLeitura(3L, 5L, LENDO, 10)`, Então `NoSuchElementException` e nada é salvo.
- **E21 (L10).** Quando chamo `atualizarLeitura(1L, 5L, LENDO, 250)` (passa das 200 páginas), Então `IllegalArgumentException` e nada é salvo.

## Casos de borda

- **Livro sem estante informada** vai para a padrão (E1). Não existe livro sem estante.
- **Tentar mexer no livro de outro usuário** responde como se o livro não existisse (`NoSuchElementException`), para
  não revelar dados de outras pessoas (E12, E17).
- **O dono e quem pegou emprestado têm leituras separadas** do mesmo livro (E18, E19).
- **Remover livro com histórico** (empréstimos devolvidos) é permitido e apaga o histórico junto (E16).

## Fora do escopo

- Reaproveitar autores e tags já existentes (buscar pelo nome antes de criar). Por enquanto são salvos como vierem.
- Validação do formato do ISBN.
- Enriquecimento dos dados do livro pelo agente de IA.
- Empréstimo e devolução (são do `EmprestimoService`).
- A consulta Cypher de `findDonoDoLivro` (testada em teste de integração, não aqui).
- Autenticação (por enquanto o `usuarioId` vem da rota).
