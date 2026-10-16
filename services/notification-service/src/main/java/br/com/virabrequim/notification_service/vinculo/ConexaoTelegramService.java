package br.com.virabrequim.notification_service.vinculo;

import br.com.virabrequim.notification_service.cliente.ClienteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Ponto único do fluxo "conectar o Telegram": o front e o bot passam por aqui.
 */
@Service
public class ConexaoTelegramService {

    private final VinculoTelegramService vinculoTelegramService;
    private final ClienteService clienteService;
    private final String usernameDoBot;

    public ConexaoTelegramService(
            VinculoTelegramService vinculoTelegramService,
            ClienteService clienteService,
            @Value("${telegram.bot.username:}") String usernameDoBot) {
        this.vinculoTelegramService = vinculoTelegramService;
        this.clienteService = clienteService;
        this.usernameDoBot = usernameDoBot;
    }

    public String gerarLink(String usuarioId) {
        if (usernameDoBot.isBlank()) {
            throw new TelegramNaoConfiguradoException("TELEGRAM_BOT_USERNAME");
        }

        String codigo = vinculoTelegramService.gerarCodigo(usuarioId);
        return "https://t.me/" + usernameDoBot + "?start=" + codigo;
    }

    /**
     * Chamado pelo bot quando alguém manda "/start <código>".
     * Devolve true se o código era válido e o vínculo foi gravado.
     */
    public boolean conectar(String codigo, Long telegramChatId, String nome, String usernameTelegram) {
        Optional<String> usuarioId = vinculoTelegramService.consumirCodigo(codigo);
        usuarioId.ifPresent(id -> clienteService.vincular(id, telegramChatId, nome, usernameTelegram));
        return usuarioId.isPresent();
    }

    public boolean estaConectado(String usuarioId) {
        return clienteService.estaConectado(usuarioId);
    }

    public void desconectar(String usuarioId) {
        clienteService.desconectar(usuarioId);
    }
}
