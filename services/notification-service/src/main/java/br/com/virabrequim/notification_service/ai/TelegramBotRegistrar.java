package br.com.virabrequim.notification_service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Liga o bot ao Telegram quando a aplicação termina de subir.
 * Sem token configurado, ou se o Telegram estiver inacessível, o serviço continua no ar sem o bot.
 */
@Component
public class TelegramBotRegistrar implements ApplicationRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotRegistrar.class);

    private final String token;
    private TelegramBotsLongPollingApplication longPollingApplication;

    public TelegramBotRegistrar(@Value("${telegram.bot.token:}") String token) {
        this.token = token;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (token.isBlank()) {
            log.warn("TELEGRAM_BOT_TOKEN não configurado: bot do Telegram desativado.");
            return;
        }

        try {
            longPollingApplication = new TelegramBotsLongPollingApplication();
            longPollingApplication.registerBot(token, new TelegramBotAgent(new OkHttpTelegramClient(token)));
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
