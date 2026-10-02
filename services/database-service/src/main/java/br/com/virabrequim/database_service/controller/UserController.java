package br.com.virabrequim.database_service.controller;

import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UsuarioService usuarioService;

    @PostMapping("/criar")
    public ResponseEntity<UsuarioNode> criarUsuario(@RequestBody UsuarioNode usuario){
        // @RequestBody converte automaticamente o JSON recebido no corpo da requisição para o objeto Usuario

        UsuarioNode novoUsuario = usuarioService.criarUsuario(usuario);

        return new ResponseEntity<>(novoUsuario, HttpStatus.CREATED);
    }

}
