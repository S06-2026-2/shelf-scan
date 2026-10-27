package br.com.virabrequim.database_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String PREFIXO_API = "/api/v1";

    // Todo @RestController ganha o prefixo; os endpoints do actuator (/actuator/health) não são afetados
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(PREFIXO_API, HandlerTypePredicate.forAnnotation(RestController.class));
    }
}
