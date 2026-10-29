package br.com.virabrequim.database_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Verificação rápida de que a API está no ar, dentro do prefixo /api/v1 (GET /api/v1/status).
// Não consulta o banco: para isso, use o /actuator/health, que verifica também o Neo4j.
@RestController
public class StatusController {

    @GetMapping("/status")
    public Map<String, String> status() {
        return Map.of("status", "ok");
    }
}
