package br.com.virabrequim.notification_service.ai;

import br.com.virabrequim.notification_service.ai.ClienteLlm.Papel;
import br.com.virabrequim.notification_service.ai.ClienteLlm.Turno;
import br.com.virabrequim.notification_service.cliente.Cliente;
import br.com.virabrequim.notification_service.cliente.ClienteService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgenteConversacionalTest {

    private static final Long CHAT = 123L;

    private final ClienteLlm llm = mock(ClienteLlm.class);
    private final ClienteService clienteService = mock(ClienteService.class);
    // Relógio parado: todas as mensagens do teste caem dentro do mesmo minuto.
    private final Clock relogio = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);

    private AgenteConversacional agente() {
        return new AgenteConversacional(Optional.of(llm), clienteService, relogio, 2, 4, false);
    }

    private void vincularChat(boolean ativo) {
        Cliente cliente = new Cliente("teste-1", CHAT, "Ana", "ana");
        cliente.setAtivo(ativo);
        when(clienteService.buscarPorChatId(CHAT)).thenReturn(Optional.of(cliente));
    }

    @Test
    void semChave_devolveVazio_eNaoConsultaNada() {
        AgenteConversacional semChave = new AgenteConversacional(Optional.empty(), clienteService, relogio, 2, 4, false);

        assertTrue(semChave.responder(CHAT, "oi").isEmpty());

        verifyNoInteractions(clienteService);
    }

    @Test
    void chatSemVinculo_pedeParaConectar_eNaoChamaOModelo() {
        when(clienteService.buscarPorChatId(CHAT)).thenReturn(Optional.empty());

        assertEquals(Optional.of(AgenteConversacional.SEM_VINCULO), agente().responder(CHAT, "oi"));

        verifyNoInteractions(llm);
    }

    @Test
    void vinculoInativo_contaComoSemVinculo() {
        vincularChat(false);

        assertEquals(Optional.of(AgenteConversacional.SEM_VINCULO), agente().responder(CHAT, "oi"));

        verifyNoInteractions(llm);
    }

    @Test
    void chatVinculado_enviaInstrucoesEPerguntaEDevolveARespostaDoModelo() {
        vincularChat(true);
        when(llm.conversar(anyString(), anyList())).thenReturn("Leia Dom Casmurro.");

        Optional<String> resposta = agente().responder(CHAT, "me indica um livro");

        assertEquals(Optional.of("Leia Dom Casmurro."), resposta);
        verify(llm).conversar(AgenteConversacional.INSTRUCOES, List.of(new Turno(Papel.USUARIO, "me indica um livro")));
    }

    @Test
    void segundaMensagem_levaOHistoricoDaPrimeira() {
        vincularChat(true);
        when(llm.conversar(anyString(), anyList())).thenReturn("Resposta 1", "Resposta 2");
        AgenteConversacional agente = agente();

        agente.responder(CHAT, "pergunta 1");
        agente.responder(CHAT, "pergunta 2");

        verify(llm).conversar(AgenteConversacional.INSTRUCOES, List.of(
                new Turno(Papel.USUARIO, "pergunta 1"),
                new Turno(Papel.ASSISTENTE, "Resposta 1"),
                new Turno(Papel.USUARIO, "pergunta 2")));
    }

    @Test
    void acimaDoLimitePorMinuto_pedeEspera_eNaoChamaOModelo() {
        vincularChat(true);
        when(llm.conversar(anyString(), anyList())).thenReturn("ok");
        AgenteConversacional agente = agente();

        agente.responder(CHAT, "um");
        agente.responder(CHAT, "dois");
        Optional<String> terceira = agente.responder(CHAT, "três");

        assertEquals(Optional.of(AgenteConversacional.LIMITE_ATINGIDO), terceira);
        verify(llm, times(2)).conversar(anyString(), anyList());
    }

    @Test
    void falhaDoModelo_devolveMensagemDeIndisponivel_eNaoVazaOTexto() {
        vincularChat(true);
        when(llm.conversar(anyString(), anyList())).thenThrow(new RuntimeException("detalhe-interno-do-erro"));

        String resposta = agente().responder(CHAT, "oi").orElseThrow();

        assertEquals(AgenteConversacional.INDISPONIVEL, resposta);
        assertFalse(resposta.contains("detalhe-interno"));
    }

    @Test
    void respostaComMarkdown_chegaLimpa_eOHistoricoGuardaALimpa() {
        vincularChat(true);
        when(llm.conversar(anyString(), anyList())).thenReturn("**Leia** *Dom Casmurro*.", "ok");
        AgenteConversacional agente = agente();

        Optional<String> primeira = agente.responder(CHAT, "me indica um livro");
        agente.responder(CHAT, "e outro?");

        assertEquals(Optional.of("Leia Dom Casmurro."), primeira);
        verify(llm).conversar(AgenteConversacional.INSTRUCOES, List.of(
                new Turno(Papel.USUARIO, "me indica um livro"),
                new Turno(Papel.ASSISTENTE, "Leia Dom Casmurro."),
                new Turno(Papel.USUARIO, "e outro?")));
    }

    @Test
    void respostaSoComMarcas_viraIndisponivel() {
        vincularChat(true);
        when(llm.conversar(anyString(), anyList())).thenReturn("**");

        assertEquals(Optional.of(AgenteConversacional.INDISPONIVEL), agente().responder(CHAT, "oi"));
    }

    @Test
    void comBuscaNaWeb_asInstrucoesPedemPesquisa() {
        vincularChat(true);
        when(llm.conversar(anyString(), anyList())).thenReturn("ok");
        AgenteConversacional comBusca = new AgenteConversacional(Optional.of(llm), clienteService, relogio, 2, 4, true);

        comBusca.responder(CHAT, "oi");

        verify(llm).conversar(
                AgenteConversacional.INSTRUCOES + AgenteConversacional.INSTRUCOES_DE_BUSCA,
                List.of(new Turno(Papel.USUARIO, "oi")));
    }

    @Test
    void textoMuitoLongo_vaiCortadoEm1000Caracteres() {
        vincularChat(true);
        when(llm.conversar(anyString(), anyList())).thenReturn("ok");

        agente().responder(CHAT, "a".repeat(1500));

        verify(llm).conversar(AgenteConversacional.INSTRUCOES, List.of(new Turno(Papel.USUARIO, "a".repeat(1000))));
    }
}
