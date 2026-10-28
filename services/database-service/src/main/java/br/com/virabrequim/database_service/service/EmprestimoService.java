package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;
import br.com.virabrequim.database_service.repository.LivroRepository;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import br.com.virabrequim.database_service.tempo.Calendario;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

// Spec: docs/specs/database-service/EmprestimoService.md
// Stubs do TDD: a implementação vem depois dos testes gerados pelo agente
@Service
public class EmprestimoService {

    private final UsuarioRepository usuarioRepository;
    private final LivroRepository livroRepository;
    private final Calendario calendario;

    public EmprestimoService(UsuarioRepository usuarioRepository, LivroRepository livroRepository,
            Calendario calendario) {
        this.usuarioRepository = usuarioRepository;
        this.livroRepository = livroRepository;
        this.calendario = calendario;
    }

    public EmprestimoRelationship emprestar(Long donoId, Long livroId, Long mutuarioId,
            LocalDate dataPrevistaDevolucao) {
        //TODO: criar (M1–M5)
        throw new UnsupportedOperationException();
    }

    // dataDevolucao null = devolução hoje
    public EmprestimoRelationship devolver(Long donoId, Long livroId, LocalDate dataDevolucao) {
        //TODO: criar (M1, M6)
        throw new UnsupportedOperationException();
    }

    public List<EmprestimoRelationship> historico(Long donoId, Long livroId) {
        //TODO: criar (M1, M10)
        throw new UnsupportedOperationException();
    }

    public List<LivroNode> listarEmprestadosPara(Long usuarioId) {
        //TODO: criar (M7)
        throw new UnsupportedOperationException();
    }

    public List<LivroNode> listarVencendo() {
        //TODO: criar (M8, M9)
        throw new UnsupportedOperationException();
    }

    public List<LivroNode> listarVencidos() {
        //TODO: criar (M8, M9)
        throw new UnsupportedOperationException();
    }
}
