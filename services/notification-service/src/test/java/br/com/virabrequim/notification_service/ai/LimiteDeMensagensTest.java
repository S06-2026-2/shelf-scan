package br.com.virabrequim.notification_service.ai;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimiteDeMensagensTest {

    private Instant agora = Instant.parse("2026-10-19T12:00:00Z");

    // Relógio que só anda quando o teste muda "agora".
    private final Clock relogio = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    };

    private final LimiteDeMensagens limite = new LimiteDeMensagens(2, relogio);

    @Test
    void permiteAteOMaximoPorMinuto_eBloqueiaOExcedente() {
        assertTrue(limite.permitir(1L));
        assertTrue(limite.permitir(1L));
        assertFalse(limite.permitir(1L));
    }

    @Test
    void liberaDeNovoDepoisDeUmMinuto() {
        limite.permitir(1L);
        limite.permitir(1L);
        assertFalse(limite.permitir(1L));

        agora = agora.plusSeconds(61);

        assertTrue(limite.permitir(1L));
    }

    @Test
    void contaCadaChatSeparadamente() {
        limite.permitir(1L);
        limite.permitir(1L);
        assertFalse(limite.permitir(1L));

        assertTrue(limite.permitir(2L));
    }
}
