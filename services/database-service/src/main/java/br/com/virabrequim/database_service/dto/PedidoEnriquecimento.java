package br.com.virabrequim.database_service.dto;

import jakarta.validation.constraints.Size;

// Corpo opcional de POST /usuario/{usuarioId}/livro/{livroId}/enriquecimento.
// textoBruto: o texto que o OCR leu da capa (o front repassa); ajuda o agente a achar a edição certa.
public record PedidoEnriquecimento(
        @Size(max = 5000) String textoBruto
) {
}
