package br.com.virabrequim.database_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Corpo de POST /usuario/{usuarioId}/estante
public record NomeEstante(
        @NotBlank @Size(max = 50) String nome
) {
}
