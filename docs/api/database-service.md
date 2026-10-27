# API — database-service

Contrato HTTP do database-service (Shelf Scan). Ele é o guia da implementação: as rotas abaixo são o que o
frontend, o ocr-service e o notification-service podem esperar. As regras de negócio de cada operação estão nas
specs em `docs/specs/database-service/` (citadas em cada rota).

> **Status:** rascunho v1 (2026-10-27). Só `POST /user/criar` existe hoje no código e vai virar `POST /api/v1/usuarios`.

## Modelo do grafo

```
(Usuario)-[:HAS]->(Estante)<-[:STORED_IN]-(Livro)-[:LENT_TO {datas, leitura}]->(Usuario)
(Usuario)-[:ESTANTE_PADRAO]->(Estante)        ← a padrão também tem HAS
(Usuario:Biblioteca)                           ← BibliotecaNode extends UsuarioNode
(Livro)-[:WRITTEN_BY]->(Autor)
(Livro)-[:HAS_TAG]->(Tag)
```

- **Livro = exemplar físico.** Duas cópias da mesma obra são dois livros, com ids diferentes.
- **O dono é derivado** pelo caminho `Livro → Estante ← Usuario`. Não existe campo "dono".
- **"Emprestado" é derivado** do empréstimo ativo (`LENT_TO` sem data de devolução).
- **Leitura do dono** fica no `Livro`; **leitura de quem pegou emprestado** fica no `LENT_TO`.
- **"Emprestados comigo"** é uma estante **virtual**: só existe nas respostas da API, calculada a partir dos
  empréstimos ativos para o usuário.

## Convenções

| Item | Convenção |
| --- | --- |
| Base | `http://localhost:8321/api/v1` (porta do compose; o `v1` permite mudar o contrato sem quebrar clientes) |
| Formato | JSON, `Content-Type: application/json` |
| Nomes | Recursos no plural, em português, minúsculas, com hífen (`/emprestados-comigo`). O verbo HTTP diz a ação. |
| Datas | ISO 8601. Só dia: `"2026-03-10"`. Momento: `"2026-03-10T14:30:00-03:00"`. Fuso de Brasília. |
| Ids | Números (`Long`) gerados pelo Neo4j. |
| Usuário "logado" | Ainda não há autenticação: o usuário que faz a ação vem na rota (`/usuarios/{usuarioId}/...`). Quando houver autenticação, essas rotas passam a usar o usuário do token. |
| Criação | `201 Created` com header `Location` apontando para o recurso criado. |
| Sem corpo | `204 No Content` (ex.: `DELETE`). |
| Status de leitura | `"quero-ler"`, `"lendo"`, `"lido"` (os mesmos do frontend). No Java é o enum `StatusLeitura`. |

### Erros

