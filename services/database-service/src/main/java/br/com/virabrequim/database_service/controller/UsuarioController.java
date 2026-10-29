package br.com.virabrequim.database_service.controller;

import br.com.virabrequim.database_service.config.WebConfig;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.dto.NovoUsuario;
import br.com.virabrequim.database_service.dto.UsuarioResposta;
import br.com.virabrequim.database_service.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    @PostMapping("/usuario")
    public ResponseEntity<UsuarioResposta> criar(@Valid @RequestBody NovoUsuario novoUsuario) {
        UsuarioNode usuario = usuarioService.criar(
                novoUsuario.nome(),
                novoUsuario.email()
        );
        return criado(usuario);
    }

    @PostMapping("/biblioteca")
    public ResponseEntity<UsuarioResposta> criarBiblioteca(@Valid @RequestBody NovoUsuario novaBiblioteca) {
        UsuarioNode biblioteca = usuarioService.criarBiblioteca(
                novaBiblioteca.nome(),
                novaBiblioteca.email()
        );
        return criado(biblioteca);
    }

    @GetMapping("/usuario/{id}")
    public UsuarioResposta buscar(@PathVariable Long id) {
        return UsuarioResposta.de(usuarioService.buscar(id));
    }

    // 201 com o header Location apontando para GET /usuario/{id} (vale também para a biblioteca, que é um usuário)
    private ResponseEntity<UsuarioResposta> criado(UsuarioNode usuario) {
        URI local = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(WebConfig.PREFIXO_API + "/usuario/{id}")
                .buildAndExpand(usuario.getId())
                .toUri();

        return ResponseEntity.created(local).body(UsuarioResposta.de(usuario));
    }
}
