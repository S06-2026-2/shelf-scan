classe: br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship

# Spec: EmprestimoRelationship

## Contexto

Representa o empréstimo de um livro para um usuário. No grafo é o relacionamento `LENT_TO` (Livro → Usuario)
com as datas do empréstimo como propriedades. Um livro guarda todos os empréstimos que já teve, então o mesmo
livro tem vários desses relacionamentos: os devolvidos (histórico) e no máximo um ativo.

Um empréstimo está **ativo** enquanto não tem data de devolução, e passa a ser **devolvido** quando a devolução
é registrada. "Está emprestado?" é sempre calculado a partir disso, não existe um campo guardado para isso.

Toda vez que a spec fala em **hoje**, é o parâmetro `hoje` recebido pelo método. A classe não consulta o relógio
nem conhece fuso horário: quem chama (o service) é responsável por passar a data atual de Brasília
(`America/Sao_Paulo`), obtida do `Clock` da aplicação. As datas são só dia, sem hora (`LocalDate`).

A classe tem duas formas de ser criada:
- pelo construtor/fábrica usado pelo sistema, que valida tudo (regras R1 a R3);
- pelo construtor de persistência (`@PersistenceCreator`), usado pelo Spring Data Neo4j para carregar do banco,
  que **não valida**. Por isso a classe também precisa se comportar bem com dados inconsistentes vindos do banco
  (regra R14).

As datas não podem ser alteradas livremente por setter. A devolução só acontece por `registrarDevolucao(data, hoje)`.

O empréstimo também guarda a **leitura de quem pegou o livro**: status, página atual e data de conclusão. Assim cada
pessoa que pega o mesmo livro tem a sua própria leitura, e ela fica no histórico depois da devolução. A leitura do
dono fica no `LivroNode`, não aqui. Ela também não tem setter: só muda por `atualizarLeitura(...)`.

A **porcentagem de leitura não é guardada**: é calculada a partir da página atual e do número de páginas do livro.
Como o relacionamento não tem acesso ao livro (só ao usuário de destino), quem chama passa o número de páginas do
livro como parâmetro (`totalPaginas`, `null` quando o livro não tem esse dado).

## API esperada

Assinaturas que a classe deve ter. Os testes usam estas formas; o que ainda não existe no código fica sem teste
até ser criado (pode começar como stub com `throw new UnsupportedOperationException()`).

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `EmprestimoRelationship(UsuarioNode usuario, LocalDate dataEmprestimo, LocalDate dataPrevistaDevolucao)` | Criação pelo sistema, com validação | R1–R4 |
| `@PersistenceCreator EmprestimoRelationship(Long id, LocalDate dataEmprestimo, LocalDate dataPrevistaDevolucao, LocalDate dataDevolucao, UsuarioNode usuario, StatusLeitura statusLeitura, Integer paginaAtual, LocalDate dataConclusaoLeitura)` | Usado pelo Spring Data Neo4j ao carregar do banco, **sem validação** (pode ter visibilidade de pacote) | R14 |
| `void registrarDevolucao(LocalDate dataDevolucao, LocalDate hoje)` | Encerra o empréstimo | R5–R7 |
| `boolean isEmprestado()` / `boolean isDevolvido()` | Estado do empréstimo | R4, R5 |
| `int diasParaVencimento(LocalDate hoje)` | Dias até a data prevista | R8, R9, R14 |
| `boolean isVencido(LocalDate hoje)` | Se está vencido | R10, R11, R14 |
| `boolean estaVencendo(LocalDate hoje)` | Se está vencendo | R12, R13, R14 |
| `void atualizarLeitura(StatusLeitura status, Integer paginaAtual, Integer totalPaginas, LocalDate hoje)` | Atualiza a leitura de quem pegou o livro | R16–R20 |
| `StatusLeitura getStatusLeitura()` / `Integer getPaginaAtual()` / `LocalDate getDataConclusaoLeitura()` | Leitura de quem pegou o livro | R15–R20 |
| `Integer porcentagemLeitura(Integer totalPaginas)` | Porcentagem calculada (não é campo guardado) | R21 |

