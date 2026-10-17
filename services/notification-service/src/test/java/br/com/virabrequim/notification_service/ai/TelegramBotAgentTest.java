package br.com.virabrequim.notification_service.ai;

import br.com.virabrequim.notification_service.vinculo.ConexaoTelegramService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelegramBotAgentTest {

    private final TelegramClient telegramClient = mock(TelegramClient.class);
    private final ConexaoTelegramService conexao = mock(ConexaoTelegramService.class);
    private final TelegramBotAgent agent = new TelegramBotAgent(telegramClient, conexao);

    @Test
    void boasVindas_apresentamOBotPeloNomeQueOGrupoDeu() {
        assertTrue(TelegramBotAgent.BOAS_VINDAS.contains("Christopher"));
    }

    @Test
    void startSemCodigo_orientaAConectarPeloSite() {
        assertEquals(TelegramBotAgent.BOAS_VINDAS, agent.tratarMensagem(1L, "/start", "Ana", "ana"));

        verifyNoInteractions(conexao);
    }

    @Test
    void startComMencaoAoBotESemCodigo_orientaAConectarPeloSite() {
        assertEquals(TelegramBotAgent.BOAS_VINDAS, agent.tratarMensagem(1L, "/start@meu_bot", "Ana", "ana"));
    }

    @Test
    void startComCodigoValido_conectaEConfirma() {
        when(conexao.conectar("abc123", 1L, "Ana", "ana")).thenReturn(true);

        assertEquals(TelegramBotAgent.CONECTADO, agent.tratarMensagem(1L, "/start abc123", "Ana", "ana"));
    }

    @Test
    void startComCodigoInvalido_pedeUmNovoLink() {
        when(conexao.conectar("velho", 1L, "Ana", "ana")).thenReturn(false);

        assertEquals(TelegramBotAgent.CODIGO_INVALIDO, agent.tratarMensagem(1L, "/start velho", "Ana", "ana"));
    }

    @Test
    void textoQualquer_respondeQueNaoEntendeuESemTentarConectar() {
        assertEquals(TelegramBotAgent.NAO_ENTENDI, agent.tratarMensagem(1L, "oi, tudo bem?", "Ana", "ana"));

        verifyNoInteractions(conexao);
    }

    @Test
    void arrobaSozinho_naoQuebra() {
        assertEquals(TelegramBotAgent.NAO_ENTENDI, agent.tratarMensagem(1L, "@", "Ana", "ana"));
    }

    @Test
    void consume_enviaARespostaParaOChatQueMandouAMensagem() throws TelegramApiException {
        agent.consume(atualizacao(123456L, "/start", null));

        ArgumentCaptor<SendMessage> enviada = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(enviada.capture());
        assertEquals("123456", enviada.getValue().getChatId());
        assertEquals(TelegramBotAgent.BOAS_VINDAS, enviada.getValue().getText());
    }

    @Test
    void consume_repassaChatNomeEUsernameAoConectar() {
        User ana = User.builder().id(99L).isBot(false).firstName("Ana").userName("ana_leitora").build();

        agent.consume(atualizacao(123456L, "/start codigo-do-link", ana));

        verify(conexao).conectar("codigo-do-link", 123456L, "Ana", "ana_leitora");
    }

    @Test
    void consume_ignoraAtualizacaoSemMensagemDeTexto() {
        agent.consume(new Update());

        verifyNoInteractions(telegramClient);
    }

    private static Update atualizacao(Long chatId, String texto, User remetente) {
        Message mensagem = Message.builder()
                .chat(new Chat(chatId, "private"))
                .from(remetente)
                .text(texto)
                .build();
        Update update = new Update();
        update.setMessage(mensagem);
        return update;
    }
}
