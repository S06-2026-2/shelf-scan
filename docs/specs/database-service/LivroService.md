classe: br.com.virabrequim.database_service.service.LivroService

# Spec: LivroService

## Contexto

Gerencia os livros físicos de um usuário: adicionar (ex.: depois de confirmar o reconhecimento do OCR), buscar,
listar por estante, editar os dados (PATCH), mover entre estantes, remover e atualizar a leitura.

Cada `LivroNode` é um **exemplar físico**. O dono não é guardado: é o usuário dono da estante onde o livro está.

```
(Usuario)-[:HAS]->(Estante)<-[:STORED_IN]-(Livro)-[:LENT_TO]->(Usuario)
(Livro)-[:WRITTEN_BY]->(Autor)
(Livro)-[:HAS_TAG]->(Tag)
```

Um livro **é do usuário** quando `UsuarioRepository.findDonoDoLivro(livroId)` retorna um usuário com o mesmo `id`.
Uma estante **é do usuário** quando está na lista `estantes` dele (comparando pelo `id`).

As regras de título, total de páginas e leitura ficam no `LivroNode` e no `EmprestimoRelationship` (ver as specs
deles): este service as usa pelos construtores, setters e `atualizarLeitura`.

**Autores e tags são reaproveitados** entre livros (*find-or-create*): antes de salvar, cada autor é buscado pelo nome
(`AutorRepository.findFirstByNomeIgnoreCase`) e cada tag pelo nome normalizado (`TagRepository.findById`); se existir,
o nó do banco é usado no lugar do informado. **Autores e tags que ficam sem nenhum livro são apagados** depois de
editar ou remover um livro (`apagarOrfaos` / `apagarOrfas`).

**Hoje** é `calendario.diaHoje()` (data de Brasília). O momento da adição é
`ZonedDateTime.now(calendario.getClock())`. Nos testes, o `Calendario` e os repositórios são mocks (Mockito).

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `LivroService(UsuarioRepository usuarioRepository, LivroRepository livroRepository, AutorRepository autorRepository, TagRepository tagRepository, Calendario calendario)` | Injeção pelo construtor | — |
| `LivroNode adicionar(Long usuarioId, LivroNode livro, Long estanteId, StatusLeitura status, Integer paginaAtual)` | Adiciona um livro (estante e leitura opcionais) | L1–L6, L11 |
| `LivroNode buscar(Long livroId)` | Busca pelo id | L1 |
| `List<LivroNode> listarDaEstante(Long usuarioId, Long estanteId)` | Livros de uma estante | L1, L4 |
| `LivroNode atualizar(Long usuarioId, Long livroId, AtualizacaoLivro dados)` | Edita os dados do livro (PATCH) | L1, L7, L11–L13 |
| `LivroNode mover(Long usuarioId, Long livroId, Long estanteDestinoId)` | Troca a estante | L1, L7, L8 |
| `void remover(Long usuarioId, Long livroId)` | Apaga o livro | L1, L7, L9, L13 |
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
| `AutorRepository.findFirstByNomeIgnoreCase(String nome)` | `Optional<AutorNode>` |
| `AutorRepository.apagarOrfaos(Collection<Long> ids)` | `void` |
| `TagRepository.findById(String nome)` | `Optional<TagNode>` |
| `TagRepository.apagarOrfas(Collection<String> nomes)` | `void` |
| `Calendario.diaHoje()` / `Calendario.getClock()` | `LocalDate` / `Clock` |

Entidades e DTOs:
- `LivroNode(String titulo)`, `setTitulo`, `setPaginas` (0 = sem total; LENDO acima do novo total lança
  `IllegalStateException`; LIDO acompanha) e `atualizarLeitura` (página inválida em LENDO vira 0). Ver `LivroNode.md`.
- `AutorNode(String nome)`: nome com espaços normalizados. `TagNode(String nome)`: `getNome()` é o nome normalizado
  (sem acentos, minúsculas) e `getNomeExibicao()` a forma informada.
- `AtualizacaoLivro(String titulo, List<String> autores, List<String> tags, String isbn, String volume,
  Integer paginas, String sinopse)`: `record` do pacote `dto`; todos os campos podem ser `null`.

## Regras

**Existência**

- **L1.** Usuário inexistente (`findById` vazio) ou livro inexistente (`findById` vazio) lança
  `NoSuchElementException`.