`StatusLeitura` é um enum em `br.com.virabrequim.database_service.domain` com os valores `QUERO_LER`, `LENDO` e
`LIDO`. As datas e os campos de leitura não têm setter público.

## Regras

**Criação**

- **R1.** A data do empréstimo é obrigatória. Criar um empréstimo sem ela lança `IllegalArgumentException`.
  Ela pode ser no futuro (empréstimo agendado).
- **R2.** A data prevista de devolução é obrigatória. Criar um empréstimo sem ela lança `IllegalArgumentException`.
- **R3.** O prazo mínimo é de 1 dia: a data prevista de devolução tem que ser depois da data do empréstimo.
  Se for no mesmo dia ou antes, lança `IllegalArgumentException`.
- **R4.** Um empréstimo recém-criado está ativo: não tem data de devolução, `isEmprestado()` retorna `true`
  e `isDevolvido()` retorna `false`.

**Devolução**

- **R5.** `registrarDevolucao(data, hoje)` encerra o empréstimo. Depois disso `isEmprestado()` retorna `false`
  e `isDevolvido()` retorna `true`.
- **R6.** A data de devolução pode ser no mesmo dia do empréstimo ou depois, até hoje.
  - data `null` → `IllegalArgumentException`;
  - data antes da data do empréstimo → `IllegalArgumentException`;
  - data no futuro (depois de hoje) → `IllegalArgumentException`.

  Consequência: um empréstimo agendado não pode ser devolvido antes de começar.
- **R7.** Um empréstimo só pode ser devolvido uma vez. Chamar `registrarDevolucao` num empréstimo já devolvido
  lança `IllegalStateException`.

**Dias para o vencimento**

- **R8.** Para um empréstimo ativo, `diasParaVencimento(hoje)` retorna quantos dias faltam de hoje até a data prevista
  de devolução:
  - positivo → ainda faltam dias;
  - `0` → vence hoje;
  - negativo → está atrasado (ex.: `-2` = 2 dias de atraso). O valor negativo é usado para avisar o atraso.
- **R9.** Para um empréstimo devolvido, `diasParaVencimento(hoje)` lança `IllegalStateException`, porque não faz
  sentido contar dias de um empréstimo que já acabou.

**Vencido**

- **R10.** Um empréstimo ativo está vencido (`isVencido(hoje)` = `true`) só a partir do dia **seguinte** à data prevista.
  No próprio dia previsto ele ainda não venceu.
- **R11.** Um empréstimo devolvido nunca está vencido: `isVencido(hoje)` retorna `false`, mesmo que a devolução tenha
  sido feita com atraso.

**Vencendo**

- **R12.** Um empréstimo ativo e não vencido está vencendo (`estaVencendo(hoje)` = `true`) quando pelo menos uma das
  condições vale:
  - faltam **3 dias ou menos** para o vencimento (`diasParaVencimento(hoje) <= 3`); **ou**
  - o tempo que falta é **15% ou menos** do prazo total
    (`diasParaVencimento(hoje) / dias entre empréstimo e data prevista <= 0,15`).

  Os dois limites contam como vencendo (são inclusivos).
- **R13.** `estaVencendo(hoje)` retorna `false` quando o empréstimo já está vencido ou já foi devolvido.

**Dados inconsistentes vindos do banco**

- **R14.** Se um empréstimo **ativo** chegar sem data prevista de devolução (só é possível pelo construtor de
  persistência), `diasParaVencimento(hoje)`, `isVencido(hoje)` e `estaVencendo(hoje)` lançam `IllegalStateException` com a
  mensagem "Empréstimo sem data prevista de devolução".
  A verificação de devolvido vem **antes**: se o empréstimo já foi devolvido, valem R9, R11 e R13, mesmo sem
  data prevista.

**Leitura de quem pegou o livro**

- **R15.** Um empréstimo recém-criado (construtor do sistema) começa com status `QUERO_LER`, página atual `0` e data
  de conclusão `null`.
