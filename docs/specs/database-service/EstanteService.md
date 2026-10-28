classe: br.com.virabrequim.database_service.service.EstanteService

# Spec: EstanteService

## Contexto

Gerencia as estantes de um usuário. Um usuário pode ter várias estantes (ex.: "Quarto", "Sala", "Escritório") e
sempre tem uma **estante padrão**, criada junto com ele (ver `UsuarioService`).

```
(Usuario)-[:HAS]->(Estante)<-[:STORED_IN]-(Livro)
(Usuario)-[:ESTANTE_PADRAO]->(Estante)   ← a padrão também tem HAS
```

Uma estante **é do usuário** quando está na lista `estantes` dele (comparando pelo `id`). A padrão é a que está em
`estantePadrao` (comparando pelo `id`), nunca identificada pelo nome.

A estante virtual "Emprestados comigo" (livros que o usuário pegou emprestado) **não é uma estante** no grafo e não
aparece neste service. Ela é montada na API a partir do `EmprestimoService`.

Nos testes, os repositórios são mocks (Mockito).

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `EstanteService(UsuarioRepository usuarioRepository, EstanteRepository estanteRepository, LivroRepository livroRepository)` | Injeção pelo construtor | — |
| `List<EstanteNode> listar(Long usuarioId)` | Estantes do usuário | S1, S2 |
| `EstanteNode criar(Long usuarioId, String nome)` | Nova estante | S1, S3–S5 |
| `EstanteNode renomear(Long usuarioId, Long estanteId, String novoNome)` | Renomeia | S1, S3, S4, S6, S7 |
| `void apagar(Long usuarioId, Long estanteId)` | Apaga e move os livros para a padrão | S1, S6, S8–S10 |

Colaboradores usados (mockados nos testes):

| Método | Retorno |
| --- | --- |
| `UsuarioRepository.findById(Long id)` | `Optional<UsuarioNode>` |
| `UsuarioRepository.save(UsuarioNode usuario)` | o usuário salvo |
| `EstanteRepository.delete(EstanteNode estante)` | `void` |
| `LivroRepository.findAllByEstanteId(Long estanteId)` | `List<LivroNode>` |
| `LivroRepository.saveAll(Iterable<LivroNode> livros)` | os livros salvos |

## Regras

- **S1.** Em todos os métodos, se o usuário não existir (`findById` vazio), lança `NoSuchElementException`.
- **S2.** `listar` retorna todas as estantes do usuário (a lista `estantes`), incluindo a padrão.
- **S3.** O nome é obrigatório. `null`, vazio ou só com espaços lança `IllegalArgumentException`. Os espaços nas
  pontas são removidos antes de salvar.
- **S4.** O nome é único entre as estantes **do mesmo usuário**, sem diferenciar maiúsculas de minúsculas
  ("Quarto" = "quarto"). Nome repetido lança `IllegalStateException`. Ao renomear, manter o próprio nome (mesmo
  mudando maiúsculas/minúsculas) é permitido. Usuários diferentes podem ter estantes com o mesmo nome.
- **S5.** `criar` adiciona a nova estante à lista `estantes`, salva o usuário e retorna a estante criada. A nova
  estante **não** é a padrão.
- **S6.** Se a estante não for do usuário (não existe, ou é de outro usuário), lança `NoSuchElementException`.
- **S7.** A estante padrão pode ser renomeada e continua sendo a padrão.
- **S8.** A estante padrão não pode ser apagada: lança `IllegalStateException` e nada é alterado
  (`delete`, `saveAll` e `save` não são chamados).
- **S9.** Ao apagar uma estante, **todos** os livros dela (`findAllByEstanteId`) passam para a estante padrão e são
  salvos (`saveAll`) **antes** da estante ser apagada.
- **S10.** A estante apagada sai da lista `estantes` do usuário, o usuário é salvo e a estante é apagada com
  `EstanteRepository.delete`.

## Exemplos

Usuário Ana (id 1) com as estantes: "Minha estante" (id 10, padrão), "Quarto" (id 11) e "Sala" (id 12).
Usuário Bruno (id 2) com "Minha estante" (id 20, padrão) e "Quarto" (id 21).

- **E1 (S2).** Quando chamo `listar(1L)`, Então retorna as estantes 10, 11 e 12.
- **E2 (S1).** Dado `findById(99L)` = vazio, Quando chamo `listar(99L)`, `criar(99L, "X")`,
  `renomear(99L, 11L, "X")` ou `apagar(99L, 11L)`, Então `NoSuchElementException`.
- **E3 (S3, S5).** Quando chamo `criar(1L, "  Escritório ")`, Então Ana passa a ter 4 estantes, a nova se chama
  "Escritório", Ana é salva, a estante retornada é a nova, e `estantePadrao` continua sendo a 10.
- **E4 (S3).** Quando chamo `criar(1L, null)`, `criar(1L, "")` ou `criar(1L, "  ")`, Então
  `IllegalArgumentException`.
- **E5 (S4).** Quando chamo `criar(1L, "QUARTO")`, Então `IllegalStateException` e Ana não é salva.
- **E6 (S4).** Quando chamo `criar(2L, "Sala")`, Então a estante é criada (Ana tem "Sala", mas Bruno não).
- **E7 (S4, S6).** Quando chamo `renomear(1L, 11L, "Sala")`, Então `IllegalStateException`.
- **E8 (S4).** Quando chamo `renomear(1L, 11L, "quarto")`, Então a estante 11 passa a se chamar "quarto".
- **E9 (S6).** Quando chamo `renomear(1L, 21L, "X")` (estante do Bruno) ou `renomear(1L, 999L, "X")`, Então
  `NoSuchElementException`.
- **E10 (S7).** Quando chamo `renomear(1L, 10L, "Estante principal")`, Então a estante 10 se chama "Estante principal"
  e continua em `estantePadrao`.
- **E11 (S8).** Quando chamo `apagar(1L, 10L)`, Então `IllegalStateException`, e `delete`, `saveAll` e `save` não são
  chamados.
- **E12 (S9, S10).** Dado `findAllByEstanteId(11L)` = livros A e B, Quando chamo `apagar(1L, 11L)`, Então A e B ficam
  na estante 10, `saveAll` é chamado com A e B antes de `delete`, a estante 11 sai da lista de Ana, Ana é salva e
  `delete` é chamado com a estante 11.
- **E13 (S9, S10).** Dado `findAllByEstanteId(12L)` = lista vazia, Quando chamo `apagar(1L, 12L)`, Então a estante 12
  é apagada normalmente.
- **E14 (S6).** Quando chamo `apagar(1L, 21L)` (estante do Bruno), Então `NoSuchElementException` e nada é apagado.

## Casos de borda

- **Renomear a padrão** é permitido (E10), porque a padrão é marcada por relacionamento, não pelo nome.
- **Criar uma estante chamada "Minha estante"** quando a padrão já se chama assim é nome repetido (S4).
- **Apagar estante vazia** funciona normalmente (E13).
- **Livros emprestados** também são movidos ao apagar a estante: o `STORED_IN` é a "casa" do livro, mesmo quando ele
  está com outra pessoa.

## Fora do escopo

- A estante virtual "Emprestados comigo" (é da API, montada com o `EmprestimoService`).
- Ordem das estantes na listagem.
- Escolher outra estante como padrão.
- Contagem de livros por estante.
- Autenticação (por enquanto o `usuarioId` vem da rota).
