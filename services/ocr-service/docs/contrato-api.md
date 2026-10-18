# ocr-service — Contrato da API (esqueleto)

> **Status:** esqueleto. Registra as decisões de arquitetura tomadas em 17/10/2026, em uma conversa de perguntas e respostas sobre as rotas do serviço. Os itens marcados com `TODO` ainda precisam ser definidos ou detalhados.
>
> **Revisar com:** quem cuida do front, do `database-service` (Neo4j) e do `notification-service` (bot do Telegram).

---

## 1. Responsabilidade do serviço

- Recebe a **foto** de uma capa (ou lombada) e devolve os **dados do livro** extraídos (EAST + Tesseract + agente estruturador).
- **Sem estado:** não grava nada, **não conhece o usuário** (`usuarioId`) e **não chama outros serviços** no MVP.
- O agente deste serviço apenas **estrutura** o texto do OCR em JSON. O **enriquecimento** (sinopse, ISBN, normalização de autores) é do `database-service`.
- Quem grava o livro no Neo4j é **o cliente** (front ou bot), depois que o usuário confirma os dados.

## 2. Fluxo de dados entre os serviços

```
Front (web)
  │ POST /v1/extraction  (foto, multipart)
  ▼
ocr-service ──► resposta: lista de livros + texto bruto
  │
  ▼
Front: usuário revisa, edita, adiciona ou remove livros
  │ POST <rota do database-service>  (lista de livros + texto bruto opcional)
  ▼
database-service: detecta duplicata, enriquece, grava no Neo4j
  │ POST /notificacoes/livro-salvo
  ▼
notification-service ──► Telegram
```

- **Bot do Telegram (futuro):** baixa a foto do Telegram (*long-polling*), envia o **arquivo** para `POST /v1/extraction` com o cabeçalho `X-Internal-Token`, conduz a edição com o usuário e grava no `database-service`, como o front. As chamadas e as respostas são as mesmas do front.
- `TODO`: rota do `database-service` que recebe a lista de livros (definida pelo colega).

## 3. Rotas

| Método | Caminho | Quem chama | Para quê |
|---|---|---|---|
| `POST` | `/v1/extraction` | front, bot | Ler a foto e devolver os livros encontrados |
| `GET` | `/health` | docker compose (healthcheck) | Serviço **pronto**: modelo carregado, aceitando fotos |
| `GET` | `/live` | `TODO` | Processo **vivo** |

- Padrão REST, com substantivos, em inglês. A versão fica no caminho (`/v1/`) e só nas rotas de negócio.
- As rotas de saúde não têm versão: o significado delas não muda, e o compose não precisa mudar.
- Porta: `8301` (faixa do OCR no compose: 8301–8320).

## 4. `POST /v1/extraction`

### 4.1 Requisição

- `multipart/form-data`, com a foto como **arquivo**. **Não** aceita URL, porque baixar endereços enviados pelo cliente abre acesso à rede interna.
- Campo do arquivo: `TODO` (nome).
- Formatos: JPEG e PNG, verificados pelo servidor pelos *magic numbers* (`FF D8 FF` e `89 50 4E 47 0D 0A 1A 0A`). A extensão e o cabeçalho declarados pelo cliente não são confiáveis.
- Cabeçalho `X-Internal-Token`: obrigatório para o bot (`TODO`: regra para o front).

### 4.2 Resposta de sucesso (2xx)

- A resposta é **sempre uma lista de livros**, com **um** item no MVP (uma capa, um livro).
- Campos de cada livro: `nome`, `volume`, `autores` (lista de textos) e `editora`. Um campo **não identificado vem `null`** (o `""` não é usado).
- **Texto bruto** do OCR: vai sempre. O que é confiável vai nos campos; o que é incerto ou não tem campo próprio fica no bruto, e o front o repassa ao `database-service` para o enriquecedor.
- **Indicador de "não estruturado"**: quando o LLM falha ou estoura o tempo, voltam o texto bruto e os campos `null`.
- **Identificador de "nenhum texto"**: a foto foi lida, mas nenhum campo foi encontrado. O usuário edita ou cadastra manualmente.