- **R16.** `status` é obrigatório. `atualizarLeitura(null, ...)` lança `IllegalArgumentException`.
- **R17.** Por status:
  - `QUERO_LER`: a página atual passa a ser `0` e a data de conclusão `null`. A `paginaAtual` recebida é ignorada.
  - `LENDO`: a página atual é obrigatória e não pode ser negativa (`0` é aceito). Se `totalPaginas` não for `null`, a
    página atual não pode passar dele. `null`, negativa ou maior que `totalPaginas` lança
    `IllegalArgumentException`. Com `totalPaginas` `null`, não há limite superior. A data de conclusão passa a ser
    `null`.
  - `LIDO`: a página atual passa a ser `totalPaginas` (`null` se ele for `null`) e a data de conclusão passa a ser
    `hoje`. A `paginaAtual` recebida é ignorada.
- **R18.** Quando `atualizarLeitura` lança exceção, a leitura não muda.
- **R19.** A leitura fica guardada depois da devolução: `registrarDevolucao` não altera status, página atual nem data
  de conclusão.
- **R20.** Num empréstimo devolvido, `atualizarLeitura` lança `IllegalStateException` (a verificação de devolvido vem
  antes da validação do status e da página).
- **R21.** `porcentagemLeitura(totalPaginas)` é calculada a cada chamada (vale também para empréstimo devolvido):
  - status `null` (empréstimo antigo vindo do banco) → `null`;
  - `QUERO_LER` → `0`;
  - `LIDO` → `100` (mesmo com `totalPaginas` `null`);
  - `LENDO` com `totalPaginas` → `paginaAtual * 100 / totalPaginas`, **arredondado para baixo** (inteiro), limitado
    a `100`;
  - `LENDO` com `totalPaginas` `null` → `null`.

## Exemplos

Nos exemplos, o parâmetro **hoje = 10/03/2026**, a não ser quando estiver escrito outro dia. Nas tabelas, a coluna
"Hoje" é o valor passado para os três métodos.

**Criação**

- **E1 (R3, R4).** Dado empréstimo em 01/03/2026 com prevista para 02/03/2026, Quando crio o empréstimo,
  Então ele é criado, `isEmprestado()` = `true` e `isDevolvido()` = `false`.
- **E2 (R3).** Dado empréstimo em 01/03/2026 com prevista para 01/03/2026, Quando crio, Então `IllegalArgumentException`.
- **E3 (R3).** Dado empréstimo em 01/03/2026 com prevista para 28/02/2026, Quando crio, Então `IllegalArgumentException`.
- **E4 (R1).** Dado empréstimo sem data de empréstimo e prevista para 12/03/2026, Quando crio, Então `IllegalArgumentException`.
- **E5 (R2).** Dado empréstimo em 01/03/2026 sem data prevista, Quando crio, Então `IllegalArgumentException`.
- **E24 (R1, R4).** Dado empréstimo em 15/03/2026 (depois de hoje) com prevista para 25/03/2026, Quando crio,
  Então ele é criado.

**Devolução** (empréstimo em 01/03/2026, prevista para 12/03/2026)

- **E6 (R5, R6).** Quando registro a devolução em 10/03/2026 (hoje), Então `isDevolvido()` = `true` e `isEmprestado()` = `false`.
- **E7 (R6).** Quando registro a devolução em 01/03/2026 (mesmo dia do empréstimo), Então é aceita.
- **E8 (R6).** Quando registro a devolução em 28/02/2026, Então `IllegalArgumentException`.
- **E9 (R6).** Quando registro a devolução em 11/03/2026 (amanhã), Então `IllegalArgumentException`.
- **E25 (R6).** Dado empréstimo agendado em 15/03/2026 com prevista para 25/03/2026, Quando registro a devolução
  em 10/03/2026 (hoje), Então `IllegalArgumentException` (a devolução seria antes do empréstimo).
- **E10 (R6).** Quando registro a devolução com data `null`, Então `IllegalArgumentException`.
- **E11 (R7).** Dado que a devolução já foi registrada em 05/03/2026, Quando registro de novo em 10/03/2026,
  Então `IllegalStateException`.

**Vencimento** (empréstimo em 01/03/2026, prevista para 12/03/2026, ativo)

