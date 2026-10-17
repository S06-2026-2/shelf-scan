package br.com.virabrequim.notification_service.cliente;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    /**
     * Liga a conta (usuarioId) ao chat do Telegram.
     * - Se a conta já tinha um vínculo, o chat dela é atualizado (reconexão).
     * - Se o chat já estava ligado a outra conta, esse outro vínculo é removido:
     *   um chat só pode pertencer a uma conta por vez.
     */
    @Transactional
    public Cliente vincular(String usuarioId, Long telegramChatId, String nome, String usernameTelegram) {
        Cliente cliente = clienteRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> new Cliente(usuarioId, telegramChatId, nome, usernameTelegram));

        clienteRepository.findByTelegramChatId(telegramChatId)
                .filter(outro -> !Objects.equals(outro.getId(), cliente.getId()))
                .ifPresent(outro -> {
                    clienteRepository.delete(outro);
                    clienteRepository.flush();
                });

        cliente.setTelegramChatId(telegramChatId);
        cliente.setNome(nome);
        cliente.setUsernameTelegram(usernameTelegram);
        cliente.setAtivo(true);
        return clienteRepository.save(cliente);
    }

    public boolean estaConectado(String usuarioId) {
        return clienteRepository.findByUsuarioId(usuarioId)
                .filter(Cliente::isAtivo)
                .isPresent();
    }

    @Transactional
    public void desconectar(String usuarioId) {
        clienteRepository.findByUsuarioId(usuarioId).ifPresent(clienteRepository::delete);
    }

    public Optional<Cliente> buscarPorChatId(Long telegramChatId) {
        return clienteRepository.findByTelegramChatId(telegramChatId);
    }
}
