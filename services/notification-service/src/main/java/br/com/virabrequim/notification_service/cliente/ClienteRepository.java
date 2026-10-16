package br.com.virabrequim.notification_service.cliente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByTelegramChatId(Long telegramChatId);
}
