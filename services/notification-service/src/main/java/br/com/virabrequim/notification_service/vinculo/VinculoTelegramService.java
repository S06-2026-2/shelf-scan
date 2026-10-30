package br.com.virabrequim.notification_service.vinculo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
public class VinculoTelegramService {

    private static final int BYTES_DO_CODIGO = 16;

    private final VinculoTelegramRepository repository;
    private final Clock relogio;
    private final long validadeMinutos;
    private final SecureRandom aleatorio = new SecureRandom();

    public VinculoTelegramService(
            VinculoTelegramRepository repository,
            Clock relogio,
            @Value("${telegram.vinculo.validade-minutos:15}") long validadeMinutos) {
        this.repository = repository;
        this.relogio = relogio;
        this.validadeMinutos = validadeMinutos;
    }

    public String gerarCodigo(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new IllegalArgumentException("usuarioId é obrigatório");
        }

        LocalDateTime agora = LocalDateTime.now(relogio);
        String codigo = novoCodigo();
        repository.save(new VinculoTelegram(codigo, usuarioId, agora, agora.plusMinutes(validadeMinutos)));
        return codigo;
    }

    /**
     * Gasta o código e devolve o usuarioId a que ele pertence.
     * Vazio quando o código não existe, já foi usado ou expirou.
     */
    @Transactional
    public Optional<String> consumirCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }

        int atualizados = repository.marcarComoUsado(codigo, LocalDateTime.now(relogio));
        if (atualizados == 0) {
            return Optional.empty();
        }

        return repository.findById(codigo).map(VinculoTelegram::getUsuarioId);
    }

    // 16 bytes aleatórios em Base64 seguro para URL = 22 caracteres [A-Za-z0-9_-],
    // que é exatamente o alfabeto aceito no parâmetro ?start= do Telegram.
    private String novoCodigo() {
        byte[] bytes = new byte[BYTES_DO_CODIGO];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
