package br.com.virabrequim.database_service.domain;

// Regras de leitura em comum entre a leitura do dono (LivroNode) e a de quem pegou o livro (EmprestimoRelationship)
public final class RegrasLeitura {

    private RegrasLeitura() {
    }

    // Página lida para o status LENDO. Página inválida (null, negativa ou maior que o total, quando o total existe)
    // vira 0 em vez de dar erro.
    // TODO: a correção silenciosa para 0 pode mudar no futuro (ex.: voltar a responder 400).
    public static int paginaLendo(Integer paginaAtual, Integer totalPaginas) {
        if (paginaAtual == null || paginaAtual < 0) {
            return 0;
        }
        if (totalPaginas != null && paginaAtual > totalPaginas) {
            return 0;
        }
        return paginaAtual;
    }

    // Porcentagem calculada (não é guardada). LENDO sem total de páginas não tem porcentagem.
    public static Integer porcentagem(StatusLeitura status, Integer paginaAtual, Integer totalPaginas) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case QUERO_LER -> 0;
            case LIDO -> 100;
            case LENDO -> {
                if (totalPaginas == null) {
                    yield null;
                }
                int lidas = paginaAtual == null ? 0 : paginaAtual;
                yield Math.min(100, lidas * 100 / totalPaginas);
            }
        };
    }

    // Ao mudar o total de páginas do livro, a leitura não pode ficar além do novo total.
    // Retorna a nova página atual: LIDO acompanha o novo total; LENDO acima do novo total é conflito (409).
    public static Integer paginaComNovoTotal(StatusLeitura status, Integer paginaAtual, Integer novoTotal,
            String deQuem) {
        if (status == StatusLeitura.LIDO) {
            return novoTotal;
        }
        if (status == StatusLeitura.LENDO && novoTotal != null && paginaAtual != null && paginaAtual > novoTotal) {
            throw new IllegalStateException("O total de páginas (" + novoTotal + ") não pode ser menor que a página "
                    + "atual da leitura " + deQuem + " (" + paginaAtual + ").");
        }
        return paginaAtual;
    }
}
