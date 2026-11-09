package br.com.virabrequim.database_service.agente;

// O agente não conseguiu responder (sem chave, LLM fora do ar, cota estourada, tempo limite ou resposta inválida).
// Vira 503 no TratadorDeErros: o livro não é alterado.
public class AgenteIndisponivelException extends RuntimeException {

    public AgenteIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public AgenteIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
