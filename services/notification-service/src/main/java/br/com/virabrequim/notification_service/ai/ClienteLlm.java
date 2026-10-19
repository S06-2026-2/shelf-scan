package br.com.virabrequim.notification_service.ai;

import java.util.List;

public interface ClienteLlm {

    enum Papel { USUARIO, ASSISTENTE }

    record Turno(Papel papel, String texto) {}

    String conversar(String instrucoes, List<Turno> turnos);
}
