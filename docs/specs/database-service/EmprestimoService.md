classe: br.com.virabrequim.database_service.service.EmprestimoService

# Spec: EmprestimoService

## Contexto

Empresta e devolve livros, mostra o histórico de empréstimos de um livro e lista empréstimos para a estante
virtual "Emprestados comigo" e para o notification-service (avisos de vencimento no Telegram).

```
(Dono)-[:HAS]->(Estante)<-[:STORED_IN]-(Livro)-[:LENT_TO {datas, leitura}]->(Mutuario)
```

O empréstimo é o relacionamento `LENT_TO` (`EmprestimoRelationship`), guardado na lista `emprestimos` do livro. O
dono é derivado do caminho e não muda durante o empréstimo: o `STORED_IN` continua apontando para a estante do dono.
Vale para qualquer par de usuários: biblioteca → cliente, ou amigo → amigo.

Um livro **é do dono** quando `UsuarioRepository.findDonoDoLivro(livroId)` retorna um usuário com o mesmo `id`.

**Hoje** é `calendario.diaHoje()` (data de Brasília). Nos testes, o `Calendario` e os repositórios são mocks
(Mockito). As validações das datas e os estados do empréstimo são do `EmprestimoRelationship` (ver a spec dele);
aqui só se verifica que o service as usa.

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `EmprestimoService(UsuarioRepository usuarioRepository, LivroRepository livroRepository, Calendario calendario)` | Injeção pelo construtor | — |
| `EmprestimoRelationship emprestar(Long donoId, Long livroId, Long mutuarioId, LocalDate dataPrevistaDevolucao)` | Empresta a partir de hoje | M1–M5 |
| `EmprestimoRelationship devolver(Long donoId, Long livroId, LocalDate dataDevolucao)` | Registra a devolução | M1, M6 |
| `List<EmprestimoRelationship> historico(Long donoId, Long livroId)` | Histórico do livro, mais recente primeiro | M1, M10 |
| `List<LivroNode> listarEmprestadosPara(Long usuarioId)` | Estante virtual "Emprestados comigo" | M7 |
| `List<LivroNode> listarAtivosDoUsuario(Long usuarioId)` | Empréstimos ativos do usuário, como dono e como mutuário | M12 |
| `List<LivroNode> listarVencendoDoUsuario(Long usuarioId)` | Os do M12 que estão vencendo | M13 |
| `List<LivroNode> listarVencidosDoUsuario(Long usuarioId)` | Os do M12 que estão vencidos | M13 |
| `List<LivroNode> listarVencendo()` | Para avisos de "vence em breve" (banco todo) | M8, M9 |
| `List<LivroNode> listarVencidos()` | Para avisos de atraso | M8, M9 |

Colaboradores usados (mockados nos testes):

| Método | Retorno |
| --- | --- |
| `UsuarioRepository.findById(Long id)` | `Optional<UsuarioNode>` |
| `UsuarioRepository.findDonoDoLivro(Long livroId)` | `Optional<UsuarioNode>` |
| `LivroRepository.findById(Long id)` | `Optional<LivroNode>` |
| `LivroRepository.save(LivroNode livro)` | o livro salvo |
| `LivroRepository.findAllComEmprestimoAtivo()` | `List<LivroNode>` |
| `LivroRepository.findAllComEmprestimoAtivoPara(Long usuarioId)` | `List<LivroNode>` |
| `LivroRepository.findAllEmprestadosPor(Long usuarioId)` | `List<LivroNode>` (livros do usuário com empréstimo ativo) |
| `Calendario.diaHoje()` | `LocalDate` |

O empréstimo é criado pelo construtor do sistema do `EmprestimoRelationship`, e a cópia da leitura usa
`EmprestimoRelationship.continuarLeitura(EmprestimoRelationship anterior)` (os dois são reais nos testes, não mocks).

## Regras

**Emprestar**

- **M1.** Dono, mutuário ou livro inexistente lança `NoSuchElementException`. Se o livro não for do dono
  (`findDonoDoLivro`), também lança `NoSuchElementException`.
