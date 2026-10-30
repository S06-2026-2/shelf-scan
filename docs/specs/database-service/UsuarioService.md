classe: br.com.virabrequim.database_service.service.UsuarioService

# Spec: UsuarioService

## Contexto

CRUD do **usuário comum** e operações que valem para **qualquer usuário** (busca usada pelas rotas de estante, livro e
empréstimo, e o dono de um livro). O CRUD da biblioteca fica no `BibliotecaService`.

Existem dois tipos de usuário: o usuário comum (`UsuarioNode`, label `Usuario`) e a biblioteca
(`BibliotecaNode extends UsuarioNode`, labels `Usuario` e `Biblioteca`). O `UsuarioRepository.findById` acha os dois e
devolve a biblioteca já como `BibliotecaNode`.

As regras de nome, email (obrigatórios e email normalizado) e estante padrão ficam no `UsuarioNode` (ver
`UsuarioNode.md`): este service só as usa através do construtor e dos setters. Aqui fica o que precisa do banco: o
**email é único entre usuários comuns e bibliotecas**, verificado com `existsByEmail` (o banco também tem uma
constraint de unicidade, que é a garantia final).

Nos testes, os repositórios são mocks (Mockito). Salvar o usuário salva junto as estantes ligadas a ele.

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `UsuarioService(UsuarioRepository usuarioRepository)` | Injeção pelo construtor | — |
| `UsuarioNode criar(String nome, String email)` | Cria um usuário comum | U1–U3 |
| `UsuarioNode buscar(Long id)` | Busca qualquer usuário (comum ou biblioteca) | U4 |
| `UsuarioNode buscarUsuarioComum(Long id)` | Busca só usuário comum (CRUD) | U4, U5 |
| `UsuarioNode atualizar(Long id, String nome, String email)` | Atualização parcial (PATCH) | U5–U8 |
| `void apagar(Long id)` | Apaga o usuário comum e o que é dele | U5, U9 |
| `UsuarioNode buscarDonoDoLivro(Long livroId)` | Dono de um livro (para as respostas da API) | U10 |

Colaboradores usados (mockados nos testes):

| Método | Retorno |
| --- | --- |
| `UsuarioRepository.existsByEmail(String email)` | `boolean` |
| `UsuarioRepository.save(S usuario)` | o usuário salvo |
| `UsuarioRepository.findById(Long id)` | `Optional<UsuarioNode>` |
| `UsuarioRepository.apagarComEstantesELivros(Long usuarioId)` | `void` |
| `UsuarioRepository.findDonoDoLivro(Long livroId)` | `Optional<UsuarioNode>` |

Entidades:
- `UsuarioNode(String nome, String email)`: valida, normaliza o email e cria a estante padrão "Minha estante"
  (ver `UsuarioNode.md`). `setNome` e `setEmail` aplicam as mesmas regras.
- `UsuarioNode.normalizarEmail(String email)`: método estático que valida e retorna o email normalizado.
- `BibliotecaNode extends UsuarioNode`, anotada com `@Node("Biblioteca")`.

## Regras

- **U1.** `criar` monta o usuário pelo construtor `UsuarioNode(nome, email)`. Nome ou email inválido lança
  `IllegalArgumentException` (vinda do node) **antes** de consultar o repositório: `existsByEmail` e `save` não são
  chamados.
- **U2.** O email é único. Se `existsByEmail(emailNormalizado)` retornar `true`, lança `IllegalStateException` e
  `save` não é chamado. A consulta usa o email **já normalizado** (ex.: "ANA@email.com" consulta "ana@email.com").
- **U3.** `criar` salva um `UsuarioNode` (não uma biblioteca), com a estante padrão "Minha estante", e retorna o objeto
  retornado por `save` (é ele que tem o id gerado).
- **U4.** `buscar(id)` retorna o usuário de `findById`, seja ele comum ou biblioteca. Se `findById` retornar vazio,
  lança `NoSuchElementException`.
- **U5.** `buscarUsuarioComum(id)` segue U4, mas se o usuário encontrado for uma `BibliotecaNode` lança
  `NoSuchElementException` com uma mensagem indicando `/biblioteca/{id}`. `atualizar` e `apagar` usam essa busca:
  com id de biblioteca ou inexistente, lançam `NoSuchElementException` e não alteram nada.
- **U6.** `atualizar` é parcial: parâmetro `null` = não mexer no campo. Com nome e email `null`, o usuário é salvo
  sem mudanças e retornado.
- **U7.** Com `nome` não nulo, o nome é alterado por `setNome` (nome em branco lança `IllegalArgumentException` e
  `save` não é chamado).
- **U8.** Com `email` não nulo, o email é normalizado. Se for **diferente** do email atual, segue U2 (repetido lança
  `IllegalStateException` e `save` não é chamado). Se for **igual** ao atual (mesmo depois de normalizar), não é
  conflito: `existsByEmail` não é chamado. `atualizar` retorna o objeto retornado por `save`.
- **U9.** `apagar` chama `apagarComEstantesELivros(id)`, que apaga o usuário, as estantes dele e os livros guardados
  nelas, menos os livros com empréstimo ativo (essa parte é feita pela query do repositório).
- **U10.** `buscarDonoDoLivro(livroId)` retorna o usuário de `findDonoDoLivro(livroId)` e não salva nada. Se vier
  vazio, lança `NoSuchElementException`.

