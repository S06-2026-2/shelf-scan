package br.com.virabrequim.notification_service.notificacao;

import br.com.virabrequim.notification_service.ai.MessageFormatterAgent;
import br.com.virabrequim.notification_service.cliente.Cliente;
import br.com.virabrequim.notification_service.cliente.ClienteService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class NotificacaoService {

    private final ClienteService clienteService;
    private final MessageFormatterAgent formatter;
    private final TelegramNotifier notifier;

    public NotificacaoService(
            ClienteService clienteService,
            MessageFormatterAgent formatter,
            TelegramNotifier notifier) {
        this.clienteService = clienteService;
        this.formatter = formatter;
        this.notifier = notifier;
    }

    /**
     * Avisa o usuário no Telegram que o livro foi salvo.
     * Quem não conectou o Telegram não é erro: o resultado é SEM_VINCULO e nada é enviado.
     */
    public ResultadoNotificacao notificarLivroSalvo(String usuarioId, String titulo, String autor) {
        Optional<Cliente> cliente = clienteService.buscarConectado(usuarioId);
        if (cliente.isEmpty()) {
            return ResultadoNotificacao.SEM_VINCULO;
        }

        String texto = formatter.livroSalvo(titulo, autor);
        boolean enviado = notifier.enviar(cliente.get().getTelegramChatId(), texto);
        return enviado ? ResultadoNotificacao.ENVIADO : ResultadoNotificacao.FALHA_NO_ENVIO;
    }
}
