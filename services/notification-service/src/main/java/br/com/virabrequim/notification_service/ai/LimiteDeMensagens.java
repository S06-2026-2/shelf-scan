package br.com.virabrequim.notification_service.ai;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class LimiteDeMensagens {

    private final int maximoPorMinuto;
    private final Clock relogio;
    private final Map<Long, Deque<Instant>> marcasPorChat = new ConcurrentHashMap<>();

    LimiteDeMensagens(int maximoPorMinuto, Clock relogio) {
        this.maximoPorMinuto = maximoPorMinuto;
        this.relogio = relogio;
    }

    boolean permitir(Long chatId) {
        Instant agora = relogio.instant();
        Deque<Instant> marcas = marcasPorChat.computeIfAbsent(chatId, id -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && marcas.peekFirst().isBefore(agora.minusSeconds(60))) {
                marcas.removeFirst();
            }
            if (marcas.size() >= maximoPorMinuto) {
                return false;
            }
            marcas.addLast(agora);
            return true;
        }
    }
}
