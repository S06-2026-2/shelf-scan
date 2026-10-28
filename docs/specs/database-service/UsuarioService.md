classe: br.com.virabrequim.database_service.service.UsuarioService

# Spec: UsuarioService

## Contexto

Cria e busca usuários, e descobre o dono de um livro. Existem dois tipos de usuário: o **usuário comum**
(`UsuarioNode`, label `Usuario`) e a **biblioteca** (`BibliotecaNode extends UsuarioNode`, labels `Usuario` e
`Biblioteca`). As regras de criação valem para os dois.

Todo usuário nasce com uma **estante padrão**. Ela é ligada ao usuário por dois relacionamentos:
`HAS` (como qualquer estante, para aparecer na listagem) e `ESTANTE_PADRAO` (para marcar qual é a padrão).
A estante padrão é identificada por esse relacionamento, nunca pelo nome.

```
(Usuario)-[:HAS]->(Estante "Minha estante")
(Usuario)-[:ESTANTE_PADRAO]->(mesma Estante)
```

Nos testes, os repositórios são mocks (Mockito). Salvar o usuário salva junto as estantes ligadas a ele.

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `UsuarioService(UsuarioRepository usuarioRepository)` | Injeção pelo construtor | — |
| `UsuarioNode criar(String nome, String email)` | Cria um usuário comum | U1–U5 |
| `BibliotecaNode criarBiblioteca(String nome, String email)` | Cria uma biblioteca | U1–U6 |
| `UsuarioNode buscar(Long id)` | Busca pelo id | U7 |
| `UsuarioNode buscarDonoDoLivro(Long livroId)` | Dono de um livro (para as respostas da API) | U8 |

Colaboradores usados (mockados nos testes):

| Método | Retorno |
| --- | --- |
| `UsuarioRepository.existsByEmail(String email)` | `boolean` |
| `UsuarioRepository.save(S usuario)` | o usuário salvo |
| `UsuarioRepository.findById(Long id)` | `Optional<UsuarioNode>` |
| `UsuarioRepository.findDonoDoLivro(Long livroId)` | `Optional<UsuarioNode>` |

Entidades:
- `UsuarioNode`: `nome`, `email`, `List<EstanteNode> estantes` (`HAS`, começa vazia, nunca `null`) e
  `EstanteNode estantePadrao` (`ESTANTE_PADRAO`).
- `BibliotecaNode extends UsuarioNode`, anotada com `@Node("Biblioteca")`.
- `EstanteNode(String nome)`: construtor que cria a estante sem id (o id é gerado pelo banco).

## Regras

- **U1.** O nome é obrigatório. `null`, vazio ou só com espaços lança `IllegalArgumentException`. Os espaços nas
  pontas são removidos antes de salvar.
- **U2.** O email é obrigatório. `null`, vazio ou só com espaços lança `IllegalArgumentException`. Antes de qualquer
  outra coisa ele é **normalizado**: sem espaços nas pontas e em letras minúsculas. A verificação de U3 e o valor
  salvo usam o email normalizado.
- **U3.** O email é único. Se `existsByEmail(emailNormalizado)` retornar `true`, lança `IllegalStateException` e
  **nada é salvo** (`save` não é chamado).
- **U4.** O usuário salvo tem exatamente uma estante em `estantes`, com o nome "Minha estante", e `estantePadrao` é
  **o mesmo objeto** dessa estante.
- **U5.** O método retorna o objeto retornado por `save` (é ele que tem o id gerado).
- **U6.** `criarBiblioteca` segue U1–U5, e o objeto passado para `save` é um `BibliotecaNode`.
- **U7.** `buscar(id)` retorna o usuário encontrado. Se `findById` retornar vazio, lança `NoSuchElementException`.
- **U8.** `buscarDonoDoLivro(livroId)` retorna o usuário de `findDonoDoLivro(livroId)`. O dono é derivado do caminho
  `Livro -[:STORED_IN]-> Estante <-[:HAS]- Usuario`. Se `findDonoDoLivro` retornar vazio (livro inexistente ou sem
  estante), lança `NoSuchElementException`. Não salva nada.

## Exemplos

- **E1 (U1, U2, U4, U5).** Dado `existsByEmail("ana@email.com")` = `false`, Quando chamo
  `criar("  Ana Souza ", "  Ana@Email.COM ")`, Então `save` recebe um `UsuarioNode` com nome "Ana Souza", email
  "ana@email.com", uma estante "Minha estante" em `estantes` e a mesma estante em `estantePadrao`; e o método retorna
  o objeto devolvido por `save`.
- **E2 (U1).** Quando chamo `criar(null, "ana@email.com")`, `criar("", "ana@email.com")` ou
  `criar("   ", "ana@email.com")`, Então `IllegalArgumentException` e `save` não é chamado.
- **E3 (U2).** Quando chamo `criar("Ana", null)` ou `criar("Ana", "  ")`, Então `IllegalArgumentException` e `save`
  não é chamado.
- **E4 (U2, U3).** Dado `existsByEmail("ana@email.com")` = `true`, Quando chamo `criar("Ana", "ANA@email.com")`,
  Então `IllegalStateException` e `save` não é chamado.
- **E5 (U6).** Dado `existsByEmail("contato@biblioteca.br")` = `false`, Quando chamo
  `criarBiblioteca("Biblioteca Inatel", "contato@biblioteca.br")`, Então `save` recebe um `BibliotecaNode` com a
  estante padrão "Minha estante".
- **E6 (U6, U3).** Dado `existsByEmail("contato@biblioteca.br")` = `true`, Quando chamo
  `criarBiblioteca("Outra", "contato@biblioteca.br")`, Então `IllegalStateException`.
- **E7 (U7).** Dado `findById(1L)` = usuário Ana, Quando chamo `buscar(1L)`, Então retorna Ana.
- **E8 (U7).** Dado `findById(99L)` = vazio, Quando chamo `buscar(99L)`, Então `NoSuchElementException`.
- **E9 (U8).** Dado `findDonoDoLivro(5L)` = usuário Ana, Quando chamo `buscarDonoDoLivro(5L)`, Então retorna Ana e
  `save` não é chamado.
- **E10 (U8).** Dado `findDonoDoLivro(99L)` = vazio, Quando chamo `buscarDonoDoLivro(99L)`, Então
  `NoSuchElementException`.

## Casos de borda

- **Email com maiúsculas e espaços** conta como o mesmo email (E1, E4).
- **Usuário comum e biblioteca dividem o mesmo espaço de emails**: uma biblioteca não pode usar o email de um
  usuário comum e vice-versa (a verificação é a mesma, `existsByEmail`).
- **A validação acontece antes de consultar o repositório**: com nome ou email inválido, `existsByEmail` não precisa
  ser chamado.

## Fora do escopo

- Validação do formato do email (ex.: ter `@`). Isso é feito no DTO da API com `@Email`.
- Condição de corrida (dois cadastros simultâneos com o mesmo email). Será tratada por uma constraint de unicidade
  no Neo4j.
- Atualizar e apagar usuário.
- Relações entre usuários (`AMIGO`, `SEGUE`).
- Autenticação e senha.
