# Shelf Scan

## Sobre o projeto
Projeto da matéria Engenharia de Produto(S06) do Inatel.


Uso de visão computacional para capturar
a imagem da capa e lombada de um livro e
identificar nome e autor. Por uso de agente
de IA, feito o enriquecimento de informações
do livro com informações extras. Integração
com um bot do Telegram para notificação do 
usuário, além de uso de agente de IA para fazer
recomendações ao usuário. Possível expansão para
tornar o projeto numa rede social.

## Integrantes
Felipe Ferreira - GES

Felipe Silva Loschi - GES 601

Pedro Henrique Ribeiro Dias - GES 528

# Mapeamento de Portas — Projeto Shelf Scan

Documentação oficial das faixas de portas locais e serviços do projeto **Shelf Scan**. Todas as portas locais utilizam o prefixo **`83xx`** (referência a **S**helf **S**can).

---

## Faixas de Portas por Serviço

| Serviço / Módulo | Faixa Reservada | Descrição / Protocolo |
| ---- | ---- | ---- | 
| **`ocr-service`** | `8301 - 8320` | API Principal de Visão Computacional (Python / FastAPI) |
| **`database-service`** | `8321 - 8340` | API Principal do Microsserviço de Banco de Dados (Spring Boot) |
| **`notification-service`** | `8341 - 8360` | API Principal e Bot do Telegram (Spring Boot) |
| **Reserva / Futuro** | `8361 - 8399` | Reservado para expansões, métricas e novos serviços |

---

## Portas Utilizadas
| Porta | Aplicação | Descrição |
| --- | --- | --- |
| **8321** | Neo4j | Conexão com o Neo4j database-service |
