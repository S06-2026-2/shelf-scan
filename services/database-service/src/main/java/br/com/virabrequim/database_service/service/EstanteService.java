package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.node.EstanteNode;
import br.com.virabrequim.database_service.repository.EstanteRepository;
import br.com.virabrequim.database_service.repository.LivroRepository;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// Spec: docs/specs/database-service/EstanteService.md
// Stubs do TDD: a implementação vem depois dos testes gerados pelo agente
@Service
public class EstanteService {

    private final UsuarioRepository usuarioRepository;
    private final EstanteRepository estanteRepository;
    private final LivroRepository livroRepository;

    public EstanteService(UsuarioRepository usuarioRepository, EstanteRepository estanteRepository,
            LivroRepository livroRepository) {
        this.usuarioRepository = usuarioRepository;
        this.estanteRepository = estanteRepository;
        this.livroRepository = livroRepository;
    }

    public List<EstanteNode> listar(Long usuarioId) {
        //TODO: criar (S1, S2)
        throw new UnsupportedOperationException();
    }

    public EstanteNode criar(Long usuarioId, String nome) {
        //TODO: criar (S1, S3–S5)
        throw new UnsupportedOperationException();
    }

    public EstanteNode renomear(Long usuarioId, Long estanteId, String novoNome) {
        //TODO: criar (S1, S3, S4, S6, S7)
        throw new UnsupportedOperationException();
    }

    public void apagar(Long usuarioId, Long estanteId) {
        //TODO: criar (S1, S6, S8–S10)
        throw new UnsupportedOperationException();
    }
}
