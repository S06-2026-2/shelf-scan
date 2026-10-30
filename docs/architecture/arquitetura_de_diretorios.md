# Arquitetura de Diretórios do Shelf Scan

```Markdown
repositorio/
├── .github/
│   ├── ISSUE_TEMPLATE/                   # Templates para tarefas/bugs no GitHub
│   └── workflows/                        # Pipelines de CI/CD
│       ├── database-service-ci.yml
│       ├── notification-service-ci.yml
│       ├── ocr-service-ci.yml
│       └── [[social-network-backend-ci.yml]]  # (Futuro)
│
├── docs/                                 
│   ├── architecture/                     # Diagramas e fluxos de dados
│   ├── api/                              # Especificações OpenAPI/Swagger
│   └── process/                          # Opicional: Definindo DoD, DoR e padrões de código
│
├── services/                             # Central dos microserviços
│   │
│   ├── database-service/                 # [Java] Camada/Serviço de Persistência e Dados
│   │   ├── src/ # e pasta ia/
│   │   ├── pom.xml                      
│   │   ├── Dockerfile
│   │   └── README.md
│   │
│   ├── notification-service/             # [Java] Sistema de Notificações + Telegram
│   │   ├── src/ # e pasta ia/
│   │   ├── pom.xml                       
│   │   ├── Dockerfile
│   │   └── README.md
│   │
│   └── ocr-service/                      # [Python] Serviço de Reconhecimento Óptico (OCR)
│       ├── app/
│       ├── tests/
│       ├── requirements.txt     
│       ├── Dockerfile
│       └── README.md
│
├── .gitignore                            
├── .editorconfig                         # Opiconal: Padronização de formatação de código entre a equipe
├── docker-compose.yml                    # Sobe toda a aplicação localmente com 1 comando
├── docker-compose.override.yml           # Variáveis locais/dev (banco de dados local, etc.)
└── README.md                             
```