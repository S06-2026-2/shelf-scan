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
| Estante, amigos, solicitações, bibliotecas, recomendações | **Exemplo**, em memória (recarregar a página volta ao início) |
| Reconhecimento do OCR | **Simulado** enquanto `VITE_OCR_API_URL` estiver vazia; a tela avisa "Modo demonstração" |

Não há login: todas as telas usam o usuário de exemplo de `src/mocks/usuario.ts` (`teste-1`).

## Variáveis de ambiente

Copie `.env.example` para `.env.local` e ajuste (o `.env.local` não vai para o Git). As variáveis `VITE_*` são embutidas no build.

| Variável | Para quê | Padrão |
|---|---|---|
| `VITE_NOTIFICATION_API_URL` | Endereço do `notification-service` | `http://localhost:8341` |
| `VITE_OCR_API_URL` | Endereço do `ocr-service`. Vazia = modo demonstração | vazia |

Contrato proposto para o OCR (ainda a combinar com quem cuida do serviço): `POST {VITE_OCR_API_URL}/reconhecer`, multipart com o campo `imagem`; resposta JSON `{ titulo, autor, editora?, ano?, genero?, sinopse? }`.

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

### Cartão "Avisos no Telegram"

Ele fala com o `notification-service`, então precisa dele no ar (porta 8341) e com os endpoints de vínculo (`/telegram/vinculos`).

> **Temporário:** esses endpoints, o bot e o passo a passo para subi-los (no `services/notification-service/README.md`) estão na branch `feat-telegram` e ainda não chegaram ao `dev`. Enquanto isso, ou o serviço roda a partir dessa branch, ou o cartão mostra o erro descrito abaixo. Remover este aviso quando o PR for mergeado.

- **Com o serviço no ar:** o cartão mostra se o usuário está conectado. "Conectar Telegram" gera o link, abre o Telegram e a página confere a conexão sozinha a cada 3 segundos.
- **Sem o serviço:** o cartão mostra uma mensagem de erro e "Tentar de novo". É o comportamento esperado.
- **Cuidado:** "Desconectar" remove o vínculo **real** do usuário `teste-1` no serviço. É só conectar de novo para refazer.

## Estrutura

```
frontend/
├── public/            # favicon e logo (SVG)
├── src/
│   ├── api/           # chamadas aos backends (telegram.ts, ocr.ts)
│   ├── components/    # peças reutilizáveis (Layout, Logo, CapaLivro, CartaoLivro...)
│   ├── estado/        # estado compartilhado da estante (LivrosProvider)
│   ├── mocks/         # dados de exemplo enquanto não há API
│   ├── pages/         # uma pasta/arquivo por tela (Perfil, Estante, AdicionarLivro...)
│   ├── tipos/         # tipos TypeScript (Livro, Pessoa...)
│   ├── utilitarios/   # funções soltas (redução de imagem no navegador)
│   ├── index.css      # paleta, tipografia e estilos base
│   └── main.tsx       # ponto de entrada
├── index.html
└── .env.example
```

Cada componente ou página tem o seu `.css` ao lado. As cores estão em variáveis CSS no `index.css`, com versão clara e escura (segue o tema do sistema). A paleta é azul cobalto com detalhes em dourado.

## Problemas comuns

- **Erro de CORS ao conectar o Telegram:** o `notification-service` só libera `http://localhost:5173` por padrão. Se a porta 5173 estiver ocupada, o Vite sobe em outra e as chamadas são bloqueadas. Suba com `npm run dev -- --port 5173 --strictPort` (para falhar em vez de trocar de porta) ou ajuste `CORS_ORIGENS_PERMITIDAS` no serviço.
- **O cartão do Telegram mostra erro:** o `notification-service` não está no ar ou não tem os endpoints de vínculo.
- **Alterei o `.env.local` e nada mudou:** as variáveis são lidas na subida; reinicie o `npm run dev`.

## Publicação (Vercel ou similar)

O front é estático (`npm run build` gera o `dist/`). Num host como o Vercel:

- Diretório raiz do projeto: `frontend`; comando de build: `npm run build`; saída: `dist`.
- Defina `VITE_NOTIFICATION_API_URL` e `VITE_OCR_API_URL` nas variáveis de ambiente do projeto.
- Os backends precisam rodar em outro lugar (hosts de container), com **HTTPS** e **CORS** liberado para o endereço do front.
- Como o roteamento é feito no navegador, o host precisa devolver o `index.html` para qualquer rota (um *rewrite* de `/(.*)` para `/index.html`); sem isso, recarregar `/estante` dá 404. Esse arquivo (`vercel.json`) ainda não existe no repositório.

## Limitações conhecidas

- Sem autenticação: usuário de exemplo fixo.
- Sem API de livros: a estante e o "Salvar na estante" funcionam só em memória.
- OCR simulado até existir a API do `ocr-service`.
- Sem testes unitários.
