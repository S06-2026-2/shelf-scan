package br.com.virabrequim.database_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record AtualizacaoUsuario(
        @Size(max = 100) String nome,
        @Email String email
) {
}
