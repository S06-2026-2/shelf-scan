package br.com.virabrequim.notification_service.vinculo;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class TelegramNaoConfiguradoException extends RuntimeException {

    public TelegramNaoConfiguradoException() {
        super("O bot do Telegram não está configurado neste serviço (TELEGRAM_BOT_USERNAME).");
    }
}
