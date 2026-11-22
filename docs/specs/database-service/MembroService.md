classe: br.com.virabrequim.database_service.service.MembroService

# Spec: MembroService

## Contexto

Membros (clientes) de uma biblioteca. É o que permite à biblioteca emprestar livros para um usuário comum (ver
`EmprestimoService.md`).

```
(Usuario)-[:MEMBRO_DE {desde}]->(Biblioteca)
```

O relacionamento não é campo do node: só é lido e gravado pelo `MembroRepository` (Cypher). **Hoje** é
`calendario.diaHoje()`. Nos testes, o repositório, os services e o `Calendario` são mocks (Mockito).

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `MembroService(MembroRepository membroRepository, UsuarioService usuarioService, BibliotecaService bibliotecaService, Calendario calendario)` | Injeção pelo construtor | — |
| `void tornarMembro(Long usuarioId, Long bibliotecaId)` | Entra na biblioteca | C1, C2 |
| `void deixarDeSerMembro(Long usuarioId, Long bibliotecaId)` | Sai da biblioteca | C1, C3 |
| `List<BibliotecaDoUsuario> listarBibliotecas(Long usuarioId)` | Bibliotecas de que o usuário é membro | C1, C4 |
| `List<Membro> listarMembros(Long bibliotecaId)` | Membros da biblioteca | C1, C4 |

Colaboradores usados (mockados nos testes):

| Método | Retorno |
| --- | --- |
| `UsuarioService.buscarUsuarioComum(Long id)` | `UsuarioNode`; lança `NoSuchElementException` se não existir ou for biblioteca |
| `BibliotecaService.buscar(Long id)` | `BibliotecaNode`; lança `NoSuchElementException` se não existir ou não for biblioteca |
| `MembroRepository.adicionar(Long usuarioId, Long bibliotecaId, LocalDate desde)` | `void` (quem já é membro mantém a data original) |
| `MembroRepository.remover(Long usuarioId, Long bibliotecaId)` | `boolean`: se removeu algo |
| `MembroRepository.listarBibliotecas(Long usuarioId)` / `listarMembros(Long bibliotecaId)` | listas |
| `Calendario.diaHoje()` | `LocalDate` |

## Regras

- **C1.** O usuário é buscado com `buscarUsuarioComum` (biblioteca não é membro de biblioteca). `tornarMembro` e
  `listarMembros` buscam a biblioteca com `BibliotecaService.buscar`. A `NoSuchElementException` é propagada e nada é
  gravado.
- **C2.** `tornarMembro` é direto, sem aprovação: chama `adicionar(usuarioId, bibliotecaId, hoje)`. Repetir não é erro.
- **C3.** Se o usuário não era membro (`remover` = `false`), `deixarDeSerMembro` lança `NoSuchElementException`.
- **C4.** As listagens devolvem o resultado do repositório.

## Exemplos

**Hoje = 10/03/2026.** Ana (id 1) é usuária comum; Inatel (id 4) é biblioteca.

- **E1 (C2).** Quando chamo `tornarMembro(1L, 4L)`, Então `adicionar(1L, 4L, 10/03/2026)` é chamado.
- **E2 (C1).** Dado que `buscarUsuarioComum(4L)` lança `NoSuchElementException`, Quando chamo `tornarMembro(4L, 4L)`,
  Então `NoSuchElementException` e `adicionar` não é chamado.
- **E3 (C1).** Dado que `BibliotecaService.buscar(2L)` lança `NoSuchElementException`, Quando chamo
  `tornarMembro(1L, 2L)`, Então `NoSuchElementException` e `adicionar` não é chamado.
- **E4 (C3).** Dado `remover(1L, 4L)` = `true`, Quando chamo `deixarDeSerMembro(1L, 4L)`, Então não lança nada. Com
  `false`, `NoSuchElementException`.
- **E5 (C4).** Dado `listarBibliotecas(1L)` = [Inatel], Quando chamo `listarBibliotecas(1L)`, Então retorna [Inatel].
- **E6 (C4).** Dado `listarMembros(4L)` = [Ana], Quando chamo `listarMembros(4L)`, Então retorna [Ana].

## Casos de borda

- **Sair com empréstimo ativo** é permitido: o empréstimo continua e pode ser devolvido.

## Fora do escopo

- As consultas Cypher (testadas com Neo4j real no `RelacoesEntreUsuariosTest`).
- Aprovação de membros e estatísticas para a biblioteca.
