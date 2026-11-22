package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.agente.AgenteEnriquecedor;
import br.com.virabrequim.database_service.agente.Fonte;
import br.com.virabrequim.database_service.agente.SugestaoEnriquecimento;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;
import br.com.virabrequim.database_service.dto.AtualizacaoLivro;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Aplica no livro o que o agente enriquecedor sugeriu. Regra: o agente só PREENCHE campos vazios, nunca
// sobrescreve o que o usuário já cadastrou. A gravação reaproveita o PATCH do LivroService (mesmas validações,
// find-or-create de autores e tags).
//
// Sem @Transactional aqui de propósito: a chamada ao LLM leva segundos e não deve segurar uma transação do banco
// aberta. Só o atualizar (no fim) é transacional.
@Service
public class EnriquecimentoService {

    private final LivroService livroService;
    private final AgenteEnriquecedor agente;

    public EnriquecimentoService(LivroService livroService, AgenteEnriquecedor agente) {
        this.livroService = livroService;
        this.agente = agente;
    }

    public Resultado enriquecer(Long usuarioId, Long livroId, String textoBruto) {
        // Confere o dono ANTES de chamar o LLM: pedido de outra pessoa não gasta cota
        LivroNode livro = livroService.buscarDoDono(usuarioId, livroId);
        SugestaoEnriquecimento sugestao = agente.sugerir(livro, textoBruto);

        Map<String, Fonte> campos = new LinkedHashMap<>();
        String sinopse = null;
        String isbn = null;
        Integer paginas = null;
        String editora = null;
        List<String> autores = null;
        List<String> tags = null;

        if (livro.getSinopse() == null && sugestao.sinopse() != null) {
            sinopse = sugestao.sinopse();
            campos.put("sinopse", Fonte.IA);
        }
        if (livro.getIsbn() == null && sugestao.isbn() != null) {
            isbn = sugestao.isbn();
            campos.put("isbn", Fonte.CATALOGO);
        }
        if (livro.getPaginas() == null && sugestao.paginas() != null && cabeNaLeitura(livro, sugestao.paginas())) {
            paginas = sugestao.paginas();
            campos.put("paginas", Fonte.CATALOGO);
        }
        if (livro.getEditora() == null && sugestao.editora() != null) {
            editora = sugestao.editora();
            campos.put("editora", Fonte.CATALOGO);
        }
        if (livro.getAutores().isEmpty() && !sugestao.autores().isEmpty()) {
            autores = sugestao.autores();
            campos.put("autores", sugestao.fonteAutores());
        }
        if (livro.getTags().isEmpty() && !sugestao.tags().isEmpty()) {
            tags = sugestao.tags();
            campos.put("tags", Fonte.IA);
        }

        if (campos.isEmpty()) {
            return resultado(livro, campos, sugestao);
        }
        LivroNode atualizado = livroService.atualizar(usuarioId, livroId,
                new AtualizacaoLivro(null, autores, tags, isbn, null, paginas, sinopse, editora));
        return resultado(atualizado, campos, sugestao);
    }

    private Resultado resultado(LivroNode livro, Map<String, Fonte> campos, SugestaoEnriquecimento sugestao) {
        return new Resultado(livro, List.copyOf(campos.keySet()), Collections.unmodifiableMap(campos),
                sugestao.encontradoNoCatalogo(), sugestao.edicaoDigital(), sugestao.consultas(), agente.modelo());
    }

    // Um total de páginas menor que a página em que alguém está lendo seria conflito (409) no LivroNode:
    // nesse caso a sugestão de páginas é descartada em vez de quebrar o enriquecimento inteiro
    private static boolean cabeNaLeitura(LivroNode livro, int paginas) {
        boolean cabeNoDono = livro.getPaginaAtual() == null || livro.getPaginaAtual() <= paginas;
        boolean cabeNoEmprestimo = livro.emprestimoAtivo()
                .map(EmprestimoRelationship::getPaginaAtual)
                .map(pagina -> pagina <= paginas)
                .orElse(true);
        return cabeNoDono && cabeNoEmprestimo;
    }

    // fontes: de onde veio cada campo preenchido. consultas: o que o agente pesquisou nos catálogos
    public record Resultado(LivroNode livro, List<String> camposPreenchidos, Map<String, Fonte> fontes,
            boolean encontradoNoCatalogo, boolean edicaoDigital, List<String> consultas, String modelo) {
    }
}
