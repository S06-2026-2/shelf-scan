package br.com.virabrequim.notification_service.ai;

import br.com.virabrequim.notification_service.vinculo.ConexaoTelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

/**
 * Liga o bot ao Telegram quando a aplicação termina de subir.
 * Sem token configurado, ou se o Telegram estiver inacessível, o serviço continua no ar sem o bot.
 */
@Component
public class TelegramBotRegistrar implements ApplicationRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotRegistrar.class);

    private final String token;
    private final Optional<TelegramClient> telegramClient;
    private final ConexaoTelegramService conexaoTelegramService;
    private TelegramBotsLongPollingApplication longPollingApplication;

    public TelegramBotRegistrar(
            @Value("${telegram.bot.token:}") String token,
            Optional<TelegramClient> telegramClient,
            ConexaoTelegramService conexaoTelegramService) {
        this.token = token;
        this.telegramClient = telegramClient;
        this.conexaoTelegramService = conexaoTelegramService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (token.isBlank() || telegramClient.isEmpty()) {
            log.warn("TELEGRAM_BOT_TOKEN não configurado: bot do Telegram desativado.");
            return;
        }

        try {
            longPollingApplication = new TelegramBotsLongPollingApplication();
            TelegramBotAgent agente = new TelegramBotAgent(telegramClient.get(), conexaoTelegramService);
            longPollingApplication.registerBot(token, agente);
            log.info("Bot do Telegram registrado e escutando mensagens (long-polling).");
        } catch (TelegramApiException e) {
            log.error("Não foi possível registrar o bot do Telegram; o serviço segue sem ele.", e);
        }
    }

    @Override
    public void destroy() throws Exception {
        if (longPollingApplication != null) {
            longPollingApplication.close();
        }
    }
}
