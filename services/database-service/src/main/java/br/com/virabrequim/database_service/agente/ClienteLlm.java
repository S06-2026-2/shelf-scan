package br.com.virabrequim.database_service.agente;

import java.util.List;
import java.util.Map;

// Porta para o modelo de linguagem: o agente não sabe qual LLM está do outro lado.
// Em produção é o GeminiCliente; nos testes, um falso que devolve um JSON fixo.
public interface ClienteLlm {

    // Envia o prompt, executa as ferramentas que o modelo pedir e devolve o JSON final, seguindo o schema
    String gerarJson(String prompt, Map<String, Object> schema, List<Ferramenta> ferramentas);

    // Nome do modelo, para a resposta dizer de onde vieram os dados
    String modelo();
}
