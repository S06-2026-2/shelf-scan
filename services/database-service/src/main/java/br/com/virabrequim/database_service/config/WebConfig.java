package br.com.virabrequim.database_service.config;

import br.com.virabrequim.database_service.domain.DestinoLivros;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String PREFIXO_API = "/api/v1";

    // Todo @RestController ganha o prefixo; os endpoints do actuator (/actuator/health) não são afetados
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(PREFIXO_API, HandlerTypePredicate.forAnnotation(RestController.class));
    }

    // Query params de enum: o Spring converte pelo nome exato (APAGAR). Este conversor aceita também minúsculas
    // (?livros=apagar), como no resto do contrato. Valor desconhecido continua dando 400.
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, DestinoLivros.class,
                valor -> DestinoLivros.valueOf(valor.strip().toUpperCase(Locale.ROOT)));
    }
}
