package br.com.virabrequim.notification_service.vinculo;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VinculoTelegramServiceTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 15, 20, 0);
    private static final Clock RELOGIO_FIXO =
            Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private final VinculoTelegramRepository repository = mock(VinculoTelegramRepository.class);
    private final VinculoTelegramService service =
            new VinculoTelegramService(repository, RELOGIO_FIXO, 15);

    @Test
    void gerarCodigo_salvaVinculoComValidadeDeQuinzeMinutos() {
        String codigo = service.gerarCodigo("42");

        ArgumentCaptor<VinculoTelegram> salvo = ArgumentCaptor.forClass(VinculoTelegram.class);
        verify(repository).save(salvo.capture());
        assertEquals(codigo, salvo.getValue().getCodigo());
        assertEquals("42", salvo.getValue().getUsuarioId());
        assertEquals(AGORA, salvo.getValue().getCriadoEm());
        assertEquals(AGORA.plusMinutes(15), salvo.getValue().getExpiraEm());
    }

    @Test
    void gerarCodigo_usaSomenteCaracteresAceitosPeloTelegram() {
        String codigo = service.gerarCodigo("42");

        assertTrue(codigo.matches("[A-Za-z0-9_-]{22}"), "código inesperado: " + codigo.length() + " caracteres");
    }

    @Test
    void gerarCodigo_geraCodigosDiferentes() {
        assertNotEquals(service.gerarCodigo("42"), service.gerarCodigo("42"));
    }

    @Test
    void gerarCodigo_recusaUsuarioEmBranco() {
        assertThrows(IllegalArgumentException.class, () -> service.gerarCodigo("  "));

        verifyNoInteractions(repository);
    }

    @Test
    void consumirCodigo_devolveOUsuarioQuandoOCodigoEstaValido() {
        VinculoTelegram vinculo = new VinculoTelegram("abc", "42", AGORA.minusMinutes(1), AGORA.plusMinutes(14));
        when(repository.marcarComoUsado("abc", AGORA)).thenReturn(1);
        when(repository.findById("abc")).thenReturn(Optional.of(vinculo));

        assertEquals(Optional.of("42"), service.consumirCodigo("abc"));
    }

    @Test
    void consumirCodigo_devolveVazioQuandoOCodigoNaoServe() {
        when(repository.marcarComoUsado("abc", AGORA)).thenReturn(0);

        assertEquals(Optional.empty(), service.consumirCodigo("abc"));
        verify(repository, never()).findById(any());
    }

    @Test
    void consumirCodigo_ignoraCodigoNuloOuEmBranco() {
        assertEquals(Optional.empty(), service.consumirCodigo(null));
        assertEquals(Optional.empty(), service.consumirCodigo(" "));

        verifyNoInteractions(repository);
    }
}
