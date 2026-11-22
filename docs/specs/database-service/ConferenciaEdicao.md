classe: br.com.virabrequim.database_service.agente.ConferenciaEdicao

# Spec: ConferenciaEdicao

## Contexto

Confere se uma edição vinda de um catálogo (`Edicao`) é mesmo do livro cadastrado (`LivroNode`). Qual é o livro já foi
decidido pelo OCR e pelo usuário; aqui é a **edição** que tem que bater com ele. Na dúvida, recusa: é melhor deixar o
campo vazio do que gravar o dado de outro livro. Classe utilitária, sem estado e visível só no pacote `agente`.

`record Edicao(String isbn, String titulo, String subtitulo, List<String> autores, String editora, Integer paginas,
String sinopse, Boolean digital, String origem)`.

## API esperada

| Assinatura | Para quê | Regras |
| --- | --- | --- |
| `static boolean mesmoLivro(LivroNode livro, List<Edicao> edicoes)` | As edições (do mesmo ISBN, uma por catálogo) são deste livro? | F1–F5 |
| `static boolean mesmaPessoa(String nome, String outro)` | Os dois nomes são da mesma pessoa? | F3 |

## Regras

Comparações sempre sem acentos, maiúsculas e pontuação, palavra por palavra.

- **F1.** `mesmoLivro` é `true` só se o título (F2) de **todas** as edições conferir e se autor (F3), volume (F4) e editora
  (F5) forem confirmados por **pelo menos uma** edição e contraditos por **nenhuma**. Catálogo que não informa um dado nem
  confirma nem contradiz. Lista vazia = `false`.
- **F2. Título:** as palavras do título do livro têm que ser o **começo** das palavras do título da edição seguido do
  subtítulo dela. Artigos e preposições (o, a, os, as, um, uma, de, do, da, dos, das, e, em, the, of) são ignorados.
- **F3. Autor:** pelo menos um autor do livro tem que ser um autor da edição. São a mesma pessoa se a última palavra
  (sobrenome) de um dos nomes aparece no outro; palavras com 7 letras ou mais podem diferir em até 2 letras. Livro sem
  autores não é conferido; edição sem autores não informa.
- **F4. Volume:** com número no volume do livro, o título da edição tem que conter esse número (zeros à esquerda não
  contam); outro número de volume contradiz, e sem volume no título não informa. Com o livro sem volume, a edição não pode ter "vol", "volume", "tomo" ou "v" seguido de número.
- **F5. Editora:** se os dois lados informam, têm que ter uma palavra em comum, fora as genéricas (editora, companhia,
  cia, livros, grupo, ltda e parecidas). Livro sem editora não é
  conferido; edição sem editora não informa.

## Exemplos

- **E1 (F2).** "O Hobbit" confere com "Hobbit"; "Duna" confere com "Duna" + "edição especial", mas não com "Messias de
  Duna"; "Dom Casmurro" não confere com "100 receitas de macarrão" nem com "Dom".
- **E2 (F2).** "O Senhor dos Anéis: A Sociedade do Anel" confere com "O Senhor dos Anéis" + "A Sociedade do Anel", mas não
  com "O Senhor dos Anéis" + "As Duas Torres".
- **E3 (F3).** "Fiódor Dostoiévski" confere com "Dostoyevsky, Fyodor"; "Machado de Assis" não confere com "José de
  Alencar" nem com edição sem autores.
- **E4 (F4).** Volume "3" confere com "Frieren Vol. 03", mas não com "Frieren Vol. 13" nem com "Frieren". Sem volume, não
  confere com "Frieren Vol. 3".
- **E5 (F5).** "Editora 34" confere com "34", mas não com edição sem editora nem com "Companhia das Letras". "Cia. das
  Letras" confere com "Companhia das Letras".
- **E6 (F1).** Livro com autor e "Editora 34": edição sem editora + edição sem autores e com "Editora 34" confere; cada
  uma sozinha não confere.

## Fora do escopo

- Obras diferentes com o mesmo começo de título e o mesmo autor: passam pela conferência.
