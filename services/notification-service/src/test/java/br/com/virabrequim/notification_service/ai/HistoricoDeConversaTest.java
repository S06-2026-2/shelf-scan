package br.com.virabrequim.notification_service.ai;

import br.com.virabrequim.notification_service.ai.ClienteLlm.Papel;
import br.com.virabrequim.notification_service.ai.ClienteLlm.Turno;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoricoDeConversaTest {

    private static final Turno U1 = new Turno(Papel.USUARIO, "pergunta 1");
    private static final Turno A1 = new Turno(Papel.ASSISTENTE, "resposta 1");
    private static final Turno U2 = new Turno(Papel.USUARIO, "pergunta 2");
    private static final Turno A2 = new Turno(Papel.ASSISTENTE, "resposta 2");

    @Test
    void guardaOsTurnosNaOrdem() {
        HistoricoDeConversa historico = new HistoricoDeConversa(4);

        historico.registrar(1L, U1);
        List<Turno> turnos = historico.registrar(1L, A1);

        assertEquals(List.of(U1, A1), turnos);
    }

    @Test
    void mantemSoOsUltimosEComecaPelaPergunta() {
        HistoricoDeConversa historico = new HistoricoDeConversa(3);

        historico.registrar(1L, U1);
        historico.registrar(1L, A1);
        historico.registrar(1L, U2);
        List<Turno> turnos = historico.registrar(1L, A2);

        // O limite 3 deixaria [A1, U2, A2]; a resposta A1 é descartada para a lista começar pelo usuário.
        assertEquals(List.of(U2, A2), turnos);
    }

    @Test
    void separaOsChats() {
        HistoricoDeConversa historico = new HistoricoDeConversa(4);

        historico.registrar(1L, U1);
        List<Turno> doOutroChat = historico.registrar(2L, U2);

        assertEquals(List.of(U2), doOutroChat);
    }
}
