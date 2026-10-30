package br.com.virabrequim.database_service.domain;

import java.text.Normalizer;
import java.util.Locale;

// Normalização de textos usada para comparar e identificar nomes (autores e tags)
public final class Textos {

    private Textos() {
    }

    // Sem espaços nas pontas e com espaços repetidos no meio juntados:
    // "  Fiódor   Dostoiévski " -> "Fiódor Dostoiévski"
    public static String espacosNormalizados(String texto) {
        return texto.strip().replaceAll("\\s+", " ");
    }

    // Normalização completa: espaços, minúsculas e sem acentos: " Ficção  Científica " -> "ficcao cientifica"
    // NFD separa a letra do acento ("ç" vira "c" + cedilha) e o \p{M} remove as marcas
    public static String chave(String texto) {
        String semAcentos = Normalizer.normalize(espacosNormalizados(texto), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(Locale.ROOT);
    }

    // Texto opcional: null ou em branco vira null (para o PATCH, "" apaga o campo)
    public static String opcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