- **M2.** O dono não pode emprestar para si mesmo: mutuário com o mesmo `id` do dono lança
  `IllegalArgumentException`.
- **M3.** Um livro tem no máximo um empréstimo ativo. Se o livro já estiver emprestado, lança
  `IllegalStateException` e nada é salvo.
- **M4.** A data do empréstimo é **hoje**. A data prevista é validada pelo `EmprestimoRelationship`
  (obrigatória e depois de hoje); se for inválida, a `IllegalArgumentException` dele é propagada e nada é salvo.
- **M5.** O empréstimo criado tem como `usuario` o mutuário, está ativo, com a leitura do mutuário em `QUERO_LER` e
  página atual 0. Ele é **acrescentado** à lista `emprestimos` do livro (o histórico continua), o livro é salvo e o
  método retorna o empréstimo criado.
- **M11.** Se o mutuário já pegou **este livro** antes, o novo empréstimo **continua a leitura** do último empréstimo
  dele (o mais recente pela regra de ordem da M10): status, página atual e data de conclusão são copiados
  (`novo.continuarLeitura(anterior)`). Empréstimos de outras pessoas não contam. Se o último dele não tiver leitura
  (status `null`, dado antigo do banco), vale a M5. O empréstimo anterior não muda.

**Devolver**

- **M6.** Só o dono registra a devolução (M1 vale para o dono e o livro). Se o livro não tiver empréstimo ativo, lança
  `IllegalStateException`. `dataDevolucao` `null` significa **hoje**. A devolução é feita por
  `emprestimoAtivo.registrarDevolucao(dataDevolucao, hoje)` (as exceções dele são propagadas e nada é salvo). Depois
  o livro é salvo e o método retorna o empréstimo devolvido.

**Histórico**

- **M10.** `historico(donoId, livroId)` só pode ser visto pelo dono (M1 vale para o dono e o livro: quem pegou
  emprestado recebe `NoSuchElementException`). Retorna **todos** os empréstimos do livro, ativos e devolvidos,
  ordenados por `dataEmprestimo` do mais recente para o mais antigo. Em caso de empate na data, o criado depois vem
  primeiro: maior `id` antes; empréstimo sem `id` (ainda não salvo) antes de todos; com `id` igual (ou os dois
  `null`), o que está mais para o fim da lista `emprestimos` vem primeiro. O Neo4j não guarda a ordem dos
  relacionamentos, por isso a posição na lista sozinha não basta. Livro sem empréstimos retorna
  lista vazia. A lista retornada é uma **lista nova**: a ordem da lista `emprestimos` do livro não muda, e nada é
  salvo.

**Listagens**

- **M7.** `listarEmprestadosPara(usuarioId)` lança `NoSuchElementException` se o usuário não existir; senão retorna o
  resultado de `findAllComEmprestimoAtivoPara(usuarioId)`.
- **M8.** `listarVencendo()` retorna, dentre os livros de `findAllComEmprestimoAtivo()`, os que têm
  `emprestimoAtivo().estaVencendo(hoje)` = `true`. `listarVencidos()` retorna os que têm
  `emprestimoAtivo().isVencido(hoje)` = `true`. Um mesmo livro nunca aparece nas duas listas.
- **M9.** Nas listagens M8, um livro cujo empréstimo lança exceção ao ser verificado (dado inconsistente do banco, ex.:
  sem data prevista) é **ignorado**: não aparece na lista e não interrompe a verificação dos outros.

**Empréstimos do usuário**

- **M12.** `listarAtivosDoUsuario(usuarioId)` lança `NoSuchElementException` se o usuário não existir; senão retorna os
  livros de `findAllEmprestadosPor(usuarioId)` (ele é o dono) **mais** os de `findAllComEmprestimoAtivoPara(usuarioId)`
  (ele pegou emprestado), ordenados pela `dataPrevistaDevolucao` do empréstimo ativo, da mais próxima para a mais
  distante (sem data prevista vai para o fim). Só empréstimos ativos.
