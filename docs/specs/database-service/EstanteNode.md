classe: br.com.virabrequim.database_service.domain.node.EstanteNode

# Spec: EstanteNode

## Contexto

Representa uma estante de um usuário (ex.: "Quarto", "Sala"). A regra do **nome** fica no próprio node, no mesmo
padrão do `UsuarioNode`: toda estante criada pelo construtor com nome já nasce válida, e o `setNome` aplica a mesma
regra na atualização. A unicidade do nome entre as estantes do usuário depende das outras estantes e fica no
`EstanteService`.

```
(Usuario)-[:HAS]->(Estante)<-[:STORED_IN]-(Livro)
```

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `EstanteNode()` | Construtor vazio (usado pelo SDN) | — |
| `EstanteNode(String nome)` | Cria uma estante sem id (o id é gerado pelo banco) | EN1, EN2 |
| `EstanteNode(Long id, String nome)` | Construtor com todos os campos (usado em testes) | EN3 |
| `void setNome(String nome)` | Altera o nome | EN1 |
| `Long getId()` / `String getNome()` | Dados da estante | EN1–EN3 |

## Regras

- **EN1.** O nome é obrigatório. `null`, vazio ou só com espaços lança `IllegalArgumentException`. Os espaços nas
  pontas são removidos. Vale no construtor `EstanteNode(String nome)` e no `setNome`. Com nome inválido, o
  `setNome` não altera o nome atual.
- **EN2.** A estante criada por `EstanteNode(String nome)` não tem id (`getId()` = `null`).
- **EN3.** O construtor `EstanteNode(Long id, String nome)` só atribui os campos, sem validar (é o construtor gerado
  pelo Lombok, usado para montar dados nos testes).

## Exemplos

- **E1 (EN1, EN2).** Quando crio `new EstanteNode("  Quarto ")`, Então o nome é "Quarto" e o id é `null`.
- **E2 (EN1).** Quando crio `new EstanteNode(null)`, `new EstanteNode("")` ou `new EstanteNode("   ")`, Então
  `IllegalArgumentException`.
- **E3 (EN1).** Dada a estante "Quarto", Quando chamo `setNome(" Sala ")`, Então o nome é "Sala".
- **E4 (EN1).** Dada a estante "Quarto", Quando chamo `setNome(null)` ou `setNome("  ")`, Então
  `IllegalArgumentException` e o nome continua "Quarto".
- **E5 (EN1).** Maiúsculas são mantidas: `new EstanteNode("QUARTO")` tem o nome "QUARTO".
- **E6 (EN3).** `new EstanteNode(10L, "Minha estante")` tem id 10 e nome "Minha estante".

## Fora do escopo

- Nome único entre as estantes do usuário (fica no `EstanteService`).
- Tamanho máximo do nome (validado no DTO da API, até 50 caracteres).
