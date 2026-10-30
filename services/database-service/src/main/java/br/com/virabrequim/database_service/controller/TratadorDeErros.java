package br.com.virabrequim.database_service.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// Traduz as exceções do domínio para status HTTP no formato ProblemDetail (RFC 9457), num lugar só.
// A classe pai já trata as exceções do próprio Spring MVC (JSON malformado, tipo errado na rota etc.).
@RestControllerAdvice
public class TratadorDeErros extends ResponseEntityExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail valorInvalido(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Também quando o recurso existe mas é de outro usuário: não revela dados de outras pessoas
    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail naoEncontrado(NoSuchElementException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail conflito(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Mudança: violação de constraint do Neo4j (hoje só a de email único) vira 409 em vez de 500. Só acontece quando
    // duas requisições passam juntas pelo existsByEmail; a mensagem do banco não é repassada ao cliente.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail violacaoDeRestricao(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Já existe um registro com esses dados (ex.: email em uso).");
    }

    // Stubs do TDD (métodos ainda não implementados) respondem 501 em vez de 500
    @ExceptionHandler(UnsupportedOperationException.class)
    public ProblemDetail naoImplementado(UnsupportedOperationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, "Funcionalidade ainda não implementada.");
    }

    // Erro do @Valid: acrescenta ao ProblemDetail padrão a lista de campos inválidos
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> Map.of(
                        "campo", erro.getField(),
                        "mensagem", String.valueOf(erro.getDefaultMessage())))
                .toList();

        ProblemDetail corpo = ex.getBody();
        corpo.setDetail("Corpo da requisição inválido.");
        corpo.setProperty("campos", campos);
        return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }
}
