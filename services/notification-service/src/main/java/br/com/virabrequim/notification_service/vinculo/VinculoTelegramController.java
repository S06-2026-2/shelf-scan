package br.com.virabrequim.notification_service.vinculo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints chamados pelo front para o usuário conectar ou desconectar o Telegram.
 */
@RestController
@RequestMapping("/telegram/vinculos")
public class VinculoTelegramController {

    public record GerarVinculoRequest(@NotBlank String usuarioId) {
    }

    public record VinculoGeradoResponse(String link) {
    }

    public record StatusVinculoResponse(boolean conectado) {
    }

    private final ConexaoTelegramService conexaoTelegramService;

    public VinculoTelegramController(ConexaoTelegramService conexaoTelegramService) {
        this.conexaoTelegramService = conexaoTelegramService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VinculoGeradoResponse gerar(@Valid @RequestBody GerarVinculoRequest requisicao) {
        return new VinculoGeradoResponse(conexaoTelegramService.gerarLink(requisicao.usuarioId()));
    }

    @GetMapping("/{usuarioId}")
    public StatusVinculoResponse status(@PathVariable String usuarioId) {
        return new StatusVinculoResponse(conexaoTelegramService.estaConectado(usuarioId));
    }

    @DeleteMapping("/{usuarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desconectar(@PathVariable String usuarioId) {
        conexaoTelegramService.desconectar(usuarioId);
    }
}
