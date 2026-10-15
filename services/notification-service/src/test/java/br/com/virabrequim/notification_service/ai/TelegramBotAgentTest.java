package br.com.virabrequim.notification_service.ai;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TelegramBotAgentTest {

    private final TelegramClient telegramClient = mock(TelegramClient.class);
    private final TelegramBotAgent agent = new TelegramBotAgent(telegramClient);

    @Test
    void startResponde_comBoasVindas() {
        assertEquals(TelegramBotAgent.BOAS_VINDAS, agent.tratarMensagem("/start"));
    }

    @Test
    void startComCodigo_porEnquantoTambemRespondeBoasVindas() {
        assertEquals(TelegramBotAgent.BOAS_VINDAS, agent.tratarMensagem("/start abc123"));
    }

    @Test
    void startComMencaoAoBot_eReconhecido() {
        assertEquals(TelegramBotAgent.BOAS_VINDAS, agent.tratarMensagem("/start@meu_bot"));
    }

    @Test
    void textoQualquer_respondeQueNaoEntendeu() {
        assertEquals(TelegramBotAgent.NAO_ENTENDI, agent.tratarMensagem("oi, tudo bem?"));
    }

    @Test
    void arrobaSozinho_naoQuebra() {
        assertEquals(TelegramBotAgent.NAO_ENTENDI, agent.tratarMensagem("@"));
    }

    @Test
    void consume_enviaARespostaParaOChatQueMandouAMensagem() throws TelegramApiException {
        agent.consume(atualizacaoComTexto(123456L, "/start"));

        ArgumentCaptor<SendMessage> enviada = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(enviada.capture());
        assertEquals("123456", enviada.getValue().getChatId());
        assertEquals(TelegramBotAgent.BOAS_VINDAS, enviada.getValue().getText());
    }

    @Test
    void consume_ignoraAtualizacaoSemMensagemDeTexto() {
        agent.consume(new Update());

        verifyNoInteractions(telegramClient);
    }

    private static Update atualizacaoComTexto(Long chatId, String texto) {
        Message mensagem = Message.builder().chat(new Chat(chatId, "private")).text(texto).build();
        Update update = new Update();
        update.setMessage(mensagem);
        return update;
    }
}
