# Notification Service - Notificações + Agentes de IA

## Sobre o serviço

Responsável por três frentes do Shelf Scan:

1. Cadastro dos usuários do Telegram (vínculo entre `telegram_chat_id` e o usuário)
2. Notificar o usuário via Telegram quando um livro dele é reconhecido e salvo no `database-service`
3. Dois agentes de IA:
   - **Agente conversacional**: interage com o usuário pelo Telegram
   - **Agente recomendador**: sugere livros com base no gosto do usuário e nos livros que ele já tem salvos

## Como funciona (fluxo pretendido)

### 1. Conectar o Telegram (opcional, escolha do usuário)

Quem não quer receber nada no Telegram simplesmente não existe neste serviço.

```
usuário (logado no site) pede "Conectar Telegram"
      │
      ▼
front chama o notification-service com o usuarioId
      │
      ▼
notification-service gera um código aleatório de uso único (validade curta)
e devolve o link https://t.me/<bot>?start=<código>
      │
      ▼
usuário abre o link e toca em "Iniciar" → o bot recebe "/start <código>"
      │
      ▼
notification-service grava usuarioId ↔ chat_id e invalida o código
```

O `chat_id` é guardado **somente aqui**; o `database-service` conhece apenas o `usuarioId`.

### 2. Notificar quando um livro é salvo

```
ocr-service extrai dados do livro
      │
      ▼
database-service salva o livro (Neo4j)
      │
      ▼
database-service avisa o notification-service com o usuarioId (REST)
      │
      ▼
notification-service procura o vínculo do usuarioId
(sem vínculo = usuário não quer Telegram → ignora)
      │
      ▼
Telegram Bot API envia a notificação ao usuário
```

O agente recomendador consulta os dados do usuário (gostos + livros salvos) para sugerir novas leituras; o agente conversacional atende o usuário diretamente pelo chat do bot.

## Decisões e pontos a discutir depois

- **Autenticação entre serviços e no front**: adiada. Enquanto isso, como o ID do usuário é sequencial, alguém poderia gerar um código de vínculo para o ID de outra pessoa. Limitação conhecida.
- **Falha na entrega do evento**: a ideia é um buffer no `database-service` segurando o evento até o `notification-service` responder (e o evento precisará de um identificador para evitar notificação duplicada).
- **Exclusão de conta**: quando a conta for excluída no `database-service`, o vínculo aqui precisa ser removido. Como o `notification-service` ficará sabendo ainda não foi definido.
- **Tipo do ID do usuário**: hoje é o ID padrão do Neo4j (`Long`); o plano é migrar para um padrão de mercado (ex.: UUID). Por isso o `usuarioId` é guardado como texto.

## Estrutura

```
services/notification-service/
├── src/main/java/br/com/virabrequim/notification_service/
│   ├── cliente/     # Cliente, ClienteRepository, ClienteService
│   └── ai/          # TelegramBotAgent, MessageFormatterAgent
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/   # Migrations Flyway
├── pom.xml
├── .dockerignore
└── Dockerfile
```

## Tecnologias

- **Java 21** / **Spring Boot 3.3.4**
- **Spring Web**, **Spring Data JPA**, **Spring Validation**, **Actuator**
- **PostgreSQL** — persistência dos clientes/usuários do Telegram
- **Flyway** — versionamento do schema
- **Telegram** — `telegrambots-longpolling` + `telegrambots-client` (long-polling, sem webhook)
- Agentes de IA: chamada HTTP direta aos provedores de LLM (ainda a implementar)

## Setup do ambiente

Pré-requisitos: Docker, JDK 21 (`JAVA_HOME` configurado).

```bash
# 1. Sobe o Postgres do serviço
docker compose up -d notification-db

# 2. Build (a partir de services/notification-service)
./mvnw clean package -DskipTests

# 3. Executa o jar empacotado
java -jar target/notification-service-0.0.1-SNAPSHOT.jar
```

O serviço sobe em `http://localhost:8341`. O Postgres fica exposto em `localhost:8342` (`shelf_scan_cliente` / `notification_user` / `notification_pass`, ver [docker-compose.yml](../../docker-compose.yml)).

### Subir tudo pelo Docker

Alternativa ao `java -jar`: o compose sobe o banco e o próprio serviço.

```bash
docker compose up -d --build notification-service
```

