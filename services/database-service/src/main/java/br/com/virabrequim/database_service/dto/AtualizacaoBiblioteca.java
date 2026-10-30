package br.com.virabrequim.database_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AtualizacaoBiblioteca(
        @Size(max = 100) String nome,
        @Email String email,
        @Valid Endereco endereco,
        @Pattern(regexp = "\\d{2}:\\d{2}") String horarioAbertura,
        @Pattern(regexp = "\\d{2}:\\d{2}") String horarioFechamento
) {
}
