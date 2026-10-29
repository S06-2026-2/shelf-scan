# Shelf Scan: Frontend

Interface web do Shelf Scan: perfil, estante virtual e o fluxo de adicionar livro por foto (OCR).

- **React 19 + TypeScript + Vite**, CSS puro (sem Tailwind) e `react-router-dom`.
- Nomes de arquivos, componentes e variáveis em português, no mesmo estilo dos serviços de backend.

## Como subir

Pré-requisitos: **Node 24** (o mesmo da pipeline) e npm.

```bash
cd frontend
npm install
npm run dev
```

Abra **http://localhost:5173**. A página recarrega sozinha quando um arquivo muda; `Ctrl+C` encerra.

| Comando | O que faz |
|---|---|
| `npm run dev` | Servidor de desenvolvimento (porta 5173) |
| `npm run build` | Verifica os tipos (TypeScript) e gera a versão de produção em `dist/` |
| `npm run preview` | Serve o `dist/` localmente, para ver como ficou o build |
| `npm run lint` | Análise estática com oxlint |

## Telas

| Rota | Tela |
|---|---|
| `/perfil` | Perfil: números, cartão "Avisos no Telegram", solicitações de amizade, amigos, bibliotecas e recomendações |
| `/estante` | Grade de livros com filtros por status de leitura |
| `/estante/adicionar` | Captura da foto do livro (OCR) |
| `/estante/adicionar/confirmar` | Confirmação dos dados reconhecidos antes de salvar |

A raiz (`/`) e rotas desconhecidas levam ao perfil. A tela de confirmação só funciona vindo da captura; abrir o endereço direto volta para a captura.

## O que é real e o que é exemplo

| Parte | Origem dos dados |
|---|---|
| Conexão do Telegram (cartão no perfil) | **Real**: chama o `notification-service` |
| Estante e "Salvar na estante" | **Real** quando `VITE_DATABASE_API_URL` e `VITE_USUARIO_ID` estão definidas (chama o `database-service`); senão, **exemplo** em memória |
| Amigos, solicitações, bibliotecas, recomendações | **Exemplo**, em memória (recarregar a página volta ao início): a API ainda não tem essas relações |
| Reconhecimento do OCR | **Simulado** enquanto `VITE_OCR_API_URL` estiver vazia; a tela avisa "Modo demonstração" |

Não há login: todas as telas usam um usuário fixo. Sem `VITE_USUARIO_ID`, é o usuário de exemplo de `src/mocks/usuario.ts` (`teste-1`); com ela, o `id` passa a ser o do banco (e o do cartão do Telegram). Nome e iniciais exibidos seguem de exemplo.

## Variáveis de ambiente

Copie `.env.example` para `.env.local` e ajuste (o `.env.local` não vai para o Git). As variáveis `VITE_*` são embutidas no build.

| Variável | Para quê | Padrão |
|---|---|---|
| `VITE_NOTIFICATION_API_URL` | Endereço do `notification-service` | `http://localhost:8341` |
| `VITE_OCR_API_URL` | Endereço do `ocr-service`. Vazia = modo demonstração | vazia |
| `VITE_DATABASE_API_URL` | Endereço do `database-service` já com o prefixo da API. No desenvolvimento, `/api/v1` (o Vite repassa para `http://localhost:8321`). Vazia = estante de exemplo | vazia |
| `VITE_USUARIO_ID` | Id numérico do usuário no banco. Vazia = usuário de exemplo `teste-1` | vazia |

**Contrato do OCR:** `services/ocr-service/docs/contrato-api.md` e a rota em `services/ocr-service/api/main.py`. O front chama `POST {VITE_OCR_API_URL}/v1/extraction` (multipart com a foto no campo `image`) e converte a resposta, que é uma lista de livros `{nome, volume, autores, editora}` (o que não foi identificado vem `null`) mais o texto bruto e um indicador (`structured`, `unstructured` ou `no_text`, mostrados como `estruturado`, `não estruturado` e `nenhum texto`). Os erros vêm como `{code, message}` e o front age pelo `code`. Essa conversão fica em `src/api/ocrContrato.ts`; se o nome do campo mudar, só a constante `CAMPO_DA_FOTO` em `src/api/ocr.ts` muda.

