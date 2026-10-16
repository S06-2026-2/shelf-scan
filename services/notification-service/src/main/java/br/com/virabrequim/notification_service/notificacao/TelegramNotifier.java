package br.com.virabrequim.notification_service.notificacao;

import br.com.virabrequim.notification_service.vinculo.TelegramNaoConfiguradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

/**
 * Canal de saída do bot: manda uma mensagem a um chat por iniciativa do sistema.
 * Qualquer gatilho (livro salvo, lista de desejos, recomendação) usa este mesmo componente.
 */
@Component
public class TelegramNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    private final Optional<TelegramClient> telegramClient;

    public TelegramNotifier(Optional<TelegramClient> telegramClient) {
        this.telegramClient = telegramClient;
    }

    /**
     * @return true se o Telegram aceitou a mensagem; false se o envio falhou
     * (rede, ou o usuário bloqueou o bot).
     * @throws TelegramNaoConfiguradoException se o serviço está sem TELEGRAM_BOT_TOKEN.
     */
    public boolean enviar(Long chatId, String texto) {
        TelegramClient cliente = telegramClient
                .orElseThrow(() -> new TelegramNaoConfiguradoException("TELEGRAM_BOT_TOKEN"));

        SendMessage mensagem = SendMessage.builder().chatId(chatId).text(texto).build();
        try {
            cliente.execute(mensagem);
            return true;
        } catch (TelegramApiException e) {
            log.warn("Não foi possível enviar a notificação ao chat {}: {}", chatId, e.getMessage());
            return false;
        }
    }
}
