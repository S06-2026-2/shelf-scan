classe: br.com.virabrequim.database_service.service.AmizadeService

# Spec: AmizadeService

## Contexto

Pedidos de amizade e amizades entre **usuários comuns** (biblioteca não tem amigos, tem membros: ver
`MembroService.md`). A amizade é o que permite emprestar livros entre usuários comuns (ver `EmprestimoService.md`).

```
(Usuario)-[:SOLICITOU_AMIZADE {em}]->(Usuario)   pedido pendente: a direção diz quem pediu
(Usuario)-[:AMIGO {desde}]-(Usuario)             amizade: vale para os dois lados
```

Os relacionamentos não são campos do `UsuarioNode`: só são lidos e gravados pelo `AmizadeRepository` (Cypher).
**Hoje** é `calendario.diaHoje()`. Nos testes, o repositório, o `UsuarioService` e o `Calendario` são mocks (Mockito).

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `AmizadeService(AmizadeRepository amizadeRepository, UsuarioService usuarioService, Calendario calendario)` | Injeção pelo construtor | — |
| `AmizadeResposta solicitar(Long usuarioId, Long destinatarioId)` | Pede amizade | Z1–Z5 |
| `AmizadeResposta aceitar(Long usuarioId, Long solicitanteId)` | Aceita um pedido recebido | Z1, Z6 |
| `void apagarSolicitacao(Long usuarioId, Long outroId)` | Recusa (recebido) ou cancela (enviado) | Z1, Z7 |
| `List<SolicitacaoAmizade> listarRecebidas(Long usuarioId)` | Pedidos recebidos | Z1, Z8 |
| `List<SolicitacaoAmizade> listarEnviadas(Long usuarioId)` | Pedidos enviados | Z1, Z8 |
| `List<Amigo> listarAmigos(Long usuarioId)` | Amigos | Z1, Z8 |
| `void desfazer(Long usuarioId, Long amigoId)` | Desfaz a amizade | Z1, Z9 |

Colaboradores usados (mockados nos testes):

| Método | Retorno |
| --- | --- |
| `UsuarioService.buscarUsuarioComum(Long id)` | `UsuarioNode`; lança `NoSuchElementException` se não existir ou for biblioteca |
| `AmizadeRepository.saoAmigos(Long usuarioId, Long outroId)` | `boolean` |
| `AmizadeRepository.existeSolicitacao(Long solicitanteId, Long destinatarioId)` | `boolean` (só nesse sentido) |
| `AmizadeRepository.criarSolicitacao(Long solicitanteId, Long destinatarioId, LocalDate em)` | `void` |
| `AmizadeRepository.trocarSolicitacaoPorAmizade(Long usuarioId, Long outroId, LocalDate desde)` | `boolean`: `true` se havia pedido (em qualquer sentido) e virou amizade |
| `AmizadeRepository.apagarSolicitacao(Long usuarioId, Long outroId)` | `boolean`: se apagou algo |
| `AmizadeRepository.apagarAmizade(Long usuarioId, Long outroId)` | `boolean`: se apagou algo |
| `AmizadeRepository.listarRecebidas / listarEnviadas / listarAmigos(Long usuarioId)` | listas |
| `Calendario.diaHoje()` | `LocalDate` |

`AmizadeResposta` é um record `(EstadoAmizade estado, Referencia usuario)`, em que `usuario` é a **outra** pessoa (id e
nome). `EstadoAmizade` é `PENDENTE` ou `AMIGOS`.

## Regras

- **Z1.** O usuário da ação (`usuarioId`) e, em `solicitar` e `aceitar`, a outra pessoa são buscados com
  `buscarUsuarioComum`. A `NoSuchElementException` dele é propagada e nada é gravado.
- **Z2.** Pedir amizade para si mesmo lança `IllegalArgumentException` e nada é gravado.
- **Z3.** Se já são amigos, ou se o usuário já enviou um pedido para essa pessoa, `solicitar` lança
  `IllegalStateException` e nada é gravado.
- **Z4.** Se a outra pessoa já tinha pedido, a amizade é criada **na hora**: `trocarSolicitacaoPorAmizade(usuarioId,
  destinatarioId, hoje)` retorna `true`, a resposta tem estado `AMIGOS` e `criarSolicitacao` não é chamado.
