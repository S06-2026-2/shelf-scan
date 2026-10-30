// Consultas para demonstrar o banco de exemplo (rodar uma por vez no Neo4j Browser).
// Antes, carregue docs/demo/neo4j-exemplo.cypher.

// 1. Visão geral: o grafo inteiro
MATCH p = ()-->()
RETURN p;

// 2. Um livro e tudo ao redor dele (autores, estante, tags, empréstimos)
MATCH p = (:Livro {nome: 'Dom Casmurro'})-->()
RETURN p;

// 3. Livro com mais de um autor
MATCH p = (l:Livro)-[:WRITTEN_BY]->(:Autor)
WHERE COUNT { (l)-[:WRITTEN_BY]->() } > 1
RETURN p;

// 4. Autores e quantos livros de cada um estão na biblioteca
MATCH (a:Autor)<-[:WRITTEN_BY]-(l:Livro)
RETURN a.nome AS autor, a.nacionalidade AS nacionalidade, count(l) AS livros, collect(l.nome) AS titulos
ORDER BY livros DESC, autor;

// 5. O que tem em cada estante
MATCH (e:Estante)<-[:STORED_IN]-(l:Livro)
RETURN e.nome AS estante, collect(l.nome) AS livros
ORDER BY estante;

// 6. Empréstimos ativos e o estado de cada um (mesmas regras do EmprestimoRelationship)
//    "hoje" no fuso de Brasília; vencendo = faltam ≤ 3 dias ou ≤ 15% do prazo
WITH date({timezone: 'America/Sao_Paulo'}) AS hoje
MATCH (l:Livro)-[e:LENT_TO]->(u:Usuario)
WHERE e.dataDevolucao IS NULL
WITH l, u, e, hoje,
     duration.inDays(hoje, e.dataPrevistaDevolucao).days AS dias,
     duration.inDays(e.dataEmprestimo, e.dataPrevistaDevolucao).days AS prazo
RETURN l.nome AS livro, u.nome AS usuario, e.dataEmprestimo AS emprestado, e.dataPrevistaDevolucao AS prevista,
       dias AS diasParaVencimento,
       CASE
         WHEN dias < 0 THEN 'VENCIDO'
         WHEN dias <= 3 OR dias <= 0.15 * prazo THEN 'VENCENDO'
         ELSE 'EM DIA'
       END AS estado
ORDER BY dias;

// 7. Histórico de empréstimos de um livro (o relacionamento guarda o histórico inteiro)
MATCH (l:Livro {nome: 'A Hora da Estrela'})-[e:LENT_TO]->(u:Usuario)
RETURN u.nome AS usuario, e.dataEmprestimo AS emprestado, e.dataPrevistaDevolucao AS prevista,
       e.dataDevolucao AS devolvido,
       CASE WHEN e.dataDevolucao > e.dataPrevistaDevolucao THEN 'com atraso' ELSE 'no prazo' END AS situacao
ORDER BY emprestado;

// 8. Navegação inversa: que livros estão com cada usuário agora (Usuario -> Livro pelo LENT_TO de entrada)
MATCH (u:Usuario)<-[e:LENT_TO]-(l:Livro)
WHERE e.dataDevolucao IS NULL
RETURN u.nome AS usuario, collect(l.nome) AS comOUsuario
ORDER BY usuario;

// 9. Base para recomendação: livros que a Ana ainda não pegou e que compartilham tags com os que ela já pegou
MATCH (ana:Usuario {nome: 'Ana Souza'})<-[:LENT_TO]-(lido:Livro)-[:HAS_TAG]->(t:Tag)<-[:HAS_TAG]-(sugestao:Livro)
WHERE NOT (sugestao)-[:LENT_TO]->(ana)
RETURN sugestao.nome AS sugestao, collect(DISTINCT t.nome) AS tagsEmComum, count(DISTINCT t) AS afinidade
ORDER BY afinidade DESC, sugestao;

// 10. Caminho entre dois autores passando por livros e tags
MATCH p = shortestPath((:Autor {nome: 'Machado de Assis'})-[*]-(:Autor {nome: 'George Orwell'}))
RETURN p;
