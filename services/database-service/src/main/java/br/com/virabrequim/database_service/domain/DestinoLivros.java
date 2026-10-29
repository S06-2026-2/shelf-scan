package br.com.virabrequim.database_service.domain;

// O que acontece com os livros de uma estante apagada (DELETE /usuario/{usuarioId}/estante/{estanteId}?livros=...)
public enum DestinoLivros {
    // Todos os livros vão para a estante padrão (padrão da API: nenhum livro se perde sem pedido explícito)
    MOVER,
    // Os livros são apagados junto; os emprestados não são apagados e vão para a estante padrão
    APAGAR
}
