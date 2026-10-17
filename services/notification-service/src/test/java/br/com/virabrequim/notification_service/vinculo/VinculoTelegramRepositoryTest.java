package br.com.virabrequim.notification_service.vinculo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Roda no Postgres local do docker-compose (o mesmo do contextLoads). Cada teste é desfeito
 * no fim (rollback), então nada fica gravado.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VinculoTelegramRepositoryTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 15, 20, 0);

    @Autowired
    private VinculoTelegramRepository repository;

    @Test
    void codigoValido_podeSerUsadoUmaUnicaVez() {
        repository.save(new VinculoTelegram("codigo-valido", "42", AGORA.minusMinutes(1), AGORA.plusMinutes(14)));

        assertEquals(1, repository.marcarComoUsado("codigo-valido", AGORA));
        assertEquals(0, repository.marcarComoUsado("codigo-valido", AGORA));
        assertNotNull(repository.findById("codigo-valido").orElseThrow().getUsadoEm());
    }

    @Test
    void codigoExpirado_naoPodeSerUsado() {
        repository.save(new VinculoTelegram("codigo-expirado", "42", AGORA.minusMinutes(30), AGORA.minusMinutes(15)));

        assertEquals(0, repository.marcarComoUsado("codigo-expirado", AGORA));
    }

    @Test
    void codigoInexistente_naoPodeSerUsado() {
        assertEquals(0, repository.marcarComoUsado("nao-existe", AGORA));
    }
}