O serviço fica em `http://localhost:8341` e conversa com o banco pela rede interna do compose. O compose lê `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `CORS_ORIGENS_PERMITIDAS` e `LLM_API_KEY` do terminal ou do `.env` na raiz do repo; sem token, o bot fica desativado, e sem `LLM_API_KEY`, o agente conversacional fica desativado. Para parar: `docker compose stop notification-service`.

- Não rode o container e o `java -jar` ao mesmo tempo: os dois brigam pela porta 8341 e, com token, pelo bot (erro 409).
- Depois de mudar o código, rode o comando de novo (o `--build` refaz a imagem).

### Bot do Telegram

O bot se chama **Christopher** (nome escolhido pelo grupo). Ele se apresenta com esse nome na mensagem de boas-vindas (constante `NOME_DO_BOT` em `TelegramBotAgent`) e é o nome que o agente conversacional deve usar. Ao criar o bot no @BotFather, use "Christopher" como nome de exibição; o username é outra coisa: precisa ser único e terminar em `bot`.

O bot só liga se a variável de ambiente `TELEGRAM_BOT_TOKEN` estiver definida na janela do terminal em que o serviço sobe. Sem ela, o serviço funciona normalmente e loga `bot do Telegram desativado`.

- Cada integrante usa o **próprio bot** de teste: crie um com o [@BotFather](https://t.me/BotFather) (`/newbot`). Dois processos com o mesmo token disputam as mensagens (erro 409).
- Guarde o token num arquivo `.env` na raiz do repo (já ignorado pelo Git) e nunca o commite. O Spring Boot não lê `.env` sozinho: carregue as variáveis no terminal antes de rodar o `java -jar`.

| Variável | Para quê | Padrão |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | Liga o bot (long-polling). Sem ela o bot fica desativado. | vazio |
| `TELEGRAM_BOT_USERNAME` | Monta o link `https://t.me/<username>?start=<código>`. Sem ela, gerar link responde 503. | vazio |
| `CORS_ORIGENS_PERMITIDAS` | Origens do front autorizadas a chamar `/telegram/**` (várias separadas por vírgula). | `http://localhost:5173` |
| `LLM_API_KEY` | Liga o agente conversacional. Chave gratuita em [console.groq.com](https://console.groq.com) (sem cartão). Sem ela o bot só entende `/start`. | vazio |
| `LLM_URL_BASE` | Endereço de qualquer provedor compatível com a API da OpenAI. | `https://api.groq.com/openai/v1` |
| `LLM_MODELO_CONVERSA` | Modelo usado na conversa. | `openai/gpt-oss-120b` |
| `LLM_ESFORCO_DE_RACIOCINIO` | `low`, `medium` ou `high` para modelos que raciocinam (gpt-oss). Deixe vazio para os demais. | `low` |
| `LLM_BUSCA_NA_WEB` | Deixa o modelo pesquisar na web os livros que não conhece (ferramenta `browser_search` da Groq). Gasta cerca de 15 vezes mais tokens por consulta com busca e envia a pergunta também à Exa, que faz a busca. **Desligue (`false`) ao usar outro provedor**, que não entende essa ferramenta. | `true` |

### Agente conversacional

Qualquer mensagem de texto que não seja um comando (`/...`) é respondida pelo Christopher com um modelo de linguagem, sempre sobre livros e leitura. A versão 1 **não acessa a estante** do usuário.

- **Só chats vinculados conversam.** Quem ainda não fez o `/start` com código recebe a orientação de conectar pelo site e não gasta a cota do modelo.
- **Limites:** 4 mensagens por minuto por chat, texto de até 1.000 caracteres e histórico das últimas 4 falas (em memória: reiniciar o serviço apaga). A cota gratuita da Groq (cerca de 1.000 requisições e 200.000 tokens por dia, 8.000 tokens por minuto) é **compartilhada por todos os usuários**; ao estourar, o bot responde que não conseguiu pensar numa resposta e a conversa volta quando a cota renova.
- **Busca na web:** o modelo não conhece todos os livros, principalmente pelo título publicado no Brasil (ex.: *Devoradores de Estrelas* é *Project Hail Mary*, de Andy Weir). Com `LLM_BUSCA_NA_WEB=true`, ele pesquisa quando não conhece o livro. Uma consulta com busca gasta cerca de 3.600 tokens (contra cerca de 240 sem busca), então a cota gratuita comporta poucas por minuto; se estourar, o bot responde que não conseguiu pensar numa resposta. O que vem da web é tratado como informação, nunca como instrução.
- **Texto simples:** o Telegram mostra símbolos de Markdown (`**`, `#`, crases) como estão, então as respostas passam por uma limpeza que os remove, e os marcadores de citação da busca também.
- **Trocar de provedor ou de modelo** é só mudar `LLM_URL_BASE`, `LLM_MODELO_CONVERSA` e `LLM_API_KEY` (e esvaziar `LLM_ESFORCO_DE_RACIOCINIO` se o modelo não raciocina). A classe `ClienteOpenAiCompativel` serve a qualquer API no formato da OpenAI; um provedor com formato próprio (como a API da Anthropic) pede uma nova implementação de `ClienteLlm`.
- **Privacidade:** o texto das mensagens e o histórico recente são enviados ao provedor do modelo (hoje a Groq). Nome e identificadores do usuário não são enviados. Não use o plano gratuito do Google com usuários reais: os termos permitem usar o conteúdo para melhorar produtos e exigem plano pago na União Europeia.
- Sem `LLM_API_KEY`, o agente fica desativado e o serviço sobe normalmente.

### API para o front (conectar o Telegram)

| Método e rota | O que faz | Resposta |
|---|---|---|
| `POST /telegram/vinculos` com `{"usuarioId": "42"}` | Gera um link de conexão (código de uso único, válido por 15 min) | `201` `{"link": "https://t.me/<bot>?start=<código>"}` |
| `GET /telegram/vinculos/{usuarioId}` | Informa se o Telegram está conectado | `200` `{"conectado": true}` |
| `DELETE /telegram/vinculos/{usuarioId}` | Desconecta (pode ser repetido sem erro) | `204` |

Quando o usuário toca em "Iniciar" no bot, o `/start <código>` grava o vínculo. Se o código já foi usado ou expirou, o bot pede um novo link.

### API para o database-service (notificações)

Contrato combinado com quem cuida do `database-service` (campos `usuarioId`, `titulo` e `autor`). Ainda não há identificador de evento.

| Método e rota | Corpo | O que faz |
|---|---|---|
| `POST /notificacoes/livro-salvo` | `{"usuarioId": "42", "titulo": "Dom Casmurro", "autor": "Machado de Assis"}` (`autor` é opcional) | Avisa o usuário no Telegram que o livro foi salvo |

| Resposta | Significa |
|---|---|
| `200` `{"resultado": "ENVIADO"}` | A mensagem foi entregue ao Telegram |
| `200` `{"resultado": "SEM_VINCULO"}` | O usuário não conectou o Telegram: nada a fazer, **não é erro** |
| `502` `{"resultado": "FALHA_NO_ENVIO"}` | O Telegram recusou (rede, ou o usuário bloqueou o bot): vale tentar de novo |
| `503` | Este serviço está sem `TELEGRAM_BOT_TOKEN` |
| `400` | `usuarioId` ou `titulo` ausentes |

Sem identificador de evento ainda: se o chamador reenviar, o usuário pode receber a mensagem duas vezes.

### ⚠️ Problema conhecido no Windows: `mvnw spring-boot:run`

Se o caminho do projeto tiver caracteres não-ASCII (acentos, `°`, etc.), o goal `spring-boot:run` do `spring-boot-maven-plugin` falha com:

```
Erro: Não foi possível localizar nem carregar a classe principal ...
Causada por: java.lang.ClassNotFoundException: ...
```

Isso acontece mesmo com o `.class` compilado corretamente — é um bug de encoding do classloader do plugin ao montar o classpath a partir de um caminho com caracteres especiais (`-Dspring-boot.run.fork=false` não resolve).

**Workaround**: empacotar e rodar o jar diretamente, como no passo 2-3 acima, em vez de usar `spring-boot:run`. Rodar pela IDE (IntelliJ) também funciona normalmente, pois não usa esse goal do Maven.

## Testes

```bash
./mvnw test
```

Os testes de banco usam o Postgres em `localhost:8342`; suba com `docker compose up -d notification-db`. A pipeline **CI - Notification Service** (`.github/workflows/notification-service-ci.yml`) roda esses testes e valida o build da imagem em todo push e PR que mexe em `services/notification-service/`.

## Status

- [x] Cadastro/consulta de cliente por `telegram_chat_id` (JPA + Flyway)
- [x] Endpoint para receber o evento "livro salvo" do `database-service` (campos combinados; falta o `eventId`)
- [x] Integração com a Telegram Bot API (long-polling; `TelegramBotAgent` responde ao `/start`)
- [x] Vínculo entre o usuário do Shelf Scan e o chat do Telegram (link com código, endpoints para o front e CORS)
- [x] Agente conversacional (IA) no chat do bot (v1, sem acesso à estante; falta testar com chave real)
- [x] Formatador de mensagens (`MessageFormatterAgent`), por enquanto só template, sem IA
- [ ] Agente recomendador de livros
- [x] Container do próprio serviço no `docker-compose.yml`
- [x] CI (`notification-service-ci.yml`)
