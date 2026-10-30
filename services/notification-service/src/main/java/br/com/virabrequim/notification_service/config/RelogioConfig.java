package br.com.virabrequim.notification_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class RelogioConfig {

    // Injetado onde o horário importa, para os testes poderem fixar "agora".
    @Bean
    public Clock relogio() {
        return Clock.systemDefaultZone();
    }
}
