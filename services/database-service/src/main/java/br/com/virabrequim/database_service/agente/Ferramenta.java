package br.com.virabrequim.database_service.agente;

import java.util.Map;
import java.util.function.Function;

// Função que o LLM pode chamar durante o raciocínio (function calling).
// `parametros` é o schema dos argumentos; `executar` recebe os argumentos e devolve o resultado para o LLM.
public record Ferramenta(
        String nome,
        String descricao,
        Map<String, Object> parametros,
        Function<Map<String, Object>, Map<String, Object>> executar
) {
}
