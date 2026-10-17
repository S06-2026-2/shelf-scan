package br.com.virabrequim.notification_service.notificacao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints chamados pelo database-service quando algo acontece com o usuário.
 */
@RestController
@RequestMapping("/notificacoes")
public class NotificacaoController {

    public record LivroSalvoRequest(@NotBlank String usuarioId, @NotBlank String titulo, String autor) {
    }

    public record ResultadoResponse(ResultadoNotificacao resultado) {
    }

    private final NotificacaoService notificacaoService;

    public NotificacaoController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    /**
     * 200 com ENVIADO ou SEM_VINCULO (os dois são desfechos normais: não há o que reenviar).
     * 502 com FALHA_NO_ENVIO quando o Telegram recusou, para quem chama poder tentar de novo.
     */
    @PostMapping("/livro-salvo")
    public ResponseEntity<ResultadoResponse> livroSalvo(@Valid @RequestBody LivroSalvoRequest requisicao) {
        ResultadoNotificacao resultado = notificacaoService.notificarLivroSalvo(
                requisicao.usuarioId(), requisicao.titulo(), requisicao.autor());

        HttpStatus status = resultado == ResultadoNotificacao.FALHA_NO_ENVIO
                ? HttpStatus.BAD_GATEWAY
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(new ResultadoResponse(resultado));
    }
}
