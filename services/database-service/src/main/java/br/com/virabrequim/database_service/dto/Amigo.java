package br.com.virabrequim.database_service.dto;

import java.time.LocalDate;

public record Amigo(
        Long id,
        String nome,
        LocalDate desde,
        int amigosEmComum
) {
}
