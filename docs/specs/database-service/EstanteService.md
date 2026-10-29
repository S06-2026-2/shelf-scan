classe: br.com.virabrequim.database_service.service.EstanteService

# Spec: EstanteService

## Contexto

Gerencia as estantes de um usuário (comum ou biblioteca). Um usuário pode ter várias estantes (ex.: "Quarto", "Sala",
"Escritório") e sempre tem uma **estante padrão**, criada junto com ele (ver `UsuarioNode.md`).

```
(Usuario)-[:HAS]->(Estante)<-[:STORED_IN]-(Livro)
(Usuario)-[:ESTANTE_PADRAO]->(Estante)   ← a padrão também tem HAS
```

Uma estante **é do usuário** quando está na lista `estantes` dele (comparando pelo `id`). A padrão é a que está em
`estantePadrao` (comparando pelo `id`), nunca identificada pelo nome.

A regra do nome (obrigatório, sem espaços nas pontas) fica no `EstanteNode` (ver `EstanteNode.md`): este service a usa
pelo construtor e pelo `setNome`. Aqui fica o que depende das outras estantes do usuário: o nome único.

As estantes são salvas **pelo usuário**: é o `save` do usuário que cria e remove o relacionamento `HAS`.

A estante virtual "Emprestados comigo" (livros que o usuário pegou emprestado) **não é uma estante** no grafo e não
aparece neste service. Ela é montada na API a partir do `EmprestimoService`.

Nos testes, os repositórios são mocks (Mockito).

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `EstanteService(UsuarioRepository usuarioRepository, EstanteRepository estanteRepository, LivroRepository livroRepository)` | Injeção pelo construtor | — |
| `List<EstanteNode> listar(Long usuarioId)` | Estantes do usuário | S1, S2 |
| `EstanteNode criar(Long usuarioId, String nome)` | Nova estante | S1, S3–S5 |
| `EstanteNode atualizar(Long usuarioId, Long estanteId, String nome)` | Atualização parcial (PATCH) | S1, S3, S4, S6, S7 |
| `void apagar(Long usuarioId, Long estanteId, DestinoLivros destino)` | Apaga a estante, movendo ou apagando os livros | S1, S6, S8–S11 |

`DestinoLivros` é um enum em `br.com.virabrequim.database_service.domain` com os valores `MOVER` e `APAGAR`.

Colaboradores usados (mockados nos testes):

| Método | Retorno |
| --- | --- |
| `UsuarioRepository.findById(Long id)` | `Optional<UsuarioNode>` |
| `UsuarioRepository.save(UsuarioNode usuario)` | o usuário salvo |
| `EstanteRepository.delete(EstanteNode estante)` | `void` |
| `LivroRepository.findAllByEstanteId(Long estanteId)` | `List<LivroNode>` |
| `LivroRepository.findIdsComEmprestimoAtivoByEstanteId(Long estanteId)` | `List<Long>` (ids dos livros da estante com empréstimo ativo) |
| `LivroRepository.saveAll(Iterable<LivroNode> livros)` | os livros salvos |
| `LivroRepository.deleteAll(Iterable<LivroNode> livros)` | `void` |

Entidades:
- `EstanteNode(String nome)`: valida o nome e remove os espaços nas pontas; `setNome` faz o mesmo (ver
  `EstanteNode.md`).
- `UsuarioNode`: `List<EstanteNode> estantes` e `EstanteNode estantePadrao`.
- `LivroNode`: `getId()` e `setEstante(EstanteNode estante)`.

## Regras

- **S1.** Em todos os métodos, se o usuário não existir (`findById` vazio), lança `NoSuchElementException`.
- **S2.** `listar` retorna todas as estantes do usuário (a lista `estantes`), incluindo a padrão.
- **S3.** O nome é validado pelo `EstanteNode`: `null`, vazio ou só com espaços lança `IllegalArgumentException`, e os
  espaços nas pontas são removidos. Com nome inválido, o usuário não é salvo.
- **S4.** O nome é único entre as estantes **do mesmo usuário**, sem diferenciar maiúsculas de minúsculas
  ("Quarto" = "quarto"), comparando o nome já sem os espaços nas pontas. Nome repetido lança `IllegalStateException`
  e o usuário não é salvo. Ao atualizar, a própria estante não conta: manter o nome (mesmo mudando
  maiúsculas/minúsculas) é permitido. Usuários diferentes podem ter estantes com o mesmo nome.
- **S5.** `criar` adiciona a nova estante à lista `estantes`, salva o usuário e retorna a estante criada (a instância
  que está na lista). A nova estante **não** é a padrão.
- **S6.** Se a estante não for do usuário (não existe, ou é de outro usuário), lança `NoSuchElementException` e nada é
  salvo nem apagado.
- **S7.** `atualizar` é parcial: `nome` `null` = não mexer no nome (o usuário é salvo sem mudanças). Com nome não nulo,
  segue S3 e S4. A estante padrão pode ser atualizada e continua sendo a padrão. Retorna a estante atualizada.
- **S8.** A estante padrão não pode ser apagada: lança `IllegalStateException` e nada é alterado (`saveAll`,
  `deleteAll`, `save` e `delete` não são chamados).
- **S9.** Com `destino = MOVER`, **todos** os livros da estante (`findAllByEstanteId`) passam para a estante padrão
  (`setEstante(padrão)`) e são salvos com `saveAll`. Nenhum livro é apagado.
- **S10.** Com `destino = APAGAR`, os livros da estante **com empréstimo ativo** (ids em
  `findIdsComEmprestimoAtivoByEstanteId`) passam para a estante padrão e são salvos com `saveAll`; os **outros** são
  apagados com `deleteAll`. Livro com empréstimo já devolvido não conta como emprestado.
