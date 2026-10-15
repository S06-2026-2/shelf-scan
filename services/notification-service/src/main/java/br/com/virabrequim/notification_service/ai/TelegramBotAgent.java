package br.com.virabrequim.notification_service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Canal de entrada do bot: recebe cada mensagem que um usuário manda e responde.
 * O envio de notificações iniciadas pelo sistema fica em outro componente (TelegramNotifier).
 */
public class TelegramBotAgent extends DefaultLongPollingUpdateConsumer {

    static final String BOAS_VINDAS =
            "Olá! Eu sou o bot do Shelf Scan. Para receber aqui os avisos dos seus livros, "
                    + "conecte sua conta pelo botão \"Conectar Telegram\" no site.";

    static final String NAO_ENTENDI =
            "Ainda não sei responder a isso. Por enquanto só entendo o comando /start.";

    private static final Logger log = LoggerFactory.getLogger(TelegramBotAgent.class);

    private final TelegramClient telegramClient;

    public TelegramBotAgent(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        String resposta = tratarMensagem(update.getMessage().getText());
        enviar(chatId, resposta);
    }

    String tratarMensagem(String texto) {
        return comando(texto).equals("/start") ? BOAS_VINDAS : NAO_ENTENDI;
    }

    // "/start", "/start abc123" e "/start@nome_do_bot" resultam todos em "/start"
    private static String comando(String texto) {
        String primeiroTermo = texto.trim().split("\\s+")[0];
        int posicaoArroba = primeiroTermo.indexOf('@');
        return posicaoArroba >= 0 ? primeiroTermo.substring(0, posicaoArroba) : primeiroTermo;
    }

    private void enviar(Long chatId, String texto) {
        SendMessage mensagem = SendMessage.builder().chatId(chatId).text(texto).build();
        try {
            telegramClient.execute(mensagem);
        } catch (TelegramApiException e) {
            log.error("Falha ao enviar mensagem ao chat {}", chatId, e);
        }
    }
}
