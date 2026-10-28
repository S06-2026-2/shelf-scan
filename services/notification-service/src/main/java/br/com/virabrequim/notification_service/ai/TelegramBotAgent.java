package br.com.virabrequim.notification_service.ai;

import br.com.virabrequim.notification_service.vinculo.ConexaoTelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.Executor;

/**
 * Canal de entrada do bot: recebe cada mensagem que um usuário manda e responde.
 * O envio de notificações iniciadas pelo sistema fica em outro componente (TelegramNotifier).
 */
public class TelegramBotAgent extends DefaultLongPollingUpdateConsumer {

    // Nome que o grupo deu ao bot.
    static final String NOME_DO_BOT = "Christopher";

    static final String BOAS_VINDAS =
            "Olá! Eu sou o " + NOME_DO_BOT + ", o bot do Shelf Scan. Para receber aqui os avisos dos seus livros, "
                    + "conecte sua conta pelo botão \"Conectar Telegram\" no site.";

    static final String CONECTADO =
            "Pronto! Seu Telegram está conectado ao Shelf Scan. "
                    + "Vou te avisar por aqui quando um livro seu for salvo.";

    static final String CODIGO_INVALIDO =
            "Esse link expirou ou já foi usado. Volte ao site e peça um novo em \"Conectar Telegram\".";

    static final String NAO_ENTENDI =
            "Ainda não sei responder a isso. Por enquanto só entendo o comando /start.";

    private static final Logger log = LoggerFactory.getLogger(TelegramBotAgent.class);

    private final TelegramClient telegramClient;
    private final ConexaoTelegramService conexaoTelegramService;
    private final AgenteConversacional agenteConversacional;
    private final Executor executorDeConversas;

    public TelegramBotAgent(
            TelegramClient telegramClient,
            ConexaoTelegramService conexaoTelegramService,
            AgenteConversacional agenteConversacional,
            Executor executorDeConversas) {
        this.telegramClient = telegramClient;
        this.conexaoTelegramService = conexaoTelegramService;
        this.agenteConversacional = agenteConversacional;
        this.executorDeConversas = executorDeConversas;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        User remetente = update.getMessage().getFrom();
        String nome = remetente != null ? remetente.getFirstName() : null;
        String username = remetente != null ? remetente.getUserName() : null;

        String texto = update.getMessage().getText();
        if (texto.trim().startsWith("/")) {
            enviar(chatId, tratarMensagem(chatId, texto, nome, username));
            return;
        }
        executorDeConversas.execute(() -> conversar(chatId, texto));
    }

    private void conversar(Long chatId, String texto) {
        avisarQueEstaDigitando(chatId);
        String resposta = agenteConversacional.responder(chatId, texto).orElse(NAO_ENTENDI);
        enviar(chatId, resposta);
    }

    private void avisarQueEstaDigitando(Long chatId) {
        try {
            telegramClient.execute(SendChatAction.builder().chatId(chatId).action("typing").build());
        } catch (TelegramApiException e) {
            log.warn("Não foi possível mostrar \"digitando\" no chat {}", chatId);
        }
    }

    String tratarMensagem(Long chatId, String texto, String nome, String username) {
        String[] termos = texto.trim().split("\\s+");

        if (!comando(termos[0]).equals("/start")) {
            return NAO_ENTENDI;
        }
        if (termos.length < 2) {
            return BOAS_VINDAS;
        }

        // O código é uma credencial de uso único: nunca vai para o log.
        boolean conectou = conexaoTelegramService.conectar(termos[1], chatId, nome, username);
        return conectou ? CONECTADO : CODIGO_INVALIDO;
    }

    // "/start" e "/start@nome_do_bot" resultam ambos em "/start"
    private static String comando(String primeiroTermo) {
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
