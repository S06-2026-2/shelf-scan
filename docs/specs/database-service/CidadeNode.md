classe: br.com.virabrequim.database_service.domain.node.CidadeNode

# Spec: CidadeNode

## Contexto

Cidade onde fica uma biblioteca, identificada pelo **código IBGE** de 7 dígitos (o nome não serve de chave, porque se
repete entre estados). O código é o `@Id` (chave natural, sem `@GeneratedValue`): salvar uma cidade que já existe
reaproveita o nó em vez de duplicar.

Os **2 primeiros dígitos** do código da cidade são o código IBGE do estado (ex.: 3159605, Santa Rita do Sapucaí →
31, MG).

```
(Biblioteca)-[:FICA_EM]->(Cidade)-[:PERTENCE_A]->(Estado)
```

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `CidadeNode()` | Construtor vazio (usado pelo SDN) | — |
| `CidadeNode(Integer codigoIbge, String nome, EstadoNode estado)` | Cria uma cidade válida | C1–C4 |
| `static int codigoDoEstado(int codigoIbgeCidade)` | Código do estado a partir do código da cidade | C5 |
| `Integer getCodigoIbge()` / `String getNome()` / `EstadoNode getEstado()` | Dados da cidade | C1–C4 |

Entidades:
- `EstadoNode(Integer codigoIbge, String sigla)`: estado com código IBGE de 2 dígitos (ver `EstadoNode.md`).

## Regras

- **C1.** O código IBGE é obrigatório e tem 7 dígitos (de 1000000 a 9999999). Fora disso (ou `null`), lança
  `IllegalArgumentException`.
- **C2.** O nome é obrigatório. `null`, vazio ou só com espaços lança `IllegalArgumentException`. Os espaços nas
  pontas são removidos.
- **C3.** O estado é obrigatório e o código dele deve ser igual a `codigoDoEstado(codigoIbge)`. Estado `null` ou de
  outro código lança `IllegalArgumentException`.
- **C4.** As validações acontecem na ordem código, nome, estado.
- **C5.** `codigoDoEstado(codigo)` retorna os 2 primeiros dígitos do código da cidade (divisão inteira por 100000).

## Exemplos

- **E1 (C1–C3).** Dado o estado MG (31), Quando crio `new CidadeNode(3159605, " Santa Rita do Sapucaí ", mg)`, Então
  o código é 3159605, o nome "Santa Rita do Sapucaí" e o estado é MG (a mesma instância).
- **E2 (C1).** Quando crio a cidade com código `null`, `999999` (6 dígitos) ou `10000000` (8 dígitos), Então
  `IllegalArgumentException`.
- **E3 (C2).** Quando crio a cidade com nome `null`, `""` ou `"  "`, Então `IllegalArgumentException`.
- **E4 (C3).** Dado o estado SP (35), Quando crio `new CidadeNode(3159605, "Santa Rita do Sapucaí", sp)`, Então
  `IllegalArgumentException` (a cidade é de MG).
- **E5 (C3).** Quando crio a cidade com estado `null`, Então `IllegalArgumentException`.
- **E6 (C5).** `codigoDoEstado(3159605)` retorna 31; `codigoDoEstado(3550308)` (São Paulo) retorna 35.

## Fora do escopo

- Conferir se o código existe de verdade no IBGE e se o nome corresponde a ele.
- Reaproveitar a cidade já cadastrada no banco (feito no `BibliotecaService`).
