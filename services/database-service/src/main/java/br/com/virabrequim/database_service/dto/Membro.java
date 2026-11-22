package br.com.virabrequim.database_service.dto;

import java.time.LocalDate;

public record Membro(
        Long id,
        String nome,
        LocalDate desde
) {
}
