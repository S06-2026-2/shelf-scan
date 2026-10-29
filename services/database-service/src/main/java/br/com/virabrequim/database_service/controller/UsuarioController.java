package br.com.virabrequim.database_service.controller;

import br.com.virabrequim.database_service.config.WebConfig;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.dto.AtualizacaoUsuario;
import br.com.virabrequim.database_service.dto.NovoUsuario;
import br.com.virabrequim.database_service.dto.UsuarioResposta;
import br.com.virabrequim.database_service.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // ============================ CRUD ============================
    @PostMapping("/usuario")
    public ResponseEntity<UsuarioResposta> criar(@Valid @RequestBody NovoUsuario novoUsuario) {
        UsuarioNode usuario = usuarioService.criar(
                novoUsuario.nome(),
                novoUsuario.email()
        );

        URI local = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(WebConfig.PREFIXO_API + "/usuario/{id}")
                .buildAndExpand(usuario.getId())
                .toUri();
        return ResponseEntity.created(local).body(UsuarioResposta.de(usuario));
    }

    @GetMapping("/usuario/{id}")
    public UsuarioResposta buscar(@PathVariable Long id) {
        return UsuarioResposta.de(usuarioService.buscarUsuarioComum(id));
    }

    @PatchMapping("/usuario/{id}")
    public UsuarioResposta atualizar(@PathVariable Long id, @Valid @RequestBody AtualizacaoUsuario dados) {
        return UsuarioResposta.de(usuarioService.atualizar(id, dados.nome(), dados.email()));
    }

    @DeleteMapping("/usuario/{id}")
    public ResponseEntity<Void> apagar(@PathVariable Long id) {
        usuarioService.apagar(id);
        return ResponseEntity.noContent().build();
    }
}
