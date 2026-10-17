package br.com.virabrequim.notification_service.notificacao;

import br.com.virabrequim.notification_service.vinculo.TelegramNaoConfiguradoException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TelegramNotifierTest {

    private final TelegramClient telegramClient = mock(TelegramClient.class);
    private final TelegramNotifier notifier = new TelegramNotifier(Optional.of(telegramClient));

    @Test
    void enviar_mandaOTextoParaOChatInformado() throws TelegramApiException {
        assertTrue(notifier.enviar(555L, "Olá"));

        ArgumentCaptor<SendMessage> enviada = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(enviada.capture());
        assertEquals("555", enviada.getValue().getChatId());
        assertEquals("Olá", enviada.getValue().getText());
    }

    @Test
    void enviar_devolveFalsoQuandoOTelegramRecusa() throws TelegramApiException {
        doThrow(new TelegramApiException("Forbidden: bot was blocked by the user"))
                .when(telegramClient).execute(any(SendMessage.class));

        assertFalse(notifier.enviar(555L, "Olá"));
    }

    @Test
    void enviar_semBotConfigurado_lancaExcecaoEmVezDeEngolir() {
        TelegramNotifier semBot = new TelegramNotifier(Optional.empty());

        assertThrows(TelegramNaoConfiguradoException.class, () -> semBot.enviar(555L, "Olá"));
    }
}
