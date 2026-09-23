package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    public UsuarioNode criarUsuario(UsuarioNode usuario){
        return usuarioRepository.save(usuario);
    }
}
