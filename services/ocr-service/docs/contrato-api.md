# ocr-service — Contrato da API

> **Status:** v1. Descreve a API implementada até 09/11/2026, incluindo o agente estruturador (tarefa E). As decisões nasceram em 17/10/2026; o que ainda não existe no código está marcado como **planejado**. O que o documento diz dos outros serviços foi conferido contra o código em 22/11/2026.
>
> **Formato exato:** o `/docs` (Swagger) do serviço, em http://localhost:8301/docs, mostra os schemas e um exemplo de cada erro. Este documento explica o comportamento e o porquê das decisões.
>
> **Revisar com:** quem cuida do front, do `database-service` (Neo4j) e do `notification-service` (bot do Telegram).

---

## 1. Responsabilidade do serviço

- Recebe a **foto** de uma capa (ou lombada) e devolve os **dados do livro** extraídos (EAST + Tesseract + agente estruturador com Gemini).
- **Sem estado:** não grava nada, **não conhece o usuário** (`usuarioId`) e **não chama outros serviços** do projeto. A única chamada externa é a do agente ao Gemini.
- O agente deste serviço apenas **estrutura** o texto do OCR em JSON. O **enriquecimento** (sinopse, ISBN, páginas, editora, autores e tags) é do `database-service`.
- O agente é **opcional**: sem a chave, a API funciona normalmente e devolve o texto bruto sem estrutura (`"unstructured"`).
- Quem grava o livro no Neo4j é **o cliente** (front ou bot), depois que o usuário confirma os dados.

## 2. Fluxo de dados entre os serviços

```
Front (web)
  │ 1. POST /v1/extraction  (foto, multipart, campo "image")
  ▼
ocr-service ──► resposta: lista de livros + texto bruto + result
  │
  ▼
Front: usuário revisa e edita o livro
  │ 2. POST /api/v1/usuario/{usuarioId}/livro  (um livro por chamada)
  │ 3. POST /api/v1/usuario/{usuarioId}/livro/{livroId}/enriquecimento  (texto bruto em "textoBruto")
  ▼
database-service: grava no Neo4j; o agente enriquecedor completa só os campos vazios
  ┆ POST /notificacoes/livro-salvo  (planejado)
  ▼
notification-service ──► Telegram
```

- **Enriquecimento:** é uma chamada separada (passo 3), feita pelo cliente depois de gravar, porque precisa do `livroId`. O `raw_text` do OCR vai no corpo, no campo `textoBruto`, e ajuda o agente a achar a edição certa. O campo aceita **até 5000 caracteres**; acima disso o banco responde `400`, então o cliente corta o texto antes de enviar. O front ainda não faz essa chamada.
- **Aviso ao Telegram — planejado:** a rota `POST /notificacoes/livro-salvo` existe no `notification-service`, mas o `database-service` ainda não a chama.
- **Bot do Telegram (futuro):** baixa a foto do Telegram (*long-polling*), envia o **arquivo** para `POST /v1/extraction` com o cabeçalho `X-Internal-Token` (planejado), conduz a edição com o usuário e grava no `database-service`, como o front. As chamadas e as respostas são as mesmas do front.
- **Rotas do `database-service`:** todas começam com `/api/v1` (no compose, `http://localhost:8321/api/v1`). `POST /api/v1/usuario/{usuarioId}/livro` recebe **um** livro por chamada. O combinado era uma lista; como o MVP tem um livro por foto, uma chamada basta.

## 3. Rotas

| Método | Caminho | Quem chama | Resposta |
|---|---|---|---|
| `POST` | `/v1/extraction` | front, bot | Os livros encontrados na foto (seção 4) |
| `GET` | `/health` | healthcheck do compose | **Pronto:** `200 {"status": "ready"}` com o modelo carregado; `503 {"status": "loading"}` ou `503 {"status": "error"}` |
| `GET` | `/live` | nenhum cliente hoje (diagnóstico manual) | **Vivo:** sempre `200 {"status": "alive"}`, mesmo com o modelo carregando |
| `GET` | `/docs` | pessoas | Documentação interativa (Swagger) gerada pelo FastAPI |

- Padrão REST, com substantivos, em inglês. A versão fica no caminho (`/v1/`) e só nas rotas de negócio.
- As rotas de saúde não têm versão: o significado delas não muda, e o compose não precisa mudar.
- Porta: `8301` (faixa do OCR no compose: 8301–8320).
- O servidor começa a responder na hora; o modelo EAST carrega em segundo plano (~1 s). Até lá, `/health` e `/v1/extraction` respondem `503`.

## 4. `POST /v1/extraction`

### 4.1 Requisição

