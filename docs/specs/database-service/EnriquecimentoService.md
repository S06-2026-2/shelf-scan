classe: br.com.virabrequim.database_service.service.EnriquecimentoService

# Spec: EnriquecimentoService

## Contexto

Aplica no livro o que o `AgenteEnriquecedor` sugeriu (rota `POST /usuario/{usuarioId}/livro/{livroId}/enriquecimento`).
Regra principal: **o agente só preenche campos vazios**, nunca sobrescreve o que o usuário cadastrou.

A gravação reaproveita o PATCH do livro (`LivroService.atualizar`), que já aplica as validações do `LivroNode` e o
*find-or-create* de autores e tags. Não há transação aberta durante a chamada ao LLM.

Campo **vazio** no livro: `sinopse`, `isbn` ou `paginas` = `null`; `autores` ou `tags` = lista vazia.

Nos testes, `LivroService` e `AgenteEnriquecedor` são mocks (Mockito).

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `EnriquecimentoService(LivroService livroService, AgenteEnriquecedor agente)` | Injeção pelo construtor | — |
| `Resultado enriquecer(Long usuarioId, Long livroId, String textoBruto)` | Enriquece o livro do usuário | N1–N7 |
| `record Resultado(LivroNode livro, List<String> camposPreenchidos, boolean livroIdentificado, String modelo)` | Retorno | N5, N6 |

Colaboradores usados (mockados nos testes):

| Método | Retorno |
| --- | --- |
| `LivroService.buscarDoDono(Long usuarioId, Long livroId)` | `LivroNode` (ou `NoSuchElementException`) |
| `LivroService.atualizar(Long usuarioId, Long livroId, AtualizacaoLivro dados)` | o livro atualizado |
| `AgenteEnriquecedor.sugerir(LivroNode livro, String textoBruto)` | `SugestaoEnriquecimento` |
| `AgenteEnriquecedor.modelo()` | nome do modelo |

`record AtualizacaoLivro(String titulo, List<String> autores, List<String> tags, String isbn, String volume,
Integer paginas, String sinopse)`: campo `null` = não mexer.

## Regras

- **N1.** Primeiro busca o livro com `buscarDoDono(usuarioId, livroId)`. Se lançar exceção (livro inexistente ou de
  outro usuário), ela não é capturada e o **agente não é chamado**.
- **N2.** Chama `agente.sugerir(livro, textoBruto)` uma vez, com o livro do N1 e o `textoBruto` recebido (pode ser
  `null`). Exceção do agente não é capturada e `atualizar` não é chamado.
- **N3.** Um campo sugerido entra só se o campo do livro estiver **vazio** e a sugestão tiver valor (não `null` /
  lista não vazia). Campos já preenchidos no livro nunca são alterados.
- **N4.** O total de páginas sugerido é descartado se for **menor** que a página atual do dono (`livro.getPaginaAtual()`)
  ou que a página atual do empréstimo ativo. Igual é aceito. Página atual `null` não impede.
- **N5.** Se nenhum campo entrar, `atualizar` **não é chamado** e o resultado tem o livro do N1, `camposPreenchidos`
  vazio, `livroIdentificado` igual ao da sugestão e o `modelo()` do agente.
- **N6.** Se algum campo entrar, chama `atualizar(usuarioId, livroId, dados)` **uma vez**, com os campos que entraram
  e `null` em todos os outros (`titulo` e `volume` sempre `null`). O resultado tem o livro devolvido por `atualizar`,
  `livroIdentificado` = `true` e o `modelo()` do agente.
- **N7.** `camposPreenchidos` lista os campos que entraram, nesta ordem: "sinopse", "isbn", "paginas", "autores",
  "tags".

## Exemplos

Usuário Ana (id 1), livro 5 "Memórias do Subsolo". Sugestão completa dos exemplos: `livroIdentificado` = `true`,
sinopse "Resumo", ISBN "9788573261585", 152 páginas, autores ["Fiódor Dostoiévski"], tags ["romance"].
`agente.modelo()` = "gemini-teste".

- **E1 (N3, N6, N7).** Dado o livro 5 só com título (sem sinopse, ISBN, páginas, autores e tags) e a sugestão completa,
  Quando chamo `enriquecer(1L, 5L, null)`, Então `atualizar(1L, 5L, dados)` é chamado com sinopse "Resumo", ISBN
  "9788573261585", páginas 152, autores ["Fiódor Dostoiévski"], tags ["romance"], título e volume `null`; e o resultado
  tem o livro devolvido por `atualizar`, campos ["sinopse", "isbn", "paginas", "autores", "tags"],
  `livroIdentificado` = `true` e modelo "gemini-teste".
- **E2 (N3, N6).** Dado o livro 5 com sinopse "Minha sinopse", 200 páginas e o autor "Dostoiévski" (sem ISBN e sem
  tags) e a sugestão completa, Quando enriqueço, Então `atualizar` recebe só ISBN e tags (sinopse, páginas e autores
  `null`) e os campos são ["isbn", "tags"].
- **E3 (N5).** Dado o livro 5 com todos os campos preenchidos e a sugestão completa, Quando enriqueço, Então
  `atualizar` não é chamado e o resultado tem o livro 5, campos vazios, `livroIdentificado` = `true` e modelo
  "gemini-teste".
- **E4 (N5).** Dado o livro 5 só com título e a sugestão `SugestaoEnriquecimento.vazia()`, Quando enriqueço, Então
  `atualizar` não é chamado e o resultado tem campos vazios e `livroIdentificado` = `false`.
- **E5 (N4).** Dado o livro 5 sem total de páginas, com a leitura do dono `LENDO` na página 180, e a sugestão de 152
  páginas, Quando enriqueço, Então "paginas" não está nos campos e `atualizar` recebe `paginas` = `null`.
- **E6 (N4).** Dado o livro 5 sem total de páginas e com empréstimo ativo cuja página atual é 152, e a sugestão de 152
  páginas, Quando enriqueço, Então "paginas" está nos campos.
- **E7 (N1).** Dado `buscarDoDono(2L, 5L)` lançando `NoSuchElementException`, Quando chamo `enriquecer(2L, 5L, null)`,
  Então lança `NoSuchElementException` e `agente.sugerir` e `atualizar` não são chamados.
- **E8 (N2).** Dado `agente.sugerir` lançando `AgenteIndisponivelException`, Quando enriqueço, Então lança
  `AgenteIndisponivelException` e `atualizar` não é chamado.
- **E9 (N2).** Quando chamo `enriquecer(1L, 5L, "MEMORIAS DO SUBSOLO")`, Então `agente.sugerir` recebe o livro 5 e
  "MEMORIAS DO SUBSOLO".

## Casos de borda

- **Usuário preencheu o campo entre a sugestão e a gravação:** o `atualizar` recarrega o livro e pode sobrescrever
  esse campo com a sugestão (janela de segundos, aceita por enquanto).
- **Página atual do empréstimo maior que a sugestão:** a sugestão de páginas é descartada (N4) em vez de o
  `LivroNode` lançar conflito e o enriquecimento inteiro falhar.

## Fora do escopo

- As travas da sugestão (ver a spec `AgenteEnriquecedor`).
- As validações e o *find-or-create* do `atualizar` (ver a spec `LivroService`).
- Enriquecer livros de outros usuários (o dono é conferido pelo `buscarDoDono`).
