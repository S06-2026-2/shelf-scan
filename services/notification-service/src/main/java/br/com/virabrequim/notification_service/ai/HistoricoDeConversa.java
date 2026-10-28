package br.com.virabrequim.notification_service.ai;

import br.com.virabrequim.notification_service.ai.ClienteLlm.Papel;
import br.com.virabrequim.notification_service.ai.ClienteLlm.Turno;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class HistoricoDeConversa {

    private final int limite;
    private final Map<Long, Deque<Turno>> turnosPorChat = new ConcurrentHashMap<>();

    HistoricoDeConversa(int limite) {
        this.limite = limite;
    }

    List<Turno> registrar(Long chatId, Turno turno) {
        Deque<Turno> turnos = turnosPorChat.computeIfAbsent(chatId, id -> new ArrayDeque<>());
        synchronized (turnos) {
            turnos.addLast(turno);
            while (turnos.size() > limite) {
                turnos.removeFirst();
            }
            while (!turnos.isEmpty() && turnos.peekFirst().papel() != Papel.USUARIO) {
                turnos.removeFirst();
            }
            return List.copyOf(turnos);
        }
    }
}
