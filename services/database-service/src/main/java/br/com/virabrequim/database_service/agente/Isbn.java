package br.com.virabrequim.database_service.agente;

// Validação de ISBN pelo dígito verificador. É a trava contra o ISBN mais fácil de alucinar: um número com o
// formato certo mas inexistente. Não garante que o ISBN é DESTE livro, só que é um ISBN bem formado.
public final class Isbn {

    private Isbn() {
    }

    // Só dígitos (e X no fim do ISBN-10), sem hífens e espaços; null se não for um ISBN-10 ou ISBN-13 válido
    public static String normalizado(String isbn) {
        if (isbn == null) {
            return null;
        }
        String limpo = isbn.replaceAll("[\\s-]", "").toUpperCase();
        if (limpo.matches("\\d{13}") && valido13(limpo)) {
            return limpo;
        }
        if (limpo.matches("\\d{9}[\\dX]") && valido10(limpo)) {
            return limpo;
        }
        return null;
    }

    // Pesos 1 e 3 alternados; a soma tem que ser múltipla de 10
    private static boolean valido13(String isbn) {
        int soma = 0;
        for (int i = 0; i < 13; i++) {
            soma += (isbn.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return soma % 10 == 0;
    }

    // Pesos 10 a 1; X vale 10 (só na última posição); a soma tem que ser múltipla de 11
    private static boolean valido10(String isbn) {
        int soma = 0;
        for (int i = 0; i < 10; i++) {
            char c = isbn.charAt(i);
            int valor = c == 'X' ? 10 : c - '0';
            soma += valor * (10 - i);
        }
        return soma % 11 == 0;
    }
}