- `multipart/form-data`, com a foto como **arquivo** no campo **`image`**. **Não** aceita URL, porque baixar endereços enviados pelo cliente abre acesso à rede interna.
- Formatos: JPEG e PNG, verificados pelo servidor pelos *magic numbers* (`FF D8 FF` e `89 50 4E 47 0D 0A 1A 0A`). A extensão e o cabeçalho declarados pelo cliente não são confiáveis.
- Cabeçalho `X-Internal-Token` — **planejado (tarefa F):** obrigatório para o bot; a regra para o front está em aberto (seção 9).

### 4.2 Resposta de sucesso (200)

| Campo | Tipo | O que é |
|---|---|---|
| `books` | lista de livros | **Sempre** uma lista, com **um** item no MVP (uma foto, um livro) |
| `books[].nome` | texto ou `null` | O título |
| `books[].volume` | texto ou `null` | Só quando o texto mostra uma marca clara (`"Vol."`, `"Volume"`, `"nº"`, `"#"`) |
| `books[].autores` | lista de textos ou `null` | Quem escreveu e, em mangás e HQs, também quem desenhou (roteiro e arte); tradutores e outros créditos ficam de fora |
| `books[].editora` | texto ou `null` | A editora |
| `raw_text` | texto | O que o OCR leu, uma região por linha, de cima para baixo. **Vai sempre** |
| `result` | `"structured"`, `"unstructured"` ou `"no_text"` | Em que pé ficou a leitura (tabela abaixo) |
| `meta.detector` | `"east"` ou `"fallback"` | `east`: o EAST achou regiões de texto; `fallback`: não achou, e o Tesseract leu a imagem inteira |
| `meta.elapsed_ms` | inteiro | Tempo de processamento em ms: OCR + agente |

- Um campo **não identificado vem `null`**. O `""` e a lista vazia `[]` não são usados.
- O que é confiável vai nos campos; o que é incerto ou não tem campo próprio fica no `raw_text`, que o cliente repassa ao enriquecedor do `database-service` no campo `textoBruto` (seção 2, passo 3).

| `result` | Quando | `books` | Ação sugerida ao cliente |
|---|---|---|---|
| `structured` | O agente achou o **título** (os outros campos podem vir `null`) | O livro preenchido | O usuário confere e confirma |
| `unstructured` | O OCR leu texto, mas o agente não estruturou: sem chave, erro do Gemini, mais de 5 s, ou nenhum título encontrado | Um livro com todos os campos `null` | Mostrar o `raw_text` para o usuário editar |
| `no_text` | O OCR não leu nada (o agente nem é chamado) | Um livro com todos os campos `null`, e `raw_text` vazio | Editar ou cadastrar manualmente |

Exemplo real (capa de *Memórias do Subsolo*, com o `raw_text` encurtado):

```json
{
  "books": [
    { "nome": "Memórias do Subsolo", "volume": null, "autores": ["Fiódor Dostoiévski"], "editora": "Editora 34" }
  ],
  "raw_text": "FIOI\nDOR\nDOSTOIÉV:\nSTOIEVSKI\n...\nSCHNAIDERMAN\nBORIS\n34\neditoral",
  "result": "structured",
  "meta": { "detector": "east", "elapsed_ms": 5835 }
}
```

### 4.3 Catálogo de erros e comportamento

Todo erro tem o mesmo corpo: `{"code": "...", "message": "..."}`. O cliente **age** a partir do `code`, que é estável, e não do texto da `message`, que serve para o log. A mensagem para o usuário é responsabilidade de cada cliente (o site e o bot podem escrever de jeitos diferentes).

| Situação | Status | `code` | Ação sugerida ao cliente |
|---|---|---|---|
| Campo `image` ausente ou inválido | `422` | `invalid_request` | Corrigir a requisição (erro de integração, não do usuário) |
| Modelo ainda carregando, ou falhou ao carregar | `503` | `service_not_ready` | Tentar de novo em instantes |
| Arquivo maior que 20 MB | `413` | `file_too_large` | Comprimir ou enviar outra foto |
| Não é JPEG nem PNG (*magic numbers*) | `415` | `unsupported_format` | Enviar JPEG ou PNG |
| Arquivo corrompido (não decodifica) | `422` | `corrupt_image` | Enviar outra foto |
| Imagem acima de 55.000.000 pixels | `422` | `image_too_large` | Enviar uma foto menor |
| Menor lado abaixo de 32 pixels | `422` | `image_too_small` | Enviar outra foto |
| Erro interno | `500` | `internal_error` | Tentar de novo mais tarde |
| Muitas requisições (*rate limit*) — **planejado (tarefa F)** | `429` | `rate_limited` | Esperar e tentar de novo |
| Fila cheia — **planejado (C4)** | `503` | `queue_full` | Tentar de novo mais tarde |

