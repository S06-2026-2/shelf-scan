classe: br.com.virabrequim.database_service.service.BibliotecaService

# Spec: BibliotecaService

## Contexto

CRUD da **biblioteca** (`BibliotecaNode extends UsuarioNode`, labels `Usuario` e `Biblioteca`). As regras de nome,
email e estante padrão ficam no `UsuarioNode`, e as de horário no `BibliotecaNode`: este service só as usa através dos
construtores e setters. Aqui ficam:

- a **unicidade do email**, que vale entre usuários comuns **e** bibliotecas, por isso é verificada no
  `UsuarioRepository.existsByEmail` (o label `Usuario` está nos dois);
- o **reaproveitamento da cidade e do estado** já cadastrados no banco (*find-or-create*), para não duplicar nós nem
  sobrescrever o nome cadastrado.

```
(Biblioteca)-[:FICA_EM]->(Cidade)-[:PERTENCE_A]->(Estado)
```

O `BibliotecaRepository` só acha nós com label `Biblioteca`: o id de um usuário comum dá "não encontrado".

Nos testes, os repositórios são mocks (Mockito).

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `BibliotecaService(BibliotecaRepository bibliotecaRepository, UsuarioRepository usuarioRepository, CidadeRepository cidadeRepository, EstadoRepository estadoRepository)` | Injeção pelo construtor | — |
| `BibliotecaNode criar(BibliotecaNode novaBiblioteca)` | Salva uma biblioteca já montada | B1–B3 |
| `BibliotecaNode buscar(Long id)` | Busca a biblioteca | B4 |
| `BibliotecaNode atualizar(Long id, AtualizacaoBiblioteca dados)` | Atualização parcial (PATCH) | B3–B8 |
| `void apagar(Long id)` | Apaga a biblioteca e o que é dela | B9 |

Colaboradores usados (mockados nos testes):

| Método | Retorno |
| --- | --- |
| `BibliotecaRepository.save(BibliotecaNode biblioteca)` | a biblioteca salva |
| `BibliotecaRepository.findById(Long id)` | `Optional<BibliotecaNode>` |
| `BibliotecaRepository.existsById(Long id)` | `boolean` |
| `UsuarioRepository.existsByEmail(String email)` | `boolean` |
| `UsuarioRepository.apagarComEstantesELivros(Long usuarioId)` | `void` |
| `CidadeRepository.findById(Integer codigoIbge)` | `Optional<CidadeNode>` |
| `EstadoRepository.findById(Integer codigoIbge)` | `Optional<EstadoNode>` |

Entidades e DTOs:
- `BibliotecaNode(String nome, String email)`: valida e cria a estante padrão (ver `BibliotecaNode.md`). Tem
  `logradouro`, `numero`, `cep`, `horarioAbertura`, `horarioFechamento` e `cidade` (`CidadeNode`).
- `UsuarioNode.normalizarEmail(String email)`: valida e retorna o email normalizado (sem espaços, minúsculas).
- `CidadeNode(Integer codigoIbge, String nome, EstadoNode estado)` e `EstadoNode(Integer codigoIbge, String sigla)`.
- `AtualizacaoBiblioteca(String nome, String email, Endereco endereco, String horarioAbertura,
  String horarioFechamento)`: `record` do pacote `dto`; todos os campos podem ser `null`.
- `Endereco(String logradouro, String numero, String cep, Integer cidadeCodigoIbge, String cidadeNome,
  String estadoSigla)`: `record` do pacote `dto`, com `CidadeNode paraCidade()`, que monta a cidade com o estado
  `EstadoNode(cidadeCodigoIbge / 100000, estadoSigla)`.

## Regras

- **B1.** `criar` recebe a biblioteca já montada (o construtor dela já validou nome, email e horários). Se
  `existsByEmail(email da biblioteca)` retornar `true`, lança `IllegalStateException` e `save` não é chamado.
- **B2.** `criar` retorna o objeto retornado por `bibliotecaRepository.save`.
- **B3.** Cidade: se a biblioteca tem cidade e `cidadeRepository.findById(codigo)` encontra uma, a biblioteca passa a
  usar **a cidade do banco** (com o nome e o estado de lá). Se não encontra, usa a cidade informada; nesse caso, se
  `estadoRepository.findById(codigo do estado)` encontra o estado, a cidade informada passa a usar **o estado do
  banco**. Biblioteca sem cidade (`null`) continua sem cidade e os repositórios de cidade e estado não são consultados.
- **B4.** `buscar(id)` retorna a biblioteca de `bibliotecaRepository.findById`. Se vier vazio (inexistente ou id de
  usuário comum), lança `NoSuchElementException`. `atualizar` usa essa busca.
- **B5.** `atualizar` é parcial: campo `null` em `dados` = não mexer. Com todos `null`, a biblioteca é salva sem
  mudanças e retornada.
- **B6.** Nome e email seguem as mesmas regras do `UsuarioService.atualizar`: nome por `setNome`; email normalizado
  e, se **diferente** do atual, verificado com `existsByEmail` (repetido lança `IllegalStateException` e `save` não é
  chamado); se igual ao atual, `existsByEmail` não é chamado.