| Exemplo | Hoje | `diasParaVencimento(hoje)` | `isVencido(hoje)` | `estaVencendo(hoje)` | Regras |
| --- | --- | --- | --- | --- | --- |
| E12 | 05/03/2026 | 7 | `false` | `false` | R8, R12 |
| E13 | 09/03/2026 | 3 | `false` | `true` | R8, R12 |
| E14 | 12/03/2026 | 0 | `false` | `true` | R8, R10, R12 |
| E15 | 13/03/2026 | -1 | `true` | `false` | R8, R10, R13 |
| E16 | 20/03/2026 | -8 | `true` | `false` | R8, R10, R13 |

**Regra dos 15%** (empréstimo em 01/03/2026, prevista para 10/04/2026, prazo de 40 dias, ativo)

| Exemplo | Hoje | `diasParaVencimento(hoje)` | % do prazo | `estaVencendo(hoje)` | Regras |
| --- | --- | --- | --- | --- | --- |
| E17 | 03/04/2026 | 7 | 17,5% | `false` | R12 |
| E18 | 04/04/2026 | 6 | 15% | `true` | R12 |

**Devolvido** (empréstimo em 01/03/2026, prevista para 05/03/2026, devolvido em 08/03/2026, ou seja, com atraso)

- **E19 (R9).** Quando chamo `diasParaVencimento(hoje)`, Então `IllegalStateException`.
- **E20 (R11).** Quando chamo `isVencido(hoje)`, Então `false`.
- **E21 (R13).** Quando chamo `estaVencendo(hoje)`, Então `false`.

**Dados do banco** (carregado pelo construtor de persistência)

- **E22 (R14).** Dado empréstimo ativo em 01/03/2026 sem data prevista, Quando chamo `diasParaVencimento(hoje)`,
  `isVencido(hoje)` ou `estaVencendo(hoje)`, Então `IllegalStateException` com a mensagem
  "Empréstimo sem data prevista de devolução".
- **E23 (R14, R11, R13, R9).** Dado empréstimo em 01/03/2026 sem data prevista e devolvido em 05/03/2026,
  Quando chamo `isVencido(hoje)` e `estaVencendo(hoje)`, Então os dois retornam `false`. Quando chamo
  `diasParaVencimento(hoje)`, Então `IllegalStateException` (pela R9, não pela falta da data).

**Leitura** (empréstimo em 01/03/2026, prevista para 12/03/2026, ativo; livro com **200 páginas**, ou seja,
`totalPaginas` = 200 nas chamadas)

- **E26 (R15, R21).** Quando crio o empréstimo, Então status `QUERO_LER`, página atual 0, conclusão `null` e
  `porcentagemLeitura(200)` = 0.

| Exemplo | Chamada | Status | Página atual | `porcentagemLeitura(200)` | Conclusão | Regras |
| --- | --- | --- | --- | --- | --- | --- |
| E27 | `atualizarLeitura(LENDO, 80, 200, hoje)` | `LENDO` | 80 | 40 | `null` | R17, R21 |
| E28 | `atualizarLeitura(LENDO, 133, 200, hoje)` | `LENDO` | 133 | 66 | `null` | R17, R21 |
| E29 | `atualizarLeitura(LIDO, 10, 200, hoje)` | `LIDO` | 200 | 100 | 10/03/2026 | R17, R21 |
| E30 | `atualizarLeitura(QUERO_LER, 70, 200, hoje)` | `QUERO_LER` | 0 | 0 | `null` | R17, R21 |

- **E31 (R16, R17, R18).** Dado a leitura `LENDO` na página 80, Quando chamo `atualizarLeitura(null, 90, 200, hoje)`,
  `atualizarLeitura(LENDO, null, 200, hoje)`, `atualizarLeitura(LENDO, -1, 200, hoje)` ou
  `atualizarLeitura(LENDO, 201, 200, hoje)`, Então `IllegalArgumentException`, e a leitura continua `LENDO` na
  página 80.
- **E32 (R19).** Dado a leitura `LENDO` na página 120, Quando registro a devolução em 10/03/2026, Então a leitura
  continua `LENDO` na página 120.
- **E33 (R20).** Dado o empréstimo devolvido em 05/03/2026, Quando chamo `atualizarLeitura(LIDO, null, 200, hoje)` ou
  `atualizarLeitura(null, null, 200, hoje)`, Então `IllegalStateException`.