**Adicionar**

- **L2.** `livro` `null` lança `IllegalArgumentException`. O título é revalidado pelo `setTitulo` (o livro pode ter
  vindo do construtor vazio): `null`, vazio ou só com espaços lança `IllegalArgumentException`, e os espaços nas pontas
  são removidos.
- **L3.** As páginas são revalidadas pelo `setPaginas`: `null` ou `0` = sem total (fica `null`); negativo lança
  `IllegalArgumentException`.
- **L4.** `estanteId` `null` coloca o livro na **estante padrão** do usuário. Se informado, a estante tem que ser do
  usuário; caso contrário (inexistente ou de outro usuário) lança `NoSuchElementException`.
- **L5.** O livro adicionado é sempre **novo**: o `id` recebido é ignorado (vira `null`), a lista de empréstimos
  recebida é descartada (fica vazia) e `dataDeAdicao` é o momento atual (`ZonedDateTime.now(calendario.getClock())`),
  ignorando o valor recebido.
- **L6.** Com `status` não nulo, a leitura inicial do dono é aplicada (`livro.atualizarLeitura(status, paginaAtual,
  hoje)`) **antes** do `save` (em LENDO, página inválida vira 0). Com `status` `null`, o livro fica sem leitura. O
  livro é salvo (`save`) e o método retorna o objeto retornado por `save`.

**Dono**

- **L7.** Em `atualizar`, `mover` e `remover`, o livro tem que ser do usuário (`findDonoDoLivro`). Se não for, lança
  `NoSuchElementException` (não revela que o livro existe) e nada é salvo nem apagado.

**Mover**

- **L8.** A estante de destino tem que ser do usuário; caso contrário lança `NoSuchElementException`. O livro passa
  para a estante de destino, é salvo e o método retorna o livro salvo. Mover um livro emprestado é permitido, e
  mover para a estante em que ele já está não é erro.

**Remover**

- **L9.** Um livro com empréstimo ativo não pode ser removido: lança `IllegalStateException` e `delete` não é
  chamado. Sem empréstimo ativo, o livro é apagado com `LivroRepository.delete` (o histórico de empréstimos é apagado
  junto).

**Leitura**

- **L10.** `atualizarLeitura` decide de quem é a leitura:
  - se o usuário é o **dono** do livro → `livro.atualizarLeitura(status, paginaAtual, hoje)`;
  - senão, se o usuário é quem está com o **empréstimo ativo** do livro (o `usuario` do empréstimo ativo tem o mesmo
    `id`) → `emprestimoAtivo.atualizarLeitura(status, paginaAtual, livro.getPaginas(), hoje)`;
  - senão → `NoSuchElementException`.

  Página inválida em LENDO vira 0 (sem exceção). Status `null` lança `IllegalArgumentException` e nada é salvo. Depois
  o livro é salvo e o método retorna o livro salvo.

**Autores e tags**

- **L11.** Ao adicionar e ao atualizar autores/tags, cada autor é trocado pelo do banco se
  `findFirstByNomeIgnoreCase(nome)` encontrar um; cada tag é trocada pela do banco se `findById(nome normalizado)`
  encontrar uma. Senão, ficam os informados (serão criados no `save`). Nomes repetidos na mesma lista (mesmo autor sem
  diferenciar maiúsculas, mesma tag normalizada) viram um só.

**Atualizar (PATCH)**

- **L12.** `atualizar` é parcial: campo `null` em `dados` = não mexer. `titulo` passa pelo `setTitulo`; `paginas` pelo
  `setPaginas` (0 remove o total; conflito com a leitura lança `IllegalStateException` e nada é salvo); `isbn`,
  `volume` e `sinopse` vazios ou só com espaços viram `null` (apagam o campo); `autores` e `tags` **substituem a lista
  inteira** (lista vazia remove todos), com L11. O livro é salvo e o método retorna o livro salvo.

**Órfãos**

- **L13.** Depois de `atualizar` e de `remover`, os autores e tags que estavam ligados ao livro **antes** da operação
  são passados para `AutorRepository.apagarOrfaos(ids)` e `TagRepository.apagarOrfas(nomes)`, que apagam os que
  ficaram sem nenhum livro. Com a lista vazia, o método correspondente não é chamado. Os dois métodos são
  `@Transactional`.

