package br.com.virabrequim.database_service.controller;

import br.com.virabrequim.database_service.config.WebConfig;
import br.com.virabrequim.database_service.dto.Amigo;
import br.com.virabrequim.database_service.dto.AmizadeResposta;
import br.com.virabrequim.database_service.dto.NovaSolicitacaoAmizade;
import br.com.virabrequim.database_service.dto.SolicitacaoAmizade;
import br.com.virabrequim.database_service.service.AmizadeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class AmizadeController {

    private final AmizadeService amizadeService;

    public AmizadeController(AmizadeService amizadeService) {
        this.amizadeService = amizadeService;
    }

    // ============================ Solicitações ============================
    @PostMapping("/usuario/{usuarioId}/solicitacao-amizade")
    public ResponseEntity<AmizadeResposta> solicitar(@PathVariable Long usuarioId,
            @Valid @RequestBody NovaSolicitacaoAmizade solicitacao) {
        AmizadeResposta amizade = amizadeService.solicitar(usuarioId, solicitacao.destinatarioId());

        // Não há GET de uma solicitação ou amizade só: o Location aponta para a lista onde ela aparece
        String lista = switch (amizade.estado()) {
            case PENDENTE -> "/usuario/{usuarioId}/solicitacao-amizade/enviadas";
            case AMIGOS -> "/usuario/{usuarioId}/amigo";
        };
        URI local = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(WebConfig.PREFIXO_API + lista)
                .buildAndExpand(usuarioId)
                .toUri();
        return ResponseEntity.created(local).body(amizade);
    }

    @GetMapping("/usuario/{usuarioId}/solicitacao-amizade/recebidas")
    public List<SolicitacaoAmizade> listarRecebidas(@PathVariable Long usuarioId) {
        return amizadeService.listarRecebidas(usuarioId);
    }

    @GetMapping("/usuario/{usuarioId}/solicitacao-amizade/enviadas")
    public List<SolicitacaoAmizade> listarEnviadas(@PathVariable Long usuarioId) {
        return amizadeService.listarEnviadas(usuarioId);
    }

    @PostMapping("/usuario/{usuarioId}/solicitacao-amizade/{solicitanteId}/aceite")
    public AmizadeResposta aceitar(@PathVariable Long usuarioId, @PathVariable Long solicitanteId) {
        return amizadeService.aceitar(usuarioId, solicitanteId);
    }

    // Recusa (quem recebeu) ou cancela (quem enviou)
    @DeleteMapping("/usuario/{usuarioId}/solicitacao-amizade/{outroId}")
    public ResponseEntity<Void> apagarSolicitacao(@PathVariable Long usuarioId, @PathVariable Long outroId) {
        amizadeService.apagarSolicitacao(usuarioId, outroId);
        return ResponseEntity.noContent().build();
    }

    // ============================ Amigos ============================
    @GetMapping("/usuario/{usuarioId}/amigo")
    public List<Amigo> listarAmigos(@PathVariable Long usuarioId) {
        return amizadeService.listarAmigos(usuarioId);
    }

    @DeleteMapping("/usuario/{usuarioId}/amigo/{amigoId}")
    public ResponseEntity<Void> desfazer(@PathVariable Long usuarioId, @PathVariable Long amigoId) {
        amizadeService.desfazer(usuarioId, amigoId);
        return ResponseEntity.noContent().build();
    }
}
