package br.com.virabrequim.notification_service.ai;

import org.springframework.stereotype.Component;

/**
 * Monta o texto das notificações. Por enquanto é só template; é aqui que uma IA
 * pode entrar depois para deixar a mensagem mais natural, sem mexer em quem a envia.
 */
@Component
public class MessageFormatterAgent {

    public String livroSalvo(String titulo, String autor) {
        if (autor == null || autor.isBlank()) {
            return "Livro salvo na sua estante: \"" + titulo + "\".";
        }
        return "Livro salvo na sua estante: \"" + titulo + "\", de " + autor + ".";
    }
}