- **Z5.** Senão (`trocarSolicitacaoPorAmizade` = `false`), `criarSolicitacao(usuarioId, destinatarioId, hoje)` é
  chamado e a resposta tem estado `PENDENTE`.
- **Z6.** Só quem recebeu aceita: sem `existeSolicitacao(solicitanteId, usuarioId)`, `aceitar` lança
  `NoSuchElementException` e nada é gravado. Com o pedido, chama `trocarSolicitacaoPorAmizade(usuarioId, solicitanteId,
  hoje)` e responde `AMIGOS` com o solicitante.
- **Z7.** `apagarSolicitacao` vale para os dois lados do pedido. Se o repositório não apagou nada (`false`), lança
  `NoSuchElementException`.
- **Z8.** As listagens devolvem o resultado do repositório.
- **Z9.** Qualquer um dos dois desfaz a amizade. Se não eram amigos (`apagarAmizade` = `false`), lança
  `NoSuchElementException`.

## Exemplos

**Hoje = 10/03/2026.** Ana (id 1) e Bruno (id 2) são usuários comuns; Inatel (id 4) é biblioteca.

- **E1 (Z5).** Dados sem amizade nem pedidos, Quando chamo `solicitar(1L, 2L)`, Então
  `criarSolicitacao(1L, 2L, 10/03/2026)` é chamado e a resposta é `PENDENTE` com a referência de Bruno.
- **E2 (Z4).** Dado `trocarSolicitacaoPorAmizade(1L, 2L, 10/03/2026)` = `true`, Quando chamo `solicitar(1L, 2L)`, Então
  a resposta é `AMIGOS` com a referência de Bruno e `criarSolicitacao` não é chamado.
- **E3 (Z2).** Quando chamo `solicitar(1L, 1L)`, Então `IllegalArgumentException` e nada é gravado.
- **E4 (Z3).** Dado `saoAmigos(1L, 2L)` = `true`, Quando chamo `solicitar(1L, 2L)`, Então `IllegalStateException`, e
  `criarSolicitacao` e `trocarSolicitacaoPorAmizade` não são chamados.
- **E5 (Z3).** Dado `existeSolicitacao(1L, 2L)` = `true`, Quando chamo `solicitar(1L, 2L)`, Então
  `IllegalStateException` e nada é gravado.
- **E6 (Z1).** Dado que `buscarUsuarioComum(4L)` lança `NoSuchElementException`, Quando chamo `solicitar(1L, 4L)` ou
  `solicitar(4L, 1L)`, Então `NoSuchElementException` e nada é gravado.
- **E7 (Z6).** Dado `existeSolicitacao(1L, 2L)` = `true`, Quando chamo `aceitar(2L, 1L)`, Então
  `trocarSolicitacaoPorAmizade(2L, 1L, 10/03/2026)` é chamado e a resposta é `AMIGOS` com a referência de Ana.
- **E8 (Z6).** Dado só `existeSolicitacao(1L, 2L)` = `true`, Quando chamo `aceitar(1L, 2L)` (quem pediu), Então
  `NoSuchElementException` e `trocarSolicitacaoPorAmizade` não é chamado.
- **E9 (Z7).** Dado `apagarSolicitacao(2L, 1L)` = `true`, Quando chamo `apagarSolicitacao(2L, 1L)`, Então não lança
  nada. Com `false`, `NoSuchElementException`.
- **E10 (Z9).** Dado `apagarAmizade(2L, 1L)` = `true`, Quando chamo `desfazer(2L, 1L)`, Então não lança nada. Com
  `false`, `NoSuchElementException`.
- **E11 (Z8).** Dado `listarAmigos(1L)` = [Bruno], Quando chamo `listarAmigos(1L)`, Então retorna [Bruno]. O mesmo para
  `listarRecebidas` e `listarEnviadas`.

## Casos de borda

- **Pedidos cruzados ao mesmo tempo**: podem ficar gravados dois pedidos, um em cada sentido. Aceitar qualquer um apaga
  os dois e cria uma amizade só (parte da query do repositório).
- **Pedir de novo depois de recusado, cancelado ou desfeito** é permitido.
- **Desfazer a amizade com empréstimo ativo** é permitido: o empréstimo continua e pode ser devolvido.

## Fora do escopo

- As consultas Cypher (testadas com Neo4j real no `RelacoesEntreUsuariosTest`).
- Bloquear pessoas, sugerir amigos e notificar pedidos.