- **M13.** `listarVencendoDoUsuario` e `listarVencidosDoUsuario` aplicam a M8 e a M9 sobre a lista da M12 (mesma
  ordem). As versões sem usuário (M8) continuam varrendo o banco todo, para o notification-service.

## Exemplos

**Hoje = 10/03/2026.** Ana (id 1) é dona do livro 5. Bruno (id 2) e Carla (id 3) são outros usuários.

**Emprestar**

- **E1 (M4, M5).** Dado o livro 5 sem empréstimos, Quando chamo `emprestar(1L, 5L, 2L, 24/03/2026)`, Então o livro
  passa a ter 1 empréstimo, ativo, para Bruno, de 10/03/2026 até 24/03/2026, leitura `QUERO_LER` na página 0; o
  livro é salvo e o método retorna esse empréstimo.
- **E2 (M5).** Dado o livro 5 com 2 empréstimos devolvidos, Quando chamo `emprestar(1L, 5L, 3L, 24/03/2026)`, Então o
  livro passa a ter 3 empréstimos (os 2 antigos continuam na lista).
- **E3 (M3).** Dado o livro 5 com empréstimo ativo para Bruno, Quando chamo `emprestar(1L, 5L, 3L, 24/03/2026)`, Então
  `IllegalStateException` e `save` não é chamado.
- **E4 (M2).** Quando chamo `emprestar(1L, 5L, 1L, 24/03/2026)`, Então `IllegalArgumentException`.
- **E5 (M1).** Quando chamo `emprestar(2L, 5L, 3L, 24/03/2026)` (Bruno não é o dono), Então `NoSuchElementException`.
- **E6 (M1).** Dado `findById(99L)` = vazio, Quando chamo `emprestar(1L, 5L, 99L, 24/03/2026)`, Então
  `NoSuchElementException`.
- **E20 (M11).** Dado o livro 5 com `emprestimos` = [Bruno (01/02/2026, devolvido, `LENDO` na página 80),
  Carla (15/02/2026, devolvido, `LIDO`)], Quando chamo `emprestar(1L, 5L, 2L, 24/03/2026)`, Então o novo empréstimo
  para Bruno começa `LENDO` na página 80, e o empréstimo antigo de Bruno continua `LENDO` na página 80.
- **E21 (M11).** No mesmo livro, Quando chamo `emprestar(1L, 5L, 3L, 24/03/2026)`, Então o novo empréstimo para Carla
  começa `LIDO`, com a mesma data de conclusão do anterior dela.
- **E22 (M11, M5).** Dado o livro 5 com 1 empréstimo devolvido de Bruno, Quando chamo `emprestar(1L, 5L, 3L, ...)`
  (Carla nunca pegou o livro), Então o empréstimo de Carla começa `QUERO_LER` na página 0.
- **E7 (M4).** Quando chamo `emprestar(1L, 5L, 2L, null)` ou `emprestar(1L, 5L, 2L, 10/03/2026)`, Então
  `IllegalArgumentException` e `save` não é chamado.

**Devolver** (livro 5 emprestado para Bruno em 01/03/2026, prevista para 12/03/2026)

- **E8 (M6).** Quando chamo `devolver(1L, 5L, null)`, Então o empréstimo fica devolvido em 10/03/2026, o livro é salvo
  e o método retorna o empréstimo.
- **E9 (M6).** Quando chamo `devolver(1L, 5L, 05/03/2026)`, Então fica devolvido em 05/03/2026.
- **E10 (M6).** Quando chamo `devolver(1L, 5L, 11/03/2026)` (amanhã), Então `IllegalArgumentException` e nada é salvo.
- **E11 (M6).** Dado o livro 5 sem empréstimo ativo, Quando chamo `devolver(1L, 5L, null)`, Então
  `IllegalStateException`.
- **E12 (M6, M1).** Quando chamo `devolver(2L, 5L, null)` (Bruno, que pegou emprestado, não é o dono), Então
  `NoSuchElementException`.