Enquanto o agente que estrutura o texto não existir, o OCR real devolve só o **texto bruto**; nesse caso a tela de confirmação mostra o texto lido e pede para preencher título e autor. Ano, gênero e sinopse não vêm do OCR (o enriquecimento é do `database-service`) e ficam para preencher à mão.

**Contrato do banco:** `docs/api/database-service.md`. O front lê a estante padrão do usuário (`GET /usuario/{id}/estante` e `.../estante/{estanteId}/livro`) e salva com `POST /usuario/{id}/livro`; as conversões ficam em `src/api/livrosContrato.ts` e as chamadas em `src/api/livros.ts`. O banco guarda título, autores, volume, páginas, sinopse e tags (o gênero vira uma tag) e calcula o progresso de leitura; **não guarda editora nem ano**, por isso esses campos não aparecem na confirmação. O `database-service` ainda não libera CORS: em desenvolvimento o Vite repassa `/api` para ele (`server.proxy` em `vite.config.ts`).

## Como testar

### Automático

```bash
npm run lint
npm run build
```

É exatamente o que a pipeline **CI - Frontend** roda (`.github/workflows/frontend-ci.yml`) em todo push e PR que mexe em `frontend/`. Ainda não há testes unitários.

### Manual, tela por tela

Suba o front (`npm run dev`) e confira:

1. **Estante** (`/estante`): clique nos filtros; a contagem de cada botão deve bater com os livros exibidos.
2. **Adicionar livro** (`/estante/adicionar`): escolha ou arraste qualquer JPG, PNG ou WebP (até 10 MB) e clique em "Reconhecer livro". No modo demonstração o resultado é sempre "Dom Casmurro". Ajuste os campos, escolha o status e salve: o livro aparece no topo da estante. Um arquivo que não seja imagem deve mostrar erro.
3. **Perfil** (`/perfil`): aceite e recuse solicitações (os números do cabeçalho mudam) e use "Tornar-se membro" numa biblioteca.
4. **Cartão do Telegram** (`/perfil`): veja abaixo.

### Estante com o banco de dados

Precisa do `database-service` e do Neo4j no ar (`docker compose up -d --build neo4j database-service`; a API fica em `http://localhost:8321/api/v1`).

1. Crie um usuário de teste e anote o `id` da resposta (ele nasce com a estante padrão "Minha estante"):

   ```powershell
   Invoke-RestMethod -Method Post -Uri http://localhost:8321/api/v1/usuario -ContentType 'application/json' -Body '{"nome":"Usuario Teste","email":"usuario-teste@exemplo.com"}'
   ```

2. Em `.env.local`, defina `VITE_DATABASE_API_URL=/api/v1` e `VITE_USUARIO_ID=<id anotado>`, e reinicie o `npm run dev -- --port 5173 --strictPort`.
3. `/estante` abre vazia. Adicione um livro como "Lendo" com total de páginas e página atual: o cartão mostra a porcentagem calculada pelo banco. Um livro "Lido" mostra o mês de conclusão.
4. Sem o banco no ar, a estante mostra o erro e o botão "Tentar de novo"; com um `VITE_USUARIO_ID` que não existe, a mensagem aponta o id.
5. Ao terminar, apague o usuário de teste (apaga também as estantes e os livros dele): `Invoke-RestMethod -Method Delete -Uri http://localhost:8321/api/v1/usuario/<id>`.

O id do cartão do Telegram passa a ser o do banco; o vínculo que existia para `teste-1` não aparece mais, e é só conectar de novo.

### Cartão "Avisos no Telegram"

Ele fala com o `notification-service`, então precisa dele no ar (porta 8341) e com os endpoints de vínculo (`/telegram/vinculos`). O passo a passo para subir o serviço e o bot está em `services/notification-service/README.md`.

- **Com o serviço no ar:** o cartão mostra se o usuário está conectado. "Conectar Telegram" gera o link, abre o Telegram e a página confere a conexão sozinha a cada 3 segundos.
- **Sem o serviço:** o cartão mostra uma mensagem de erro e "Tentar de novo". É o comportamento esperado.
- **Cuidado:** "Desconectar" remove o vínculo **real** do usuário `teste-1` no serviço. É só conectar de novo para refazer.

## Estrutura

