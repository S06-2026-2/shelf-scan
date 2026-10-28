// Banco de exemplo do Shelf Scan para demonstração no Neo4j Browser (http://localhost:8339, neo4j/shelfscan123).
// Segue o modelo do database-service: labels, propriedades e tipos de relacionamento iguais aos do SDN.
// Datas dos empréstimos pensadas para "hoje" = 19/10/2026 (fuso de Brasília):
//   em dia, vencendo (≤ 3 dias ou ≤ 15% do prazo), vencido e devolvidos (histórico).
//
// Para começar do zero, rode antes (APAGA TUDO do banco):
//   MATCH (n) DETACH DELETE n;

// ---------- Tags ----------
UNWIND ['Clássico', 'Romance', 'Literatura Russa', 'Literatura Brasileira', 'Filosofia',
        'Distopia', 'Ficção Científica', 'Fantasia', 'Humor'] AS nome
MERGE (:Tag {nome: nome});

// ---------- Estantes ----------
CREATE (:Estante {nome: 'Sala'}),
       (:Estante {nome: 'Quarto'}),
       (:Estante {nome: 'Escritório'});

// ---------- Autores ----------
CREATE (:Autor {nome: 'Fiódor Dostoiévski', nacionalidade: 'Russa',
                dataDeNascimento: date('1821-11-11'), dataDeFalecimento: date('1881-02-09')}),
       (:Autor {nome: 'Machado de Assis', nacionalidade: 'Brasileira',
                dataDeNascimento: date('1839-06-21'), dataDeFalecimento: date('1908-09-29')}),
       (:Autor {nome: 'George Orwell', nacionalidade: 'Britânica',
                dataDeNascimento: date('1903-06-25'), dataDeFalecimento: date('1950-01-21')}),
       (:Autor {nome: 'Clarice Lispector', nacionalidade: 'Brasileira',
                dataDeNascimento: date('1920-12-10'), dataDeFalecimento: date('1977-12-09')}),
       (:Autor {nome: 'Isaac Asimov', nacionalidade: 'Americana',
                dataDeNascimento: date('1920-01-02'), dataDeFalecimento: date('1992-04-06')}),
       (:Autor {nome: 'Terry Pratchett', nacionalidade: 'Britânica',
                dataDeNascimento: date('1948-04-28'), dataDeFalecimento: date('2015-03-12')}),
       (:Autor {nome: 'Neil Gaiman', nacionalidade: 'Britânica',
                dataDeNascimento: date('1960-11-10')});

// ---------- Usuários (fictícios) ----------
CREATE (:Usuario {nome: 'Ana Souza', email: 'ana.souza@exemplo.com'}),
       (:Usuario {nome: 'Bruno Lima', email: 'bruno.lima@exemplo.com'}),
       (:Usuario {nome: 'Carla Mendes', email: 'carla.mendes@exemplo.com'});

