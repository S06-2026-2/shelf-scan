package br.com.virabrequim.notification_service.notificacao;

import br.com.virabrequim.notification_service.ai.MessageFormatterAgent;
import br.com.virabrequim.notification_service.cliente.Cliente;
import br.com.virabrequim.notification_service.cliente.ClienteService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificacaoServiceTest {

    private final ClienteService clientes = mock(ClienteService.class);
    private final TelegramNotifier notifier = mock(TelegramNotifier.class);
    private final NotificacaoService service =
            new NotificacaoService(clientes, new MessageFormatterAgent(), notifier);

    private final Cliente ana = new Cliente("42", 777L, "Ana", "ana");

    @Test
    void usuarioSemVinculo_naoEnviaNada() {
        when(clientes.buscarConectado("42")).thenReturn(Optional.empty());

        assertEquals(ResultadoNotificacao.SEM_VINCULO, service.notificarLivroSalvo("42", "Dom Casmurro", "Machado"));

        verifyNoInteractions(notifier);
    }

    @Test
    void usuarioConectado_recebeAMensagemNoChatDele() {
        when(clientes.buscarConectado("42")).thenReturn(Optional.of(ana));
        when(notifier.enviar(777L, "Livro salvo na sua estante: \"Dom Casmurro\", de Machado.")).thenReturn(true);

        assertEquals(ResultadoNotificacao.ENVIADO, service.notificarLivroSalvo("42", "Dom Casmurro", "Machado"));

        verify(notifier).enviar(777L, "Livro salvo na sua estante: \"Dom Casmurro\", de Machado.");
    }

    @Test
    void telegramRecusou_resultadoEFalhaNoEnvio() {
        when(clientes.buscarConectado("42")).thenReturn(Optional.of(ana));
        when(notifier.enviar(777L, "Livro salvo na sua estante: \"Dom Casmurro\".")).thenReturn(false);

        assertEquals(ResultadoNotificacao.FALHA_NO_ENVIO, service.notificarLivroSalvo("42", "Dom Casmurro", null));
    }
}