```
frontend/
├── public/            # favicon e logo (SVG)
├── src/
│   ├── api/           # chamadas aos backends (telegram.ts, ocr.ts, livros.ts) e a conversão dos contratos (ocrContrato.ts, livrosContrato.ts)
│   ├── components/    # peças reutilizáveis (Layout, Logo, CapaLivro, CartaoLivro...)
│   ├── estado/        # estado compartilhado da estante (LivrosProvider)
│   ├── mocks/         # dados de exemplo enquanto não há API
│   ├── pages/         # uma pasta/arquivo por tela (Perfil, Estante, AdicionarLivro...)
│   ├── tipos/         # tipos TypeScript (Livro, Pessoa...)
│   ├── utilitarios/   # funções soltas (redução de imagem no navegador, cor e texto do livro)
│   ├── index.css      # paleta, tipografia e estilos base
│   └── main.tsx       # ponto de entrada
├── index.html
└── .env.example
```

Cada componente ou página tem o seu `.css` ao lado. As cores estão em variáveis CSS no `index.css`, com versão clara e escura (segue o tema do sistema). A paleta é azul cobalto com detalhes em dourado.

## Problemas comuns

- **Erro de CORS ao conectar o Telegram:** o `notification-service` só libera `http://localhost:5173` por padrão. Se a porta 5173 estiver ocupada, o Vite sobe em outra e as chamadas são bloqueadas. Suba com `npm run dev -- --port 5173 --strictPort` (para falhar em vez de trocar de porta) ou ajuste `CORS_ORIGENS_PERMITIDAS` no serviço.
- **O cartão do Telegram mostra erro:** o `notification-service` não está no ar ou não tem os endpoints de vínculo.
- **"Não foi possível falar com o serviço de OCR" com o serviço no ar:** o `ocr-service` só responde ao navegador para as origens listadas em `CORS_ORIGENS_PERMITIDAS` (padrão `http://localhost:5173`). Se o front estiver em outra porta ou endereço, inclua-o nessa variável do serviço.
- **Alterei o `.env.local` e nada mudou:** as variáveis são lidas na subida; reinicie o `npm run dev`.
- **A estante mostra "Não foi possível falar com o banco de dados":** o `database-service` não está no ar, ou `VITE_DATABASE_API_URL` não é `/api/v1` (sem o proxy do Vite, o navegador bloqueia a chamada por CORS).
- **A estante mostra "Usuário não encontrado no banco":** o `VITE_USUARIO_ID` não existe. Os ids vêm do Neo4j e podem mudar se o banco for recriado; crie o usuário de novo.

## Publicação (Vercel ou similar)

O front é estático (`npm run build` gera o `dist/`). Num host como o Vercel:

- Diretório raiz do projeto: `frontend`; comando de build: `npm run build`; saída: `dist`.
- Defina `VITE_NOTIFICATION_API_URL`, `VITE_OCR_API_URL`, `VITE_DATABASE_API_URL` e `VITE_USUARIO_ID` nas variáveis de ambiente do projeto.
- Os backends precisam rodar em outro lugar (hosts de container), com **HTTPS** e **CORS** liberado para o endereço do front.
- O proxy do Vite só existe no `npm run dev`. Publicado, o `VITE_DATABASE_API_URL` deve ser o endereço completo do `database-service`, que por enquanto **não libera CORS**: até isso mudar, só funciona em desenvolvimento.
- Como o roteamento é feito no navegador, o host precisa devolver o `index.html` para qualquer rota (um *rewrite* de `/(.*)` para `/index.html`); sem isso, recarregar `/estante` dá 404. Esse arquivo (`vercel.json`) ainda não existe no repositório.

## Limitações conhecidas

- Sem autenticação: usuário de exemplo fixo.
- A estante só mostra a estante padrão do usuário; não há telas para outras estantes nem para empréstimos (a API já os tem).
- Amigos, solicitações, bibliotecas e recomendações continuam de exemplo (a API não tem essas relações).
- Sem `VITE_DATABASE_API_URL` e `VITE_USUARIO_ID`, a estante e o "Salvar na estante" funcionam só em memória.
- Sem `VITE_OCR_API_URL` o OCR é simulado; com ela, o reconhecimento real depende do `ocr-service` no ar (`http://localhost:8301` pelo compose).
- Sem testes unitários.