```json
{
  "TODO_nome_da_lista": [
    { "nome": null, "volume": null, "autores": [], "editora": null }
  ],
  "TODO_texto_bruto": "",
  "TODO_indicadores": "estruturado / não estruturado / nenhum texto"
}
```

`TODO`: nomes definitivos dos campos e exemplo completo.

### 4.3 Catálogo de erros e comportamento

O cliente **age** a partir de um **identificador estável**, e não do texto da mensagem. A mensagem para o usuário é responsabilidade de cada cliente (o site e o bot podem escrever de jeitos diferentes).

| Situação | Família | Status | Identificador | Ação sugerida ao cliente |
|---|---|---|---|---|
| Arquivo maior que 20 MB | 4xx | `TODO` | `TODO` | Comprimir ou enviar outra foto |
| Tipo não suportado (*magic numbers*) | 4xx | `TODO` | `TODO` | Enviar JPEG ou PNG |
| Arquivo corrompido (`cv2.imdecode` devolve `None`) | 4xx | `TODO` | `TODO` | Enviar outra foto |
| Imagem acima de 55.000.000 pixels | 4xx | `TODO` | `TODO` | Enviar uma foto menor |
| Imagem abaixo do mínimo de pixels | 4xx | `TODO` | `TODO` | Enviar outra foto |
| **Todas** as regiões com letras pequenas demais | 4xx | `TODO` | `TODO` | Aproximar a câmera |
| Nenhum texto lido | **2xx** | `TODO` | `TODO` | Editar ou cadastrar manualmente |
| LLM indisponível ou acima de 2,5 s | **2xx** | `TODO` | `TODO` | Mostrar o texto bruto para edição |
| Muitas requisições (*rate limit*) | 4xx | `429` | `TODO` | Esperar e tentar de novo |
| Fila cheia | 5xx | `TODO` | `TODO` | Tentar de novo mais tarde |
| Serviço ainda não pronto (modelo carregando) | 5xx | `TODO` | `TODO` | Tentar de novo em instantes |
| Erro interno | 5xx | `TODO` | `TODO` | Tentar de novo mais tarde |

- Se só **algumas** regiões tiverem letras pequenas demais, a resposta é 2xx com o que foi lido, e o usuário completa o resto.
- `TODO`: códigos 4xx e 5xx exatos (consultar a lista de status HTTP da MDN).

## 5. Limites de infraestrutura

- **Ordem das verificações:** tamanho em MB → *magic numbers* → decodificação (com o limite de pixels) → OCR → "nenhum texto".
- **20 MB** por arquivo, verificados antes de qualquer outra coisa.
- **Pixels:** `OPENCV_IO_MAX_IMAGE_PIXELS = 55000000` (50 MP + 10% de folga); o padrão do OpenCV, de 2³⁰ pixels, é alto demais para o container.
- **Mínimo de pixels:** `TODO`, a definir com um experimento nas amostras, junto com o limite da altura das letras.
- **Processamento sequencial**, uma leitura por vez (um único modelo EAST, ~630 MB), com **fila de 3** (espera máxima de ~30 s).
- **Tempo:** ~6 a 10 s por capa, ou seja, ~7,5 leituras por minuto no serviço inteiro.
- **LLM:** tempo máximo de **2,5 s** (modelo de baixa latência); a chave é um *secret*.
- **Rate limiting:** **10 por minuto por IP**, respondendo `429` (ver as questões em aberto). O limite **por usuário do Telegram** fica no bot.
- **CORS:** só a origem do front, configurada numa variável de ambiente.

## 6. Segurança

