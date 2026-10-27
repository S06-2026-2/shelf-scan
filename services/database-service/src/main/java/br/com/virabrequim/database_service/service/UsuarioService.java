package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.node.BibliotecaNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

// Spec: docs/specs/database-service/UsuarioService.md
// Stubs do TDD: a implementação vem depois dos testes gerados pelo agente
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public UsuarioNode criar(String nome, String email) {
        //TODO: criar
        throw new UnsupportedOperationException();
    }

    public BibliotecaNode criarBiblioteca(String nome, String email) {
        //TODO: criar
        throw new UnsupportedOperationException();
    }

    public UsuarioNode buscar(Long id) {
        //TODO: criar
        throw new UnsupportedOperationException();
    }
}