## Exemplos

**Hoje = 10/03/2026**; o `Clock` do calendário está fixo em 10/03/2026 às 14:30 de Brasília.
Usuário Ana (id 1): estantes "Minha estante" (id 10, padrão) e "Quarto" (id 11).
Usuário Bruno (id 2): estante "Minha estante" (id 20, padrão).
Nos exemplos de `adicionar`, `status` e `paginaAtual` são `null`, a não ser quando estiver escrito.

**Adicionar**

- **E1 (L4, L5, L6).** Quando chamo `adicionar(1L, livro "  Memórias do Subsolo ", null, null, null)`, Então o livro
  salvo tem título "Memórias do Subsolo", está na estante 10, `dataDeAdicao` = 10/03/2026 14:30 (-03:00), não tem
  leitura, e o método retorna o objeto devolvido por `save`.
- **E2 (L4).** Quando chamo `adicionar(1L, livro, 11L, null, null)`, Então o livro salvo está na estante 11.
- **E3 (L4).** Quando chamo `adicionar(1L, livro, 20L, null, null)` (estante do Bruno) ou com `999L`, Então
  `NoSuchElementException` e `save` não é chamado.
- **E4 (L5).** Dado um livro recebido com `id` = 50, um empréstimo na lista e `dataDeAdicao` = 01/01/2020, Quando
  adiciono, Então o livro salvo tem `id` `null`, lista de empréstimos vazia e `dataDeAdicao` = 10/03/2026 14:30.
- **E5 (L2).** Quando adiciono com livro `null`, ou com um livro montado pelo construtor vazio com título `null`,
  `""` ou `"  "`, Então `IllegalArgumentException` e `save` não é chamado.
- **E6 (L3).** Quando adiciono com `paginas` = `null` ou `0`, Então é aceito e `paginas` fica `null`. Com `paginas`
  = -5, Então `IllegalArgumentException`.
- **E7 (L1).** Quando chamo `adicionar(99L, livro, null, null, null)` (usuário inexistente), Então
  `NoSuchElementException`.
- **E8 (L6).** Dado um livro de 200 páginas, Quando chamo `adicionar(1L, livro, null, LENDO, 80)`, Então o livro
  salvo está `LENDO` na página 80 (40%). Com `LENDO, 500`, Então fica `LENDO` na página 0.

**Autores e tags**

- **E9 (L11).** Dado `findFirstByNomeIgnoreCase("fiódor dostoiévski")` = autor do banco "Fiódor Dostoiévski" (id 7),
  Quando adiciono um livro com o autor "fiódor   dostoiévski" (o `AutorNode` junta os espaços), Então o livro salvo
  tem o autor do banco (id 7).
- **E10 (L11).** Dado `findFirstByNomeIgnoreCase` vazio, Quando adiciono com autores "Ana Maria" e "ana maria", Então
  o livro salvo tem **um** autor novo, "Ana Maria".
- **E11 (L11).** Dado `TagRepository.findById("ficcao")` = tag do banco com `nomeExibicao` "Ficção", Quando adiciono
  com as tags "FICÇÃO" e "ficcao", Então o livro salvo tem uma tag só, a do banco.

**Buscar e listar**

- **E12 (L1).** Dado `LivroRepository.findById(5L)` = vazio, Quando chamo `buscar(5L)`, Então
  `NoSuchElementException`.
- **E13 (L4).** Dado `findAllByEstanteId(11L)` = livros A e B, Quando chamo `listarDaEstante(1L, 11L)`, Então retorna
  A e B. Quando chamo `listarDaEstante(1L, 20L)`, Então `NoSuchElementException`.

**Atualizar** (livro 5 de Ana, 200 páginas, autor "Autor X" (id 7), tag "romance")

- **E14 (L12).** Quando chamo `atualizar(1L, 5L, dados)` só com `titulo = "Novo"`, Então o título muda e autores, tags,
  páginas e leitura não mudam.
- **E15 (L12, L13).** Quando atualizo com `autores = ["Autor Y"]` e `tags = []`, Então o livro salvo tem só o autor
  "Autor Y" e nenhuma tag, e `apagarOrfaos([7])` e `apagarOrfas(["romance"])` são chamados.
