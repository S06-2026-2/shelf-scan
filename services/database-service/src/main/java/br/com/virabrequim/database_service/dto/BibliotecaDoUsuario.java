package br.com.virabrequim.database_service.dto;

import java.time.LocalDate;

public record BibliotecaDoUsuario(
        Long id,
        String nome,
        String cidade,
        String estado,
        LocalDate desde
) {
}