- A entrada é só por arquivo (sem URL).
- O tipo do arquivo é verificado pelo servidor.
- `X-Internal-Token`: segredo compartilhado entre o bot e o OCR, guardado como *secret* dos dois lados.
- A chave do LLM é um *secret* e nunca vai para o repositório.
- O **CORS protege o usuário** no navegador; quem **protege o serviço** é o *rate limiting*.
- Autenticação de usuário: fora do MVP (como no `notification-service`).

## 7. Escopo do MVP

**Dentro**
- Uma capa, um livro, com a resposta já em formato de lista.
- Os campos `nome`, `autores` e `editora`, além do texto bruto e dos indicadores.
- O `volume`, se der para identificar.
- O catálogo de erros, os limites, o *rate limiting*, o CORS e as rotas de saúde.

**Fora (depois do MVP)**
- Vários livros por foto, agrupando as regiões por lombada.
- Incerteza por campo, para o front destacar o que revisar.
- HEIC e outros formatos.
- Distinguir uma foto aleatória de uma capa ilegível (trade-off com a velocidade).
- Consulta ao Neo4j durante a leitura.
- Processamento assíncrono, fila externa e mais de um container.

## 8. Combinados com os colegas

- **`database-service`:** adicionar `editora` ao nó `Livro`; receber uma **lista de livros** (`nome`, `volume`, `autores` como textos, `editora` e o texto bruto opcional); detectar duplicatas no `POST`; o enriquecedor usa o texto bruto.
- **Front:** monta o próprio payload para o `database-service` (não repassa o JSON do OCR); age pelos identificadores do catálogo; trata a resposta como lista; permite editar, adicionar e remover livros.
- **Bot:** baixa a foto e envia o arquivo; envia o `X-Internal-Token`; aplica o limite por usuário.

## 9. Questões em aberto

- [ ] **Rascunho no `database-service`** em vez de o front guardar os dados até a confirmação. Decidir com o colega; essa opção obrigaria o OCR a conhecer o `usuarioId`.
- [ ] **O rate limit de 10 por minuto por IP é maior do que a capacidade de ~7,5 por minuto:** um IP sozinho pode ocupar o serviço? E a tolerância de rajada continua?
- [ ] **Semântica de `/health` e `/live`:** confirmar que `/health` = pronto (healthcheck do compose) e `/live` = vivo; quem consome o `/live`?
- [ ] Nomes dos campos do JSON e dos identificadores do catálogo.
- [ ] Códigos de status exatos.
- [ ] Mínimo de pixels e o experimento da altura das letras.
- [ ] Regra do `X-Internal-Token` para o front.
- [ ] Requisições perdidas quando o container reinicia (limitação conhecida, a resolver com a escala).
- [ ] Onde este documento fica: `services/ocr-service/docs/` ou `docs/api/` na raiz.

## 10. Histórico da conversa

| Rodada | Tema | Principais decisões |
|---|---|---|
| 1 | Fronteiras | O front chama o OCR; quem grava é o cliente, depois da confirmação; o agente só estrutura; a API não depende do cliente; N livros por foto fica para o futuro |
| 2 | Contrato | Entrada por arquivo (sem URL); resposta como lista; `null` para não identificado; famílias 2xx/4xx/5xx e catálogo com identificadores; 20 MB e 55 MP; *magic numbers*; "nenhum texto" é 2xx |
| 3 | Dependências | Nenhuma dependência do `database-service` no MVP; duplicata detectada no `database-service`; texto bruto vai pelo front até o enriquecedor |
| 4 | Falhas e operação | LLM com tempo máximo de 2,5 s e indicador de "não estruturado"; processamento sequencial com fila de 3; pronto ≠ vivo; CORS só para o front; *rate limiting* com `429`; segredo do bot |
| 5 | Rotas | `POST /v1/extraction`, `GET /health`, `GET /live`; REST em inglês; versão no caminho; este documento além do `/docs` do FastAPI |
