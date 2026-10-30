classe: br.com.virabrequim.database_service.domain.node.UsuarioNode

# Spec: UsuarioNode

## Contexto

Representa um usuário. Existem dois tipos: o **usuário comum** (`UsuarioNode`, label `Usuario`) e a **biblioteca**
(`BibliotecaNode extends UsuarioNode`, labels `Usuario` e `Biblioteca`). As regras desta spec valem para os dois.

As **invariantes** do usuário ficam no próprio node, não nos services: todo usuário criado pelo construtor já nasce
válido (nome e email obrigatórios, email normalizado) e com a **estante padrão**. Os setters de nome e email aplicam
as mesmas regras, então elas valem também na atualização. O que depende do banco (email único) fica nos services.

A estante padrão é ligada ao usuário por dois relacionamentos: `HAS` (como qualquer estante) e `ESTANTE_PADRAO`
(para marcar qual é a padrão). A mesma instância de `EstanteNode` fica nos dois.

```
(Usuario)-[:HAS]->(Estante "Minha estante")
(Usuario)-[:ESTANTE_PADRAO]->(mesma Estante)
```

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `UsuarioNode()` | Construtor vazio (usado pelo SDN ao carregar do banco) | UN1 |
| `UsuarioNode(String nome, String email)` | Cria um usuário válido com a estante padrão | UN2–UN5 |
| `void setNome(String nome)` | Altera o nome | UN2 |
| `void setEmail(String email)` | Altera o email | UN3 |
| `static String normalizarEmail(String email)` | Valida e normaliza um email | UN3 |
| `static final String NOME_ESTANTE_PADRAO` | `"Minha estante"` | UN4 |
| `String getNome()` / `String getEmail()` | Dados do usuário | UN2, UN3 |
| `List<EstanteNode> getEstantes()` | Todas as estantes (`HAS`), inclusive a padrão | UN1, UN4 |
| `EstanteNode getEstantePadrao()` | A estante padrão (`ESTANTE_PADRAO`) | UN4 |

Entidades:
- `EstanteNode(String nome)`: construtor que cria a estante sem id (o id é gerado pelo banco).

## Regras

- **UN1.** O construtor sem argumentos cria um usuário com a lista `estantes` **vazia** (nunca `null`) e
  `estantePadrao` `null`. Ele não valida nada (é o construtor usado pelo SDN).
- **UN2.** O nome é obrigatório. `null`, vazio ou só com espaços lança `IllegalArgumentException`. Os espaços nas
  pontas são removidos. Vale no construtor e no `setNome`.
- **UN3.** O email é obrigatório. `null`, vazio ou só com espaços lança `IllegalArgumentException`. O email é
  **normalizado**: sem espaços nas pontas e em letras minúsculas. `normalizarEmail` aplica essa regra e retorna o email
  normalizado; o construtor e o `setEmail` usam `normalizarEmail`.
- **UN4.** O construtor com nome e email cria **uma** estante chamada `NOME_ESTANTE_PADRAO` ("Minha estante"), que fica
  em `estantes` (única da lista) **e** em `estantePadrao` (a mesma instância).
- **UN5.** O construtor valida o nome antes do email: com os dois inválidos, a mensagem da exceção é a do nome.

## Exemplos

- **E1 (UN2, UN3, UN4).** Quando crio `new UsuarioNode("  Ana Souza ", "  Ana@Email.COM ")`, Então o nome é
  "Ana Souza", o email é "ana@email.com", `estantes` tem 1 estante chamada "Minha estante" e `estantePadrao` é essa
  mesma instância.
- **E2 (UN2).** Quando crio `new UsuarioNode(null, "ana@email.com")`, `new UsuarioNode("", "ana@email.com")` ou
  `new UsuarioNode("   ", "ana@email.com")`, Então `IllegalArgumentException`.
- **E3 (UN3).** Quando crio `new UsuarioNode("Ana", null)`, `new UsuarioNode("Ana", "")` ou
  `new UsuarioNode("Ana", "   ")`, Então `IllegalArgumentException`.
- **E4 (UN2).** Dado um usuário "Ana", Quando chamo `setNome("  Ana Lima ")`, Então o nome é "Ana Lima"; Quando
  chamo `setNome("  ")`, Então `IllegalArgumentException` e o nome continua "Ana Lima".
- **E5 (UN3).** Dado um usuário com email "ana@email.com", Quando chamo `setEmail(" NOVO@Email.com ")`, Então o
  email é "novo@email.com"; Quando chamo `setEmail(null)`, Então `IllegalArgumentException` e o email continua
  "novo@email.com".
- **E6 (UN3).** `normalizarEmail("  Ana@Email.COM ")` retorna "ana@email.com"; `normalizarEmail(null)` e
  `normalizarEmail("  ")` lançam `IllegalArgumentException`.
- **E7 (UN1).** `new UsuarioNode()` tem `getEstantes()` vazia (não `null`) e `getEstantePadrao()` `null`.
- **E8 (UN4).** Dois usuários criados pelo construtor têm estantes padrão **diferentes** (instâncias diferentes).

## Casos de borda

- **Setter com valor inválido não altera o campo**: a exceção é lançada antes da atribuição (E4, E5).
- **Maiúsculas no nome são mantidas**: só o email é convertido para minúsculas.

## Fora do escopo

- Unicidade do email (precisa do banco; fica nos services).
- Formato do email (ter `@` etc.): é validado no DTO da API com `@Email`.
- Campos próprios da biblioteca (ver `BibliotecaNode.md`).
