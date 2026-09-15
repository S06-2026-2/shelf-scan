package br.com.virabrequim.notification_service.cliente;

import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    public Cliente buscarOuCadastrar(Long telegramChatId, String nome, String usernameTelegram) {
        return clienteRepository.findByTelegramChatId(telegramChatId)
                .orElseGet(() -> clienteRepository.save(
                        new Cliente(telegramChatId, nome, usernameTelegram)
                ));
    }

    public Optional<Cliente> buscarPorChatId(Long telegramChatId) {
        return clienteRepository.findByTelegramChatId(telegramChatId);
    }
}
