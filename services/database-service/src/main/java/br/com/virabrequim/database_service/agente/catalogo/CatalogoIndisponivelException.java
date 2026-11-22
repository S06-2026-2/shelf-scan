package br.com.virabrequim.database_service.agente.catalogo;

// O catálogo não respondeu. Não derruba o enriquecimento: o agente é avisado e segue sem essa consulta.
public class CatalogoIndisponivelException extends RuntimeException {

    public CatalogoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
