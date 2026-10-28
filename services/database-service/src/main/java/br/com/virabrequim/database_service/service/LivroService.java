package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.StatusLeitura;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.repository.LivroRepository;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import br.com.virabrequim.database_service.tempo.Calendario;
import org.springframework.stereotype.Service;

import java.util.List;

// Spec: docs/specs/database-service/LivroService.md
// Stubs do TDD: a implementação vem depois dos testes gerados pelo agente
@Service
public class LivroService {

    private final UsuarioRepository usuarioRepository;
    private final LivroRepository livroRepository;
    private final Calendario calendario;

    public LivroService(UsuarioRepository usuarioRepository, LivroRepository livroRepository, Calendario calendario) {
        this.usuarioRepository = usuarioRepository;
        this.livroRepository = livroRepository;
        this.calendario = calendario;
    }

    public LivroNode adicionar(Long usuarioId, LivroNode livro, Long estanteId) {
        //TODO: criar (L1–L6)
        throw new UnsupportedOperationException();
    }

    public LivroNode buscar(Long livroId) {
        //TODO: criar (L1)
        throw new UnsupportedOperationException();
    }

    public List<LivroNode> listarDaEstante(Long usuarioId, Long estanteId) {
        //TODO: criar (L1, L4)
        throw new UnsupportedOperationException();
    }

    public LivroNode mover(Long usuarioId, Long livroId, Long estanteDestinoId) {
        //TODO: criar (L1, L7, L8)
        throw new UnsupportedOperationException();
    }

    public void remover(Long usuarioId, Long livroId) {
        //TODO: criar (L1, L7, L9)
        throw new UnsupportedOperationException();
    }

    public LivroNode atualizarLeitura(Long usuarioId, Long livroId, StatusLeitura status, Integer paginaAtual) {
        //TODO: criar (L1, L10)
        throw new UnsupportedOperationException();
    }
}
