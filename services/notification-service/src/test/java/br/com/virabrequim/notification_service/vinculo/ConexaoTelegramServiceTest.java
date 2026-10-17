package br.com.virabrequim.notification_service.vinculo;

import br.com.virabrequim.notification_service.cliente.ClienteService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConexaoTelegramServiceTest {

    private final VinculoTelegramService vinculos = mock(VinculoTelegramService.class);
    private final ClienteService clientes = mock(ClienteService.class);
    private final ConexaoTelegramService service = new ConexaoTelegramService(vinculos, clientes, "shelf_scan_bot");

    @Test
    void gerarLink_montaOLinkDoBotComOCodigo() {
        when(vinculos.gerarCodigo("42")).thenReturn("CODIGO123");

        assertEquals("https://t.me/shelf_scan_bot?start=CODIGO123", service.gerarLink("42"));
    }

    @Test
    void gerarLink_falhaQuandoOUsernameDoBotNaoEstaConfigurado() {
        ConexaoTelegramService semBot = new ConexaoTelegramService(vinculos, clientes, "");

        assertThrows(TelegramNaoConfiguradoException.class, () -> semBot.gerarLink("42"));
        verifyNoInteractions(vinculos);
    }

    @Test
    void conectar_comCodigoValido_vinculaOChatAoUsuarioDoCodigo() {
        when(vinculos.consumirCodigo("abc")).thenReturn(Optional.of("42"));

        assertTrue(service.conectar("abc", 777L, "Ana", "ana"));

        verify(clientes).vincular("42", 777L, "Ana", "ana");
    }

    @Test
    void conectar_comCodigoInvalido_naoVinculaNada() {
        when(vinculos.consumirCodigo("abc")).thenReturn(Optional.empty());

        assertFalse(service.conectar("abc", 777L, "Ana", "ana"));

        verifyNoInteractions(clientes);
    }

    @Test
    void estaConectadoEDesconectar_delegamAoClienteService() {
        when(clientes.estaConectado("42")).thenReturn(true);

        assertTrue(service.estaConectado("42"));
        service.desconectar("42");

        verify(clientes).desconectar("42");
    }
}