**Histórico**

- **E16 (M10).** Dado o livro 5 com `emprestimos` = [X (01/02/2026, devolvido), Y (01/03/2026, ativo),
  W (15/02/2026, devolvido)], Quando chamo `historico(1L, 5L)`, Então retorna [Y, W, X]; a lista `emprestimos` do livro
  continua [X, Y, W] e `save` não é chamado.
- **E17 (M10).** Dado o livro 5 com `emprestimos` = [P (10/03/2026, devolvido no mesmo dia), Q (10/03/2026, ativo)],
  Quando chamo `historico(1L, 5L)`, Então retorna [Q, P].
- **E17b (M10).** Dado o livro 5 com `emprestimos` = [Q (id 31, 10/03/2026), P (id 19, 10/03/2026)] (ordem
  qualquer, como vem do banco), Quando chamo `historico(1L, 5L)`, Então retorna [Q, P].
- **E18 (M10).** Dado o livro 5 sem empréstimos, Quando chamo `historico(1L, 5L)`, Então retorna lista vazia.
- **E19 (M10, M1).** Quando chamo `historico(2L, 5L)` (Bruno não é o dono), Então `NoSuchElementException`. Com o
  livro 99 inexistente, `historico(1L, 99L)` também lança `NoSuchElementException`.

**Listagens**

- **E13 (M7).** Dado `findAllComEmprestimoAtivoPara(2L)` = livros 5 e 6, Quando chamo `listarEmprestadosPara(2L)`,
  Então retorna 5 e 6. Com usuário 99 inexistente, Então `NoSuchElementException`.
- **E14 (M8).** Dado `findAllComEmprestimoAtivo()` = livros com empréstimo de 01/03/2026 e previstas para:
  livro A 12/03/2026 (vence em 2 dias), livro B 30/03/2026 (20 dias) e livro C 08/03/2026 (atrasado), Então
  `listarVencendo()` = [A] e `listarVencidos()` = [C].
- **E15 (M9).** Dado `findAllComEmprestimoAtivo()` = livro A (vencendo) e livro D (empréstimo ativo sem data prevista),
  Então `listarVencendo()` = [A], sem lançar exceção.

**Empréstimos do usuário** (hoje = 10/03/2026)

- **E23 (M12).** Dado `findAllEmprestadosPor(2L)` = [livro B (prevista 24/03/2026)] e
  `findAllComEmprestimoAtivoPara(2L)` = [livro A (prevista 12/03/2026), livro C (prevista 08/03/2026)], Quando chamo
  `listarAtivosDoUsuario(2L)`, Então retorna [C, A, B]. Com usuário 99 inexistente, `NoSuchElementException`.
- **E24 (M13).** Com os mesmos dados, `listarVencendoDoUsuario(2L)` = [A] e `listarVencidosDoUsuario(2L)` = [C].

## Casos de borda

- **Emprestar de novo depois da devolução** é permitido e mantém o histórico (E2).
- **A mesma pessoa pegando o livro de novo** continua a leitura de onde parou (E20, E21); outra pessoa começa do zero (E22).
- **Quem pegou emprestado não registra a devolução**, só o dono (E12).
- **Data prevista igual a hoje** é inválida, porque o prazo mínimo é de 1 dia (E7).
- **Livro de biblioteca e livro de amigo** seguem as mesmas regras.
- **Histórico não depende de hoje**: `historico` não precisa consultar o `Calendario`.

## Fora do escopo

- Empréstimo agendado (data de empréstimo no futuro). Pela API o empréstimo sempre começa hoje.
- Renovar o prazo de um empréstimo.
- Emprestar só para amigos/clientes (depende das relações `AMIGO`/`SEGUE`, que ainda não existem).
- Enviar as notificações (é do notification-service; aqui só se listam os livros).
- As consultas Cypher dos repositórios (testadas em teste de integração, não aqui).
- Autenticação (por enquanto `donoId`/`usuarioId` vêm da rota).
