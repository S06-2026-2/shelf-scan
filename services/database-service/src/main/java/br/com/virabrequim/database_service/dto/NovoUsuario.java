package br.com.virabrequim.database_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Corpo de POST /usuario e POST /biblioteca
public record NovoUsuario(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Email String email
) {
}