## Exemplos

- **E1 (U1, U2, U3).** Dado `existsByEmail("ana@email.com")` = `false`, Quando chamo
  `criar("  Ana Souza ", "  Ana@Email.COM ")`, Então `save` recebe um `UsuarioNode` (não `BibliotecaNode`) com nome
  "Ana Souza", email "ana@email.com", 1 estante "Minha estante" que é também a `estantePadrao`, e o método retorna o
  objeto retornado por `save`.
- **E2 (U1).** Quando chamo `criar(null, "ana@email.com")`, `criar("", "ana@email.com")` ou
  `criar("  ", "ana@email.com")`, Então `IllegalArgumentException`, e `existsByEmail` e `save` não são chamados.
- **E3 (U1).** Quando chamo `criar("Ana", null)` ou `criar("Ana", "  ")`, Então `IllegalArgumentException` e `save`
  não é chamado.
- **E4 (U2).** Dado `existsByEmail("ana@email.com")` = `true`, Quando chamo `criar("Ana", "ANA@email.com")`, Então
  `IllegalStateException` e `save` não é chamado.
- **E5 (U4).** Dado `findById(1L)` = usuário Ana, Quando chamo `buscar(1L)`, Então retorna Ana.
- **E6 (U4).** Dado `findById(2L)` = biblioteca Inatel (`BibliotecaNode`), Quando chamo `buscar(2L)`, Então retorna
  a biblioteca.
- **E7 (U4).** Dado `findById(99L)` = vazio, Quando chamo `buscar(99L)` ou `buscarUsuarioComum(99L)`, Então
  `NoSuchElementException`.
- **E8 (U5).** Dado `findById(2L)` = biblioteca, Quando chamo `buscarUsuarioComum(2L)`, Então
  `NoSuchElementException` e a mensagem contém "/biblioteca/2".
- **E9 (U5).** Dado `findById(2L)` = biblioteca, Quando chamo `atualizar(2L, "X", null)` ou `apagar(2L)`, Então
  `NoSuchElementException`, e `save` e `apagarComEstantesELivros` não são chamados.
- **E10 (U6, U7).** Dado Ana (id 1, email "ana@email.com"), Quando chamo `atualizar(1L, "  Ana Lima ", null)`, Então
  o nome salvo é "Ana Lima", o email continua "ana@email.com" e `existsByEmail` não é chamado.
- **E11 (U6).** Dado Ana (id 1), Quando chamo `atualizar(1L, null, null)`, Então Ana é salva sem mudanças.
- **E12 (U7).** Dado Ana (id 1), Quando chamo `atualizar(1L, "   ", null)`, Então `IllegalArgumentException` e `save`
  não é chamado.
- **E13 (U8).** Dado Ana (id 1, email "ana@email.com") e `existsByEmail("novo@email.com")` = `false`, Quando chamo
  `atualizar(1L, null, " NOVO@Email.com ")`, Então o email salvo é "novo@email.com".
- **E14 (U8).** Dado Ana (id 1) e `existsByEmail("bruno@email.com")` = `true`, Quando chamo
  `atualizar(1L, null, "bruno@email.com")`, Então `IllegalStateException` e `save` não é chamado.
- **E15 (U8).** Dado Ana (id 1, email "ana@email.com"), Quando chamo `atualizar(1L, null, "ANA@Email.com")`, Então
  não há conflito, `existsByEmail` não é chamado e Ana é salva com "ana@email.com".
- **E16 (U9).** Dado `findById(1L)` = Ana, Quando chamo `apagar(1L)`, Então `apagarComEstantesELivros(1L)` é chamado.
- **E17 (U5, U9).** Dado `findById(99L)` = vazio, Quando chamo `apagar(99L)`, Então `NoSuchElementException` e
  `apagarComEstantesELivros` não é chamado.
- **E18 (U10).** Dado `findDonoDoLivro(5L)` = usuário Ana, Quando chamo `buscarDonoDoLivro(5L)`, Então retorna Ana e
  `save` não é chamado.
- **E19 (U10).** Dado `findDonoDoLivro(99L)` = vazio, Quando chamo `buscarDonoDoLivro(99L)`, Então
  `NoSuchElementException`.

## Casos de borda

- **Email com maiúsculas e espaços** conta como o mesmo email (E1, E4, E15).
- **Usuário comum e biblioteca dividem o mesmo espaço de emails**: a verificação é a mesma, `existsByEmail`.
- **A validação acontece antes de consultar o repositório**: com nome ou email inválido, `existsByEmail` não é
  chamado.
- **`buscar` aceita biblioteca de propósito**: as rotas de estante, livro e empréstimo valem para os dois tipos. Só o
  CRUD de `/usuario` recusa bibliotecas.

## Fora do escopo

- Criar, buscar, atualizar e apagar biblioteca (ver `BibliotecaService.md`).
- O que a query `apagarComEstantesELivros` apaga no banco (testado só com banco real). Livro emprestado de usuário
  apagado fica sem dono, e empréstimo ativo **para** o usuário apagado some (pendências de domínio).
- Condição de corrida no email: coberta pela constraint de unicidade do Neo4j.
- Relações entre usuários (`AMIGO`, `SEGUE`).
- Autenticação e senha.
