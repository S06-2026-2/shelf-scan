package br.com.virabrequim.database_service.tempo;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class Calendario {
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private final Clock clock;

    public Calendario(){
        this(Clock.system(FUSO));
    }

    // sem modificador de acesso, assim somente classes dentro do mesmo pacote(Testes) podem chama-los
    Calendario(Clock clock){
        this.clock = clock;
    }

    public LocalDate diaHoje(){
        return LocalDate.now(clock);
    }

    public Clock getClock() {
        return clock;
    }
}