Formato **Problem Details** (RFC 9457), que o Spring já suporta com `ProblemDetail`:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "O nome é obrigatório.",
  "instance": "/api/v1/usuarios",
  "campos": [{ "campo": "nome", "mensagem": "não pode estar em branco" }]
}
```

`campos` só aparece em erro de validação do corpo. As exceções do domínio viram status HTTP num único lugar
(`@RestControllerAdvice`):

| Exceção | Status | Quando |
| --- | --- | --- |
| `MethodArgumentNotValidException` (`@Valid`) | `400` | Corpo inválido (campo faltando, formato errado) |
| `IllegalArgumentException` | `400` | Valor inválido para a regra (ex.: página atual maior que o total, prazo de 0 dias) |
| `NoSuchElementException` | `404` | Recurso não existe **ou não é do usuário** (não revela dados de outras pessoas) |
| `IllegalStateException` | `409` | Conflito com o estado atual (email repetido, livro já emprestado, apagar estante padrão) |

---

## Usuários

Spec: `UsuarioService.md`.

| Método | Rota | Corpo | Resposta | Erros |
| --- | --- | --- | --- | --- |
| `POST` | `/usuarios` | `NovoUsuario` | `201` `Usuario` | `400` nome/email inválido · `409` email já usado |
| `POST` | `/bibliotecas` | `NovoUsuario` | `201` `Usuario` (`tipo: "biblioteca"`) | `400` · `409` |
| `GET` | `/usuarios/{usuarioId}` | — | `200` `Usuario` | `404` |

O usuário (e a biblioteca) já nasce com a estante padrão "Minha estante".

## Estantes

Spec: `EstanteService.md` (a estante virtual usa o `EmprestimoService.md`).

| Método | Rota | Corpo | Resposta | Erros |
| --- | --- | --- | --- | --- |
| `GET` | `/usuarios/{usuarioId}/estantes` | — | `200` `Estante[]` (reais + a virtual por último) | `404` usuário |
| `POST` | `/usuarios/{usuarioId}/estantes` | `NomeEstante` | `201` `Estante` | `400` nome · `404` · `409` nome repetido |
| `PATCH` | `/usuarios/{usuarioId}/estantes/{estanteId}` | `NomeEstante` | `200` `Estante` | `400` · `404` · `409` nome repetido |
| `DELETE` | `/usuarios/{usuarioId}/estantes/{estanteId}` | — | `204` (livros vão para a padrão) | `404` · `409` é a padrão |
| `GET` | `/usuarios/{usuarioId}/estantes/{estanteId}/livros` | — | `200` `LivroResumo[]` | `404` |
| `GET` | `/usuarios/{usuarioId}/emprestados-comigo` | — | `200` `LivroResumo[]` (conteúdo da estante virtual) | `404` |

## Livros

Spec: `LivroService.md` e `LivroNode.md`.

| Método | Rota | Corpo | Resposta | Erros |
| --- | --- | --- | --- | --- |
| `POST` | `/usuarios/{usuarioId}/livros` | `NovoLivro` | `201` `Livro` | `400` título/páginas/leitura · `404` usuário ou estante |
| `GET` | `/livros/{livroId}` | — | `200` `Livro` | `404` |
| `PATCH` | `/usuarios/{usuarioId}/livros/{livroId}/estante` | `{ "estanteId": 11 }` | `200` `Livro` | `404` livro ou estante não é do usuário |
| `PUT` | `/usuarios/{usuarioId}/livros/{livroId}/leitura` | `Leitura` | `200` `Livro` | `400` página inválida · `404` não é dono nem está com o livro |
| `DELETE` | `/usuarios/{usuarioId}/livros/{livroId}` | — | `204` | `404` · `409` livro emprestado |

`PUT .../leitura` atualiza **a leitura de quem chama**: se for o dono, a do livro; se for quem está com o livro
emprestado, a do empréstimo.

O `POST` de livro é o passo final do fluxo do OCR: o frontend recebe título/autor do ocr-service, o usuário confirma
na tela "Confirmar livro" e o frontend envia o `NovoLivro` para cá.

## Empréstimos

Spec: `EmprestimoService.md` e `EmprestimoRelationship.md`.

| Método | Rota | Corpo | Resposta | Erros |
| --- | --- | --- | --- | --- |
| `POST` | `/usuarios/{donoId}/livros/{livroId}/emprestimos` | `NovoEmprestimo` | `201` `Emprestimo` | `400` data/mesmo usuário · `404` · `409` já emprestado |
| `POST` | `/usuarios/{donoId}/livros/{livroId}/emprestimos/devolucao` | `Devolucao` (opcional) | `200` `Emprestimo` | `400` data · `404` · `409` não está emprestado |
| `GET` | `/usuarios/{donoId}/livros/{livroId}/emprestimos` | — | `200` `Emprestimo[]` (histórico, mais recente primeiro) | `404` |
| `GET` | `/emprestimos/vencendo` | — | `200` `AlertaEmprestimo[]` | — |
| `GET` | `/emprestimos/vencidos` | — | `200` `AlertaEmprestimo[]` | — |

A devolução é um `POST` numa sub-rota (e não um `PATCH` no empréstimo) porque é uma **ação** com regra própria,
não uma edição livre de campos. As rotas `/emprestimos/vencendo` e `/vencidos` são para o notification-service.

---

## DTOs

Os DTOs são `record`s Java no pacote `dto`, separados das entidades. Os de entrada usam Bean Validation (`@Valid`).

### Entrada

**`NovoUsuario`**
| Campo | Tipo | Obrigatório | Validação |
| --- | --- | --- | --- |
| `nome` | string | sim | `@NotBlank`, até 100 caracteres |
| `email` | string | sim | `@NotBlank`, `@Email` |

**`NomeEstante`**
| Campo | Tipo | Obrigatório | Validação |
| --- | --- | --- | --- |
| `nome` | string | sim | `@NotBlank`, até 50 caracteres |

**`NovoLivro`**
| Campo | Tipo | Obrigatório | Observação |
| --- | --- | --- | --- |
| `titulo` | string | sim | `@NotBlank` |
| `autores` | string[] | não | nomes dos autores |
| `tags` | string[] | não | nomes das tags (ex.: gênero) |
| `isbn` | string | não | |
| `volume` | string | não | |
| `paginas` | int | não | `@Positive` |
| `sinopse` | string | não | |
| `estanteId` | long | não | sem ela, vai para a estante padrão |
| `leitura` | `Leitura` | não | leitura inicial do dono |

**`Leitura`**
| Campo | Tipo | Obrigatório | Observação |
| --- | --- | --- | --- |
| `status` | `"quero-ler"` \| `"lendo"` \| `"lido"` | sim | |
| `paginaAtual` | int (≥ 0, até o total de páginas do livro) | só em `"lendo"` | ignorada nos outros status; a porcentagem é calculada pelo backend |

**`NovoEmprestimo`**
| Campo | Tipo | Obrigatório | Observação |
| --- | --- | --- | --- |
| `mutuarioId` | long | sim | quem pega o livro |
| `dataPrevistaDevolucao` | data | sim | depois de hoje; o empréstimo começa hoje |

**`Devolucao`**
| Campo | Tipo | Obrigatório | Observação |
| --- | --- | --- | --- |
| `dataDevolucao` | data | não | sem ela, a devolução é hoje |

### Saída

**`Usuario`**
```json
{ "id": 1, "nome": "Ana Souza", "email": "ana@email.com", "tipo": "usuario", "estantePadraoId": 10 }
```
`tipo` é `"usuario"` ou `"biblioteca"`.

**`Estante`**
```json
{ "id": 10, "nome": "Minha estante", "padrao": true, "virtual": false }
```
A estante virtual vem com `"id": null`, `"nome": "Emprestados comigo"`, `"padrao": false`, `"virtual": true`.

**`LivroResumo`** (listas)
```json
{
  "id": 5, "titulo": "Memórias do Subsolo", "autores": ["Fiódor Dostoiévski"],
  "leitura": { "status": "lendo", "paginaAtual": 61, "porcentagem": 40, "dataConclusao": null },
  "emprestado": true, "emprestadoPara": "Bruno", "diasParaVencimento": 2
}
```
`leitura` é a de **quem pediu a lista**: numa estante do dono, a do livro; em "Emprestados comigo", a do empréstimo.
`emprestadoPara` e `diasParaVencimento` só aparecem quando `emprestado` é `true`.

`leitura.porcentagem` é **calculada** pelo backend (`paginaAtual * 100 / paginas`, arredondada para baixo): `0` em
`quero-ler`, `100` em `lido`, e `null` em `lendo` quando o livro não tem o número de páginas.

**`Livro`** (detalhe)
```json
{
  "id": 5, "titulo": "Memórias do Subsolo", "autores": ["Fiódor Dostoiévski"], "tags": ["romance"],
  "isbn": "9788573261974", "volume": null, "paginas": 152, "sinopse": "...",
  "dataDeAdicao": "2026-03-10T14:30:00-03:00",
  "dono": { "id": 1, "nome": "Ana Souza" },
  "estante": { "id": 10, "nome": "Minha estante" },
  "leitura": { "status": "lendo", "paginaAtual": 61, "porcentagem": 40, "dataConclusao": null },
  "emprestimoAtivo": null
}
```

**`Emprestimo`**
```json
{
  "id": 30, "livroId": 5, "mutuario": { "id": 2, "nome": "Bruno" },
  "dataEmprestimo": "2026-03-10", "dataPrevistaDevolucao": "2026-03-24", "dataDevolucao": null,
  "estado": "ativo", "diasParaVencimento": 14, "vencendo": false, "vencido": false,
  "leitura": { "status": "quero-ler", "paginaAtual": 0, "porcentagem": 0, "dataConclusao": null }
}
```
`estado` é `"ativo"` ou `"devolvido"`. Em empréstimo devolvido, `diasParaVencimento` é `null`.

**`AlertaEmprestimo`** (para o notification-service)
```json
{
  "livroId": 5, "titulo": "Memórias do Subsolo",
  "dono": { "id": 1, "nome": "Ana Souza" }, "mutuario": { "id": 2, "nome": "Bruno" },
  "dataPrevistaDevolucao": "2026-03-12", "diasParaVencimento": 2
}
```

---

## Fora desta versão

- Autenticação (o usuário vem da rota).
- Atualizar/apagar usuário; editar dados do livro depois de adicionado.
- Relações entre usuários: `AMIGO` (usuários comuns) e `SEGUE` (cliente → biblioteca).
- Recomendações e enriquecimento por agente de IA.
- Paginação das listas.
- Reaproveitar autores/tags pelo nome.
