classe: br.com.virabrequim.database_service.domain.node.BibliotecaNode

# Spec: BibliotecaNode

## Contexto

A biblioteca **é um usuário** (`BibliotecaNode extends UsuarioNode`): no grafo, o nó tem os labels `Usuario` e
`Biblioteca`. Ela herda nome, email e estante padrão do `UsuarioNode` (ver `UsuarioNode.md`) e acrescenta endereço e
horário de funcionamento.

O endereço fica no próprio nó (logradouro, número, CEP), menos a cidade, que é um nó separado:

```
(Biblioteca)-[:FICA_EM]->(Cidade)-[:PERTENCE_A]->(Estado)
```

O horário é provisoriamente uma `String` no formato `"HH:mm"` (pode virar `LocalTime` no futuro).

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `BibliotecaNode()` | Construtor vazio (usado pelo SDN) | — |
| `BibliotecaNode(String nome, String email)` | Cria uma biblioteca válida | B1 |
| `void setHorarioAbertura(String horario)` / `void setHorarioFechamento(String horario)` | Horários | B2, B3 |
| `String getHorarioAbertura()` / `String getHorarioFechamento()` | Horários | B2, B3 |
| `setLogradouro` / `setNumero` / `setCep` e getters | Endereço (sem validação no node) | B4 |
| `CidadeNode getCidade()` / `void setCidade(CidadeNode cidade)` | Cidade (`FICA_EM`) | B4 |

## Regras

- **B1.** O construtor com nome e email segue todas as regras do `UsuarioNode` (nome e email obrigatórios, email
  normalizado, estante padrão "Minha estante" em `estantes` e em `estantePadrao`) e o objeto é um `UsuarioNode`.
- **B2.** Os horários são opcionais: `null` é aceito e significa "não informado".
- **B3.** Um horário não nulo deve estar no formato `HH:mm`, de `00:00` a `23:59`, depois de remover os espaços nas
  pontas (o valor guardado é sem os espaços). Fora disso, lança `IllegalArgumentException` e o horário anterior é
  mantido.
- **B4.** Logradouro, número, CEP e cidade não são validados no node (a validação de formato é feita no DTO da API).

## Exemplos

- **E1 (B1).** Quando crio `new BibliotecaNode(" Biblioteca Inatel ", "Contato@Inatel.br")`, Então o nome é
  "Biblioteca Inatel", o email "contato@inatel.br", a estante padrão "Minha estante" existe, e o objeto é
  `instanceof UsuarioNode`.
- **E2 (B1).** Quando crio `new BibliotecaNode("  ", "contato@inatel.br")`, Então `IllegalArgumentException`.
- **E3 (B3).** Quando chamo `setHorarioAbertura("08:00")`, `setHorarioAbertura("00:00")` ou
  `setHorarioFechamento("23:59")`, Então o horário é guardado como informado.
- **E4 (B3).** Quando chamo `setHorarioAbertura(" 08:30 ")`, Então o horário guardado é "08:30".
- **E5 (B2).** Quando chamo `setHorarioFechamento(null)`, Então o horário de fechamento é `null`.
- **E6 (B3).** Quando chamo `setHorarioAbertura` com `"24:00"`, `"25:00"`, `"08:60"`, `"8:00"`, `"8h"`, `""` ou
  `"08:00:00"`, Então `IllegalArgumentException`.
- **E7 (B3).** Dado horário de abertura "08:00", Quando chamo `setHorarioAbertura("99:99")`, Então
  `IllegalArgumentException` e o horário continua "08:00".

## Casos de borda

- **Abertura depois do fechamento** (ex.: abre 22:00, fecha 06:00) é aceito: pode ser uma biblioteca que vira a noite.

## Fora do escopo

- Validação de CEP, número e logradouro (feita no DTO `Endereco`).
- Regras da cidade e do estado (ver `CidadeNode.md` e `EstadoNode.md`).
- "Está aberta agora?" (precisaria de horário como `LocalTime` e do fuso de Brasília).
