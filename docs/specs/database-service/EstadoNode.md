classe: br.com.virabrequim.database_service.domain.node.EstadoNode

# Spec: EstadoNode

## Contexto

Estado da federação, identificado pelo **código IBGE** de 2 dígitos (ex.: 31 = MG). O código é o `@Id` (chave
natural): salvar um estado que já existe reaproveita o nó em vez de criar um "MG" duplicado.

```
(Cidade)-[:PERTENCE_A]->(Estado)
```

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `EstadoNode()` | Construtor vazio (usado pelo SDN) | — |
| `EstadoNode(Integer codigoIbge, String sigla)` | Cria um estado válido | S1, S2 |
| `Integer getCodigoIbge()` / `String getSigla()` | Dados do estado | S1, S2 |

## Regras

- **S1.** O código IBGE é obrigatório e tem 2 dígitos (de 10 a 99). Fora disso (ou `null`), lança
  `IllegalArgumentException`.
- **S2.** A sigla é obrigatória e tem exatamente 2 letras (depois de remover os espaços nas pontas). É guardada em
  **maiúsculas**. `null`, com número, com 1 ou 3 letras lança `IllegalArgumentException`.

## Exemplos

- **E1 (S1, S2).** Quando crio `new EstadoNode(31, "MG")`, Então o código é 31 e a sigla "MG".
- **E2 (S2).** Quando crio `new EstadoNode(31, " mg ")`, Então a sigla é "MG".
- **E3 (S1).** Quando crio o estado com código `null`, `9` ou `100`, Então `IllegalArgumentException`.
- **E4 (S2).** Quando crio o estado com sigla `null`, `""`, `"M"`, `"MGS"` ou `"M1"`, Então
  `IllegalArgumentException`.

## Fora do escopo

- Conferir se o código e a sigla correspondem de verdade (ex.: 31 é MG) e se o estado existe.
- Nome por extenso do estado.