- **E16 (L12).** Dado o livro `LENDO` na página 150, Quando atualizo com `paginas = 100`, Então
  `IllegalStateException` e `save` não é chamado. Com `paginas = 0`, Então `paginas` fica `null`.
- **E17 (L12).** Dado o livro com ISBN "123", Quando atualizo com `isbn = ""`, Então o ISBN fica `null`.
- **E18 (L7).** Quando chamo `atualizar(2L, 5L, dados)` (Bruno), Então `NoSuchElementException` e `save` não é
  chamado.

**Mover** (livro 5 de Ana, na estante 10)

- **E19 (L8).** Quando chamo `mover(1L, 5L, 11L)`, Então o livro passa para a estante 11 e é salvo.
- **E20 (L8).** Quando chamo `mover(1L, 5L, 20L)` (estante do Bruno), Então `NoSuchElementException` e nada é salvo.
- **E21 (L7).** Quando chamo `mover(2L, 5L, 20L)` (Bruno tentando mover livro de Ana), Então
  `NoSuchElementException`.
- **E22 (L8).** Dado o livro 5 com empréstimo ativo para Bruno, Quando chamo `mover(1L, 5L, 11L)`, Então é aceito.

**Remover** (livro 5 de Ana)

- **E23 (L9, L13).** Dado o livro 5 sem empréstimos, com o autor id 7 e a tag "romance", Quando chamo
  `remover(1L, 5L)`, Então `delete` é chamado com o livro 5, e depois `apagarOrfaos([7])` e `apagarOrfas(["romance"])`.
- **E24 (L9).** Dado o livro 5 com empréstimo ativo, Quando chamo `remover(1L, 5L)`, Então `IllegalStateException` e
  `delete` não é chamado.
- **E25 (L9).** Dado o livro 5 só com empréstimos devolvidos, Quando chamo `remover(1L, 5L)`, Então `delete` é
  chamado.
- **E26 (L7).** Quando chamo `remover(2L, 5L)`, Então `NoSuchElementException` e `delete` não é chamado.

**Leitura** (livro 5 de Ana, com 200 páginas, emprestado para Bruno; usuário Carla, id 3, sem relação com o livro)

- **E27 (L10).** Quando chamo `atualizarLeitura(1L, 5L, LENDO, 80)`, Então a leitura **do livro** fica `LENDO` na
  página 80 (40%), a do empréstimo não muda, e o livro é salvo.
- **E28 (L10).** Quando chamo `atualizarLeitura(2L, 5L, LIDO, null)`, Então a leitura **do empréstimo ativo** fica
  `LIDO` (página 200, 100%, concluído em 10/03/2026), a do livro não muda, e o livro é salvo.
- **E29 (L10).** Quando chamo `atualizarLeitura(3L, 5L, LENDO, 10)`, Então `NoSuchElementException` e nada é salvo.
- **E30 (L10).** Quando chamo `atualizarLeitura(1L, 5L, LENDO, 250)` (passa das 200 páginas), Então a leitura do livro
  fica `LENDO` na página 0, sem exceção, e o livro é salvo.
- **E31 (L10).** Quando chamo `atualizarLeitura(1L, 5L, null, 10)`, Então `IllegalArgumentException` e nada é salvo.

## Casos de borda

- **Livro sem estante informada** vai para a padrão (E1). Não existe livro sem estante.
- **Tentar mexer no livro de outro usuário** responde como se o livro não existisse (`NoSuchElementException`), para
  não revelar dados de outras pessoas (E18, E21, E26).
- **O dono e quem pegou emprestado têm leituras separadas** do mesmo livro (E27, E28).
- **Remover livro com histórico** (empréstimos devolvidos) é permitido e apaga o histórico junto (E25). Isso é perda
  de dados conhecida (TODO no código).
- **Página inválida não dá erro**: vira 0 (E8, E30). Pode mudar no futuro.

## Fora do escopo

- Diferenciar autores homônimos (o mesmo nome é o mesmo autor; TODO no código).
- As queries Cypher (`findDonoDoLivro`, `apagarOrfaos`, `apagarOrfas`, `findFirstByNomeIgnoreCase`): testadas só com
  banco real.
- Validação do formato do ISBN.
- Enriquecimento dos dados do livro pelo agente de IA.
- Empréstimo e devolução (são do `EmprestimoService`).
- Autenticação (por enquanto o `usuarioId` vem da rota).
