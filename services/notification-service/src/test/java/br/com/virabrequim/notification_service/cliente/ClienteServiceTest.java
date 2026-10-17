package br.com.virabrequim.notification_service.cliente;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roda no Postgres local do docker-compose, com rollback no fim de cada teste.
 * Usa o banco de verdade porque o que importa aqui são as regras de unicidade.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ClienteService.class)
class ClienteServiceTest {

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private ClienteRepository clienteRepository;

    @Test
    void vincular_criaOClienteNaPrimeiraConexao() {
        clienteService.vincular("42", 1001L, "Ana", "ana");

        Cliente salvo = clienteRepository.findByUsuarioId("42").orElseThrow();
        assertEquals(1001L, salvo.getTelegramChatId());
        assertEquals("Ana", salvo.getNome());
        assertTrue(clienteService.estaConectado("42"));
    }

    @Test
    void vincular_reconectandoComOutroChat_atualizaSemDuplicar() {
        long antes = clienteRepository.count();

        clienteService.vincular("42", 1001L, "Ana", "ana");
        clienteService.vincular("42", 2002L, "Ana", "ana_nova");

        assertEquals(antes + 1, clienteRepository.count());
        Cliente salvo = clienteRepository.findByUsuarioId("42").orElseThrow();
        assertEquals(2002L, salvo.getTelegramChatId());
        assertEquals("ana_nova", salvo.getUsernameTelegram());
    }

    @Test
    void vincular_chatQueJaEraDeOutraConta_passaParaANovaConta() {
        long antes = clienteRepository.count();

        clienteService.vincular("42", 1001L, "Ana", "ana");
        clienteService.vincular("77", 1001L, "Ana", "ana");

        assertFalse(clienteService.estaConectado("42"));
        assertTrue(clienteService.estaConectado("77"));
        assertEquals(antes + 1, clienteRepository.count());
    }

    @Test
    void desconectar_removeOVinculoEPodeSerRepetido() {
        clienteService.vincular("42", 1001L, "Ana", "ana");

        clienteService.desconectar("42");
        clienteService.desconectar("42");

        assertFalse(clienteService.estaConectado("42"));
    }

    @Test
    void estaConectado_ehFalsoParaQuemNuncaConectou() {
        assertFalse(clienteService.estaConectado("nunca-conectou"));
    }
}