**Livro sem número de páginas** (`totalPaginas` = `null`)

- **E34 (R17, R21).** Quando chamo `atualizarLeitura(LENDO, 500, null, hoje)`, Então fica `LENDO` na página 500 e
  `porcentagemLeitura(null)` = `null`.
- **E35 (R17, R21).** Quando chamo `atualizarLeitura(LIDO, 30, null, hoje)`, Então fica `LIDO`, página atual `null`,
  conclusão 10/03/2026 e `porcentagemLeitura(null)` = 100.

**Dado antigo do banco** (carregado pelo construtor de persistência sem leitura)

- **E36 (R21).** Dado empréstimo com status `null`, Quando chamo `porcentagemLeitura(200)`, Então `null`.

## Casos de borda

- **Prazo de exatamente 1 dia** é o menor permitido (E1). Prazo de 0 dias é inválido (E2).
- **Prazos curtos (1 a 3 dias) já nascem vencendo**, porque no dia do empréstimo já faltam 3 dias ou menos.
  Isso é intencional. Ex.: empréstimo em 01/03/2026, prevista para 03/03/2026, hoje = 01/03/2026 →
  `diasParaVencimento(hoje)` = 2 e `estaVencendo(hoje)` = `true`.
- **No dia previsto** o empréstimo ainda não venceu, mas está vencendo (E14). Vence no dia seguinte (E15).
- **Exatamente 3 dias** antes do vencimento já é vencendo (E13). Com 4 dias e prazo curto, não é. Ex.: empréstimo em
  01/03/2026, prevista para 11/03/2026 (10 dias), hoje = 07/03/2026 → 4 dias, 40% do prazo → `false`.
- **Exatamente 15%** do prazo já é vencendo (E18).
- **A regra dos 15% só faz diferença em prazos maiores que 20 dias.** Até 20 dias, quem decide é a regra dos 3 dias.
- **Devolução no mesmo dia do empréstimo** é válida (E7).
- **Devolução feita com atraso** não deixa o empréstimo "vencido" depois de devolvido (E20).
- **Empréstimo agendado** (data do empréstimo no futuro) pode ser criado (E24), mas não pode ser devolvido antes de
  começar (E25).
- **Ordem das verificações:** primeiro se está devolvido, depois se falta a data prevista (E23).
- **Leitura depois da devolução** fica guardada (E32), mas não pode mais ser alterada (E33).
- **A porcentagem é arredondada para baixo** (E28): só mostra 100% na última página ou quando `LIDO`.
- **Empréstimo antigo carregado do banco sem leitura** (campos `null`): os getters retornam `null` e a porcentagem
  também (E36).

## Fora do escopo

- Empréstimo sem data prevista de devolução ("devolve quando puder"). Por enquanto a data é obrigatória.
  O grupo decide no futuro se isso vai existir.
- Empréstimo por horas (prazo menor que 1 dia).
- Validação do usuário no construtor (ex.: usuário `null`). Por enquanto a spec só valida as datas.
- Estado de um empréstimo agendado antes de começar (se conta como emprestado, se existe um estado "agendado",
  e se pode ser agendado enquanto o livro está com outra pessoa). Por enquanto a spec só garante que ele pode
  ser criado e que não pode ser devolvido antes de começar.
- Exceções próprias do domínio. Por enquanto usamos `IllegalArgumentException` e `IllegalStateException`;
  quando as próprias forem criadas, elas vão estender essas.
- Garantir que um livro tenha só um empréstimo ativo. Essa regra é do service (`LivroService`), não desta classe.
- Serialização para JSON / DTOs da API.
- Fuso horário e relógio. A classe só recebe `hoje` pronto; garantir que é a data de Brasília é responsabilidade
  de quem chama (o `Clock` da aplicação e o service). Ex.: às 22h de 10/03/2026 em Brasília já é 11/03/2026 em UTC,
  e o hoje correto é 10/03/2026. Isso é testado lá, não aqui.
- Notificações do Telegram. Esta classe só informa os dias e os estados, quem decide quando avisar é o
  notification-service.