- **Não são erros** (respondem `200`): "nenhum texto lido" (`result: "no_text"`) e "agente indisponível ou acima de 5 s" (`result: "unstructured"`). Ver a seção 4.2.
- Os erros de imagem dividem o status `422`; o cliente distingue pelo `code`.
- O `500` não expõe detalhes internos: a `message` é sempre `"erro interno do ocr-service"`, e o traceback fica só no log do servidor.
- Se só **algumas** regiões tiverem letras pequenas demais, a resposta é `200` com o que foi lido, e o usuário completa o resto.

## 5. Limites de infraestrutura

- **Ordem das verificações:** requisição válida (campo `image`) → modelo pronto → tamanho em MB → *magic numbers* → decodificação (com o limite de pixels) → mínimo de pixels → OCR → "nenhum texto" → agente.
- **20 MB** por arquivo, verificados antes de qualquer outra coisa (o servidor lê no máximo 20 MB + 1 byte).
- **Pixels:** `OPENCV_IO_MAX_IMAGE_PIXELS = 55000000` (50 MP + 10% de folga); o padrão do OpenCV, de 2³⁰ pixels, é alto demais para o container.
- **Mínimo de pixels:** **32** no menor lado. Valor **provisório**, a confirmar com o experimento da altura das letras (seção 9).
- **Processamento sequencial:** uma leitura por vez, porque o modelo EAST é um só (~630 MB). Hoje as fotos esperam a vez sem limite; a **fila de 3** (espera máxima de ~30 s) com `503 queue_full` é **planejada (C4)**.
- **Tempo medido em 09/11** (já com o agente): ~6 s para uma capa; ~27 s para uma lombada com muitas regiões (~75 linhas), quase todo o tempo no OCR.
- **Agente estruturador:**
  - modelo `gemini-3.5-flash-lite` (Google, *free tier*);
  - **corte de 5 s** do nosso lado; o SDK manda um prazo de 10 s ao servidor, o mínimo que o Gemini aceita;
  - uma tentativa só, sem repetir;
  - roda **depois** do OCR e **fora** do lock: enquanto espera o Gemini, a próxima foto já pode usar o EAST;
  - não é chamado quando o OCR não leu nada (`no_text`).
- **Rate limiting** — **planejado (tarefa F):** **10 por minuto por IP**, respondendo `429 rate_limited` (ver as questões em aberto). O limite **por usuário do Telegram** fica no bot.
- **CORS:** só a origem do front, na variável `CORS_ORIGENS_PERMITIDAS` (lista separada por vírgula; padrão `http://localhost:5173`), com os métodos `GET` e `POST`.

### 5.1 Limitações conhecidas

- O `500` sai **sem** o cabeçalho de CORS: no navegador, o front vê um erro de rede em vez do JSON.
- O servidor continua processando uma foto mesmo quando o cliente já desistiu de esperar.
- Se o modelo EAST falhar ao carregar, o serviço fica em `503` até o container ser reiniciado.
- O OCR quase nunca lê o número do volume na lombada, então `volume` costuma vir `null`; vários volumes da mesma série numa foto viram um livro só.

## 6. Segurança

- A entrada é só por arquivo (sem URL).
- O tipo do arquivo é verificado pelo servidor.
- `X-Internal-Token` — **planejado (tarefa F):** segredo compartilhado entre o bot e o OCR, guardado como *secret* dos dois lados.
- **Chave do LLM:** `GEMINI_API_KEY`, no `.env` da raiz (que o git ignora); o compose a repassa ao container. Nunca vai para o repositório nem para o log.
- No *free tier* do Gemini, o Google pode usar o conteúdo enviado (o texto lido da foto) para melhorar os produtos dele.
- O **CORS protege o usuário** no navegador; quem **protege o serviço** é o *rate limiting*.
- Autenticação de usuário: fora do MVP (como no `notification-service`).

## 7. Escopo do MVP

**Dentro**
- Uma capa, um livro, com a resposta já em formato de lista.
- Os campos `nome`, `autores` e `editora`, além do texto bruto e do `result`.
- O `volume`, se der para identificar.
- O agente estruturador, opcional.
- O catálogo de erros, os limites, o CORS e as rotas de saúde; o *rate limiting* e a fila de 3 estão planejados.

**Fora (depois do MVP)**
- Vários livros por foto, agrupando as regiões por lombada.
- Incerteza por campo, para o front destacar o que revisar.
- HEIC e outros formatos.
- Distinguir uma foto aleatória de uma capa ilegível (trade-off com a velocidade).
- Consulta ao Neo4j durante a leitura.
- Processamento assíncrono, fila externa e mais de um container.

## 8. Combinados com os colegas

Situação conferida contra o código em 22/11/2026.

