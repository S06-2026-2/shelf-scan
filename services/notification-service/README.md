# Notification Service - Notificações + Agentes de IA

## Sobre o serviço

Responsável por três frentes do Shelf Scan:

1. Cadastro dos usuários do Telegram (vínculo entre `telegram_chat_id` e o usuário)
2. Notificar o usuário via Telegram quando um livro dele é reconhecido e salvo no `database-service`
3. Dois agentes de IA:
   - **Agente conversacional**: interage com o usuário pelo Telegram
   - **Agente recomendador**: sugere livros com base no gosto do usuário e nos livros que ele já tem salvos

## Como funciona (fluxo pretendido)

```
ocr-service extrai dados do livro
      │
      ▼
database-service salva o livro (Neo4j)
      │
      ▼
database-service avisa o notification-service (REST — contrato ainda em definição)
      │
      ▼
notification-service busca o Cliente pelo telegram_chat_id
      │
      ▼
Telegram Bot API envia a notificação ao usuário
```

O agente recomendador consulta os dados do usuário (gostos + livros salvos) para sugerir novas leituras; o agente conversacional atende o usuário diretamente pelo chat do bot.

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
└── Dockerfile
```

## Tecnologias

- **Java 21** / **Spring Boot 3.3.4**
- **Spring Web**, **Spring Data JPA**, **Spring Validation**, **Actuator**
- **PostgreSQL** — persistência dos clientes/usuários do Telegram
- **Flyway** — versionamento do schema
- Bot do Telegram e stack de IA (Spring AI/LangChain4j): ainda a definir

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

## Status

- [x] Cadastro/consulta de cliente por `telegram_chat_id` (JPA + Flyway)
- [ ] Endpoint para receber o evento "livro salvo" do `database-service`
- [ ] Integração com a Telegram Bot API (webhook ou polling)
- [ ] Agente conversacional (`TelegramBotAgent`)
- [ ] Agente formatador de mensagens (`MessageFormatterAgent`)
- [ ] Agente recomendador de livros
- [ ] Container do próprio serviço no `docker-compose.yml` (hoje só o banco está lá)
- [ ] CI (`notification-service-ci.yml`)