- **S11.** Em qualquer destino, os livros são movidos (`saveAll`) e apagados (`deleteAll`) **antes** de a estante ser
  apagada. Depois, a estante sai da lista `estantes` do usuário, o usuário é salvo e a estante é apagada com
  `EstanteRepository.delete`. O método é `@Transactional`.

## Exemplos

Usuário Ana (id 1) com as estantes: "Minha estante" (id 10, padrão), "Quarto" (id 11) e "Sala" (id 12).
Usuário Bruno (id 2) com "Minha estante" (id 20, padrão) e "Quarto" (id 21).

- **E1 (S2).** Quando chamo `listar(1L)`, Então retorna as estantes 10, 11 e 12.
- **E2 (S1).** Dado `findById(99L)` = vazio, Quando chamo `listar(99L)`, `criar(99L, "X")`,
  `atualizar(99L, 11L, "X")` ou `apagar(99L, 11L, MOVER)`, Então `NoSuchElementException`.
- **E3 (S3, S5).** Quando chamo `criar(1L, "  Escritório ")`, Então Ana passa a ter 4 estantes, a nova se chama
  "Escritório", Ana é salva, a estante retornada é a nova (a mesma instância da lista), e `estantePadrao` continua
  sendo a 10.
- **E4 (S3).** Quando chamo `criar(1L, null)`, `criar(1L, "")` ou `criar(1L, "  ")`, Então
  `IllegalArgumentException` e Ana não é salva.
- **E5 (S4).** Quando chamo `criar(1L, "QUARTO")` ou `criar(1L, "  quarto ")`, Então `IllegalStateException` e Ana
  não é salva.
- **E6 (S4).** Quando chamo `criar(2L, "Sala")`, Então a estante é criada (Ana tem "Sala", mas Bruno não).
- **E7 (S4).** Quando chamo `atualizar(1L, 11L, "Sala")`, Então `IllegalStateException` e Ana não é salva.
- **E8 (S4, S7).** Quando chamo `atualizar(1L, 11L, "quarto")`, Então a estante 11 passa a se chamar "quarto".
- **E9 (S6).** Quando chamo `atualizar(1L, 21L, "X")` (estante do Bruno) ou `atualizar(1L, 999L, "X")`, Então
  `NoSuchElementException`.
- **E10 (S7).** Quando chamo `atualizar(1L, 10L, "Estante principal")`, Então a estante 10 se chama
  "Estante principal" e continua em `estantePadrao`.
- **E11 (S7).** Quando chamo `atualizar(1L, 11L, null)`, Então o nome continua "Quarto", Ana é salva e a estante 11
  é retornada.
- **E12 (S3, S7).** Quando chamo `atualizar(1L, 11L, "   ")`, Então `IllegalArgumentException`, o nome continua
  "Quarto" e Ana não é salva.
- **E13 (S8).** Quando chamo `apagar(1L, 10L, MOVER)` ou `apagar(1L, 10L, APAGAR)`, Então `IllegalStateException`, e
  `saveAll`, `deleteAll`, `save` e `delete` não são chamados.
- **E14 (S9, S11).** Dado `findAllByEstanteId(11L)` = livros A e B, Quando chamo `apagar(1L, 11L, MOVER)`, Então A e B
  ficam na estante 10, `saveAll` é chamado com A e B **antes** de `delete`, nenhum livro é apagado, a estante 11 sai
  da lista de Ana, Ana é salva e `delete` é chamado com a estante 11.
- **E15 (S10, S11).** Dado `findAllByEstanteId(11L)` = livros A (id 100) e B (id 101) e
  `findIdsComEmprestimoAtivoByEstanteId(11L)` = [101], Quando chamo `apagar(1L, 11L, APAGAR)`, Então B fica na estante
  10 e é salvo com `saveAll`, A é apagado com `deleteAll`, ambos **antes** de `delete`, e a estante 11 é apagada.
- **E16 (S10).** Dado `findAllByEstanteId(11L)` = livros A e B e `findIdsComEmprestimoAtivoByEstanteId(11L)` = lista
  vazia, Quando chamo `apagar(1L, 11L, APAGAR)`, Então A e B são apagados e nenhum livro vai para a estante 10.
- **E17 (S9, S11).** Dado `findAllByEstanteId(12L)` = lista vazia, Quando chamo `apagar(1L, 12L, MOVER)`, Então a
  estante 12 é apagada normalmente.
- **E18 (S6).** Quando chamo `apagar(1L, 21L, MOVER)` (estante do Bruno) ou `apagar(1L, 999L, APAGAR)`, Então
  `NoSuchElementException` e nada é apagado.

## Casos de borda

- **Atualizar a padrão** é permitido (E10), porque a padrão é marcada por relacionamento, não pelo nome.
- **Criar uma estante chamada "Minha estante"** quando a padrão já se chama assim é nome repetido (S4).
- **Apagar estante vazia** funciona normalmente (E17).
- **Livros emprestados nunca são apagados com a estante**: no destino `MOVER` vão para a padrão com os outros; no
  `APAGAR` vão para a padrão enquanto os outros são apagados. O `STORED_IN` é a "casa" do livro, mesmo quando ele está
  com outra pessoa, e o dono continua existindo.

## Fora do escopo

- A estante virtual "Emprestados comigo" (é da API, montada com o `EmprestimoService`).
- A conversão do parâmetro da API (`?livros=mover|apagar`, padrão `mover`) para `DestinoLivros` (feita no
  `WebConfig`/controller).
- Ordem das estantes na listagem.
- Escolher outra estante como padrão.
- Contagem de livros por estante.
- Autenticação (por enquanto o `usuarioId` vem da rota).
