package br.com.virabrequim.notification_service.ai;

import br.com.virabrequim.notification_service.vinculo.ConexaoTelegramService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelegramBotAgentTest {

    private final TelegramClient telegramClient = mock(TelegramClient.class);
    private final ConexaoTelegramService conexao = mock(ConexaoTelegramService.class);
    private final AgenteConversacional agenteConversacional = mock(AgenteConversacional.class);
    private final AgenteDeFotos agenteDeFotos = mock(AgenteDeFotos.class);
    // Runnable::run executa a conversa na própria thread do teste, sem esperar nada em segundo plano.
    private final TelegramBotAgent agent =
            new TelegramBotAgent(telegramClient, conexao, agenteConversacional, agenteDeFotos, Runnable::run);

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
    void comandoDesconhecido_respondeQueNaoEntendeuESemTentarConectar() {
        assertEquals(TelegramBotAgent.NAO_ENTENDI, agent.tratarMensagem(1L, "/ajuda", "Ana", "ana"));

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
    void consume_textoComum_mostraDigitandoEEnviaARespostaDoAgente() throws TelegramApiException {
        when(agenteConversacional.responder(777L, "me indica um livro")).thenReturn(Optional.of("Que tal Dom Casmurro?"));

        agent.consume(atualizacao(777L, "me indica um livro", null));

        InOrder ordem = inOrder(telegramClient);
        ordem.verify(telegramClient).execute(any(SendChatAction.class));
        ArgumentCaptor<SendMessage> enviada = ArgumentCaptor.forClass(SendMessage.class);
        ordem.verify(telegramClient).execute(enviada.capture());
        assertEquals("777", enviada.getValue().getChatId());
        assertEquals("Que tal Dom Casmurro?", enviada.getValue().getText());
    }

    @Test
    void consume_agenteDesativado_respondeNaoEntendi() throws TelegramApiException {
        when(agenteConversacional.responder(777L, "oi")).thenReturn(Optional.empty());

        agent.consume(atualizacao(777L, "oi", null));

        ArgumentCaptor<SendMessage> enviada = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(enviada.capture());
        assertEquals(TelegramBotAgent.NAO_ENTENDI, enviada.getValue().getText());
    }

    @Test
    void consume_comandoDesconhecido_naoChamaOAgente() {
        agent.consume(atualizacao(777L, "/ajuda", null));

        verifyNoInteractions(agenteConversacional);
    }

    @Test
    @SuppressWarnings("unchecked")
    void consume_foto_baixaOMaiorTamanho_eEnviaARespostaDoAgenteDeFotos() throws Exception {
        org.telegram.telegrambots.meta.api.objects.File arquivo =
                new org.telegram.telegrambots.meta.api.objects.File("grande", "u2", 2048L, "photos/capa.jpg");
        when(telegramClient.execute(any(GetFile.class))).thenReturn(arquivo);
        when(telegramClient.downloadFileAsStream(arquivo)).thenReturn(new ByteArrayInputStream(new byte[] {7, 8}));
        when(agenteDeFotos.processar(eq(777L), any(Supplier.class))).thenAnswer(chamada -> {
            Supplier<byte[]> baixar = chamada.getArgument(1);
            assertArrayEquals(new byte[] {7, 8}, baixar.get());
            return "Guardei na sua estante: Livro.";
        });

        agent.consume(atualizacaoComFoto(777L));

        ArgumentCaptor<GetFile> pedido = ArgumentCaptor.forClass(GetFile.class);
        verify(telegramClient).execute(pedido.capture());
        assertEquals("grande", pedido.getValue().getFileId());
        ArgumentCaptor<SendMessage> enviada = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(enviada.capture());
        assertEquals("777", enviada.getValue().getChatId());
        assertEquals("Guardei na sua estante: Livro.", enviada.getValue().getText());
        verifyNoInteractions(agenteConversacional);
    }

    @Test
    void consume_ignoraAtualizacaoSemMensagemDeTexto() {
        agent.consume(new Update());

        verifyNoInteractions(telegramClient);
    }

    // O Telegram manda a mesma foto em vários tamanhos, do menor para o maior
    private static Update atualizacaoComFoto(Long chatId) {
        PhotoSize pequena = PhotoSize.builder().fileId("pequena").fileUniqueId("u1").width(90).height(120).build();
        PhotoSize grande = PhotoSize.builder().fileId("grande").fileUniqueId("u2").width(900).height(1200).build();
        Message mensagem = Message.builder()
                .chat(new Chat(chatId, "private"))
                .photo(List.of(pequena, grande))
                .build();
        Update update = new Update();
        update.setMessage(mensagem);
        return update;
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
