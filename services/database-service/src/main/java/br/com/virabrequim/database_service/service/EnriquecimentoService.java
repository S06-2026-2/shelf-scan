package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.agente.AgenteEnriquecedor;
import br.com.virabrequim.database_service.agente.SugestaoEnriquecimento;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;
import br.com.virabrequim.database_service.dto.AtualizacaoLivro;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

        List<String> campos = new ArrayList<>();
        String sinopse = null;
        String isbn = null;
        Integer paginas = null;
        List<String> autores = null;
        List<String> tags = null;

        if (livro.getSinopse() == null && sugestao.sinopse() != null) {
            sinopse = sugestao.sinopse();
            campos.add("sinopse");
        }
        if (livro.getIsbn() == null && sugestao.isbn() != null) {
            isbn = sugestao.isbn();
            campos.add("isbn");
        }
        if (livro.getPaginas() == null && sugestao.paginas() != null && cabeNaLeitura(livro, sugestao.paginas())) {
            paginas = sugestao.paginas();
            campos.add("paginas");
        }
        if (livro.getAutores().isEmpty() && !sugestao.autores().isEmpty()) {
            autores = sugestao.autores();
            campos.add("autores");
        }
        if (livro.getTags().isEmpty() && !sugestao.tags().isEmpty()) {
            tags = sugestao.tags();
            campos.add("tags");
        }

        if (campos.isEmpty()) {
            return new Resultado(livro, campos, sugestao.livroIdentificado(), agente.modelo());
        }
        LivroNode atualizado = livroService.atualizar(usuarioId, livroId,
                new AtualizacaoLivro(null, autores, tags, isbn, null, paginas, sinopse));
        return new Resultado(atualizado, List.copyOf(campos), true, agente.modelo());
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

    public record Resultado(LivroNode livro, List<String> camposPreenchidos, boolean livroIdentificado,
            String modelo) {
    }
}