- **`database-service`:**
  - receber um livro por chamada em `POST /api/v1/usuario/{usuarioId}/livro`, com `volume` e `autores` como textos (o `nome` do OCR vira `titulo`) — **feito**;
  - o enriquecedor usar o texto bruto — **feito**, no campo `textoBruto` da rota de enriquecimento;
  - adicionar `editora` ao nó `Livro` — **feito** (PR #68): o `NovoLivro` aceita a `editora`, com até 120 caracteres;
  - detectar duplicatas no `POST` — **pendente**;
  - avisar o `notification-service` em `POST /notificacoes/livro-salvo` — **pendente**.
- **Front:**
  - enviar a foto no campo `image`, montar o próprio payload para o `database-service` (sem repassar o JSON do OCR), agir pelo `code` dos erros e pelo `result`, tratar a resposta como lista e permitir editar o livro — **feito** (PRs #58 e #59);
  - enviar no cadastro a `editora` lida pelo OCR — **pendente**: o front lê o campo, mas o `montarCorpo` (`livrosContrato.ts`) não o manda, então a editora ainda se perde;
  - chamar o enriquecimento com o `raw_text` em `textoBruto` — **pendente**.
- **Bot:** baixar a foto e enviar o arquivo, enviar o `X-Internal-Token` e aplicar o limite por usuário — **pendente** (o bot ainda não chama o OCR).

## 9. Questões em aberto

- [ ] **Rascunho no `database-service`** em vez de o front guardar os dados até a confirmação. Decidir com o colega; essa opção obrigaria o OCR a conhecer o `usuarioId`.
- [ ] **O rate limit de 10 por minuto por IP é maior do que a capacidade do serviço** (~10 capas por minuto, mas só ~2 lombadas por minuto): um IP sozinho pode ocupar o serviço? E a tolerância de rajada continua?
- [ ] **Mínimo de pixels e o experimento da altura das letras.** Inclui o caso de **todas** as regiões com letras pequenas demais: hoje a foto volta `200` com o que deu para ler (ou `no_text`); um aviso como "aproxime a câmera" ainda não existe.
- [ ] Regra do `X-Internal-Token` para o front.
- [ ] Requisições perdidas quando o container reinicia (limitação conhecida, a resolver com a escala).

**Resolvidas**
- [x] Semântica de `/health` e `/live`: `/health` = pronto (healthcheck do compose); `/live` = vivo, sem cliente por enquanto.
- [x] Nomes dos campos do JSON e dos identificadores do catálogo (seção 4).
- [x] Códigos de status exatos (seção 4.3).
- [x] Onde este documento fica: `services/ocr-service/docs/`.

## 10. Histórico das decisões

| Rodada | Data | Tema | Principais decisões |
|---|---|---|---|
| 1 | 17/10 | Fronteiras | O front chama o OCR; quem grava é o cliente, depois da confirmação; o agente só estrutura; a API não depende do cliente; N livros por foto fica para o futuro |
| 2 | 17/10 | Contrato | Entrada por arquivo (sem URL); resposta como lista; `null` para não identificado; famílias 2xx/4xx/5xx e catálogo com identificadores; 20 MB e 55 MP; *magic numbers*; "nenhum texto" é 2xx |
| 3 | 17/10 | Dependências | Nenhuma dependência do `database-service` no MVP; duplicata detectada no `database-service`; texto bruto vai pelo front até o enriquecedor |
| 4 | 17/10 | Falhas e operação | LLM com tempo máximo de 2,5 s (virou 5 s na rodada 7) e indicador de "não estruturado"; processamento sequencial com fila de 3; pronto ≠ vivo; CORS só para o front; *rate limiting* com `429`; segredo do bot |
| 5 | 17/10 | Rotas | `POST /v1/extraction`, `GET /health`, `GET /live`; REST em inglês; versão no caminho; este documento além do `/docs` do FastAPI |
| 6 | 26–29/10 | API e erros | Campo `image`; status de cada `code` (413, 415, 422, 503, 500); corpo `{code, message}`; uma leitura por vez (lock); CORS por variável de ambiente; exemplos dos erros no `/docs` |
| 7 | 09/11 | Agente estruturador | Gemini 3.5 Flash-Lite (*free tier*); um livro por foto; `structured` só com título; corte de 5 s nosso (o servidor exige no mínimo 10 s); sem chave ou com falha, segue `unstructured` com 200; arte conta como autor; volume só com marca clara; não chama o agente em `no_text`; `elapsed_ms` inclui o agente |
| 8 | 20–22/11 | Conferência com o código | Enriquecimento como chamada separada, depois de gravar, com o texto bruto em `textoBruto`, de até 5000 caracteres (PR #64 do `database-service`); rotas do banco com o prefixo `/api/v1`; `editora` aceita pelo banco (PR #68), mas ainda não enviada pelo front; aviso ao Telegram e duplicatas marcados como pendentes; combinados com a situação de cada um |