- **B7.** Com `endereco` não nulo, o endereço é **substituído inteiro**: logradouro, número e CEP recebem os valores do
  endereço (inclusive `null`), e a cidade vem de `endereco.paraCidade()` passando por B3.
- **B8.** Horários não nulos são alterados pelos setters do `BibliotecaNode` (formato inválido lança
  `IllegalArgumentException`). `atualizar` retorna o objeto retornado por `save`.
- **B9.** `apagar(id)`: se `bibliotecaRepository.existsById(id)` for `false`, lança `NoSuchElementException` e nada é
  apagado. Senão, chama `usuarioRepository.apagarComEstantesELivros(id)` (a mesma deleção do usuário comum: apaga a
  biblioteca, as estantes e os livros, menos os emprestados).

## Exemplos

Cidade Santa Rita do Sapucaí: código 3159605, estado MG (31).

- **E1 (B1, B2).** Dado `existsByEmail("contato@inatel.br")` = `false` e uma biblioteca nova sem cidade, Quando chamo
  `criar(biblioteca)`, Então `save` é chamado com ela e o método retorna o objeto retornado por `save`.
- **E2 (B1).** Dado `existsByEmail("contato@inatel.br")` = `true`, Quando chamo `criar` com uma biblioteca desse
  email, Então `IllegalStateException` e `save` não é chamado.
- **E3 (B3).** Dado `cidadeRepository.findById(3159605)` = cidade do banco com nome "Santa Rita do Sapucaí", Quando
  chamo `criar` com uma biblioteca cuja cidade informada é (3159605, "SRS", MG), Então a biblioteca salva tem a
  **cidade do banco** (nome "Santa Rita do Sapucaí") e `estadoRepository.findById` não é chamado.
- **E4 (B3).** Dado `cidadeRepository.findById(3159605)` = vazio e `estadoRepository.findById(31)` = estado MG do
  banco, Quando chamo `criar` com a cidade (3159605, "Santa Rita do Sapucaí", MG informado), Então a biblioteca salva
  tem a cidade informada, e o estado dela é o **estado do banco**.
- **E5 (B3).** Dado `cidadeRepository.findById(3159605)` = vazio e `estadoRepository.findById(31)` = vazio, Quando
  chamo `criar`, Então a cidade e o estado salvos são os informados.
- **E6 (B4).** Dado `bibliotecaRepository.findById(2L)` = biblioteca Inatel, Quando chamo `buscar(2L)`, Então retorna
  a biblioteca.
- **E7 (B4).** Dado `bibliotecaRepository.findById(1L)` = vazio, Quando chamo `buscar(1L)` ou
  `atualizar(1L, dados)`, Então `NoSuchElementException` e `save` não é chamado.
- **E8 (B5, B8).** Dada a biblioteca Inatel (id 2, fecha "22:00"), Quando chamo `atualizar(2L, dados)` com só
  `horarioFechamento = "18:30"`, Então o fechamento salvo é "18:30" e nome, email, endereço e abertura não mudam.
- **E9 (B6).** Dada a biblioteca (id 2) e `existsByEmail("ana@email.com")` = `true`, Quando chamo `atualizar` com
  `email = "ANA@email.com"`, Então `IllegalStateException` e `save` não é chamado.
- **E10 (B6).** Dada a biblioteca (id 2, email "contato@inatel.br"), Quando chamo `atualizar` com
  `email = "Contato@Inatel.br"`, Então não há conflito e `existsByEmail` não é chamado.
- **E11 (B7).** Dada a biblioteca (id 2, número "510", em Santa Rita), e `cidadeRepository.findById(3152501)` = vazio,
  Quando chamo `atualizar` com o endereço ("Av. Dr. Lisboa", número `null`, "37550-000", 3152501, "Pouso Alegre",
  "MG"), Então o logradouro é "Av. Dr. Lisboa", o número é `null`, o CEP "37550-000" e a cidade é Pouso Alegre.
- **E12 (B8).** Dada a biblioteca (id 2), Quando chamo `atualizar` com `horarioAbertura = "25:00"`, Então
  `IllegalArgumentException` e `save` não é chamado.
- **E13 (B9).** Dado `existsById(2L)` = `true`, Quando chamo `apagar(2L)`, Então
  `apagarComEstantesELivros(2L)` é chamado.
- **E14 (B9).** Dado `existsById(1L)` = `false`, Quando chamo `apagar(1L)`, Então `NoSuchElementException` e
  `apagarComEstantesELivros` não é chamado.

## Casos de borda

- **Usuário comum e biblioteca dividem o mesmo espaço de emails** (E2, E9).
- **Cidade já cadastrada mantém o nome do banco**, mesmo que a requisição mande outro (E3).

## Fora do escopo

- Montar a biblioteca a partir do JSON (feito pelo DTO `NovaBiblioteca.paraBiblioteca()`).
- Validação de formato de CEP, número e código IBGE (DTOs) e das regras de cidade/estado (nodes).
- O que a query `apagarComEstantesELivros` apaga no banco (testado só com banco real).
- Limpar um campo opcional pelo PATCH (ex.: apagar o horário): `null` significa "não mexer".