// ---------- Livros, com autores (WRITTEN_BY), estante (STORED_IN) e tags (HAS_TAG) ----------
// ISBNs ilustrativos
UNWIND [
  {nome: 'Memórias do Subsolo', isbn: '978-0-00-000001-1', paginas: 152, adicao: '2026-08-02T10:15:00-03:00',
   sinopse: 'O monólogo amargo de um funcionário aposentado que se isola da sociedade de São Petersburgo.',
   autores: ['Fiódor Dostoiévski'], estante: 'Escritório', tags: ['Clássico', 'Literatura Russa', 'Filosofia']},
  {nome: 'Crime e Castigo', isbn: '978-0-00-000002-8', paginas: 592, adicao: '2026-08-02T10:20:00-03:00',
   sinopse: 'Raskólnikov comete um assassinato e passa a ser consumido pela culpa.',
   autores: ['Fiódor Dostoiévski'], estante: 'Escritório', tags: ['Clássico', 'Literatura Russa', 'Romance']},
  {nome: 'Dom Casmurro', isbn: '978-0-00-000003-5', paginas: 256, adicao: '2026-07-15T19:00:00-03:00',
   sinopse: 'Bentinho relembra a vida com Capitu e a dúvida que o persegue.',
   autores: ['Machado de Assis'], estante: 'Sala', tags: ['Clássico', 'Literatura Brasileira', 'Romance']},
  {nome: 'Memórias Póstumas de Brás Cubas', isbn: '978-0-00-000004-2', paginas: 208, adicao: '2026-07-15T19:05:00-03:00',
   sinopse: 'Um defunto autor narra a própria vida com ironia.',
   autores: ['Machado de Assis'], estante: 'Sala', tags: ['Clássico', 'Literatura Brasileira', 'Romance']},
  {nome: '1984', isbn: '978-0-00-000005-9', paginas: 416, adicao: '2026-09-01T09:30:00-03:00',
   sinopse: 'Winston Smith vive sob a vigilância constante do Grande Irmão.',
   autores: ['George Orwell'], estante: 'Quarto', tags: ['Clássico', 'Distopia', 'Ficção Científica']},
  {nome: 'A Revolução dos Bichos', isbn: '978-0-00-000006-6', paginas: 152, adicao: '2026-09-01T09:35:00-03:00',
   sinopse: 'Os animais tomam a fazenda, mas alguns são mais iguais que os outros.',
   autores: ['George Orwell'], estante: 'Quarto', tags: ['Clássico', 'Distopia']},
  {nome: 'A Hora da Estrela', isbn: '978-0-00-000007-3', paginas: 88, adicao: '2026-06-20T14:00:00-03:00',
   sinopse: 'A história de Macabéa, narrada por Rodrigo S.M.',
   autores: ['Clarice Lispector'], estante: 'Sala', tags: ['Literatura Brasileira', 'Romance']},
  {nome: 'Eu, Robô', isbn: '978-0-00-000008-0', paginas: 320, adicao: '2026-08-20T21:10:00-03:00',
   sinopse: 'Contos sobre robôs e as Três Leis da Robótica.',
   autores: ['Isaac Asimov'], estante: 'Quarto', tags: ['Ficção Científica', 'Clássico']},
  {nome: 'Belas Maldições', isbn: '978-0-00-000009-7', paginas: 400, adicao: '2026-10-01T18:45:00-03:00',
   sinopse: 'Um anjo e um demônio tentam impedir o fim do mundo.',
   autores: ['Terry Pratchett', 'Neil Gaiman'], estante: 'Quarto', tags: ['Fantasia', 'Humor']}
] AS l
CREATE (livro:Livro {nome: l.nome, isbn: l.isbn, paginas: l.paginas, sinopse: l.sinopse,
                     dataDeAdicao: datetime(l.adicao)})
WITH livro, l
MATCH (e:Estante {nome: l.estante})
CREATE (livro)-[:STORED_IN]->(e)
WITH livro, l
UNWIND l.autores AS nomeAutor
MATCH (a:Autor {nome: nomeAutor})
CREATE (livro)-[:WRITTEN_BY]->(a)
WITH DISTINCT livro, l
UNWIND l.tags AS nomeTag
MATCH (t:Tag {nome: nomeTag})
CREATE (livro)-[:HAS_TAG]->(t);

// ---------- Empréstimos (LENT_TO: Livro -> Usuario, com propriedades) ----------
UNWIND [
  // Dom Casmurro: histórico devolvido + empréstimo atual VENCENDO (prevista 21/10, faltam 2 dias)
  {livro: 'Dom Casmurro', usuario: 'Ana Souza', emp: '2026-08-01', prev: '2026-08-15', dev: '2026-08-14'},
  {livro: 'Dom Casmurro', usuario: 'Bruno Lima', emp: '2026-10-07', prev: '2026-10-21', dev: null},
  // 1984: VENCIDO (prevista 10/10, 9 dias de atraso)
  {livro: '1984', usuario: 'Carla Mendes', emp: '2026-09-20', prev: '2026-10-10', dev: null},
  // Crime e Castigo: EM DIA (prevista 05/11)
  {livro: 'Crime e Castigo', usuario: 'Ana Souza', emp: '2026-10-15', prev: '2026-11-05', dev: null},
  // Eu, Robô: devolvido com atraso
  {livro: 'Eu, Robô', usuario: 'Bruno Lima', emp: '2026-09-01', prev: '2026-09-15', dev: '2026-09-20'},
  // A Hora da Estrela: dois empréstimos já devolvidos (histórico)
  {livro: 'A Hora da Estrela', usuario: 'Carla Mendes', emp: '2026-07-10', prev: '2026-07-24', dev: '2026-07-22'},
  {livro: 'A Hora da Estrela', usuario: 'Ana Souza', emp: '2026-09-01', prev: '2026-09-08', dev: '2026-09-08'}
] AS emp
MATCH (l:Livro {nome: emp.livro}), (u:Usuario {nome: emp.usuario})
CREATE (l)-[:LENT_TO {dataEmprestimo: date(emp.emp),
                      dataPrevistaDevolucao: date(emp.prev),
                      dataDevolucao: CASE WHEN emp.dev IS NULL THEN null ELSE date(emp.dev) END}]->(u);
