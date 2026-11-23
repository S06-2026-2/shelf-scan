package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.dto.BibliotecaDoUsuario;
import br.com.virabrequim.database_service.dto.Membro;
import br.com.virabrequim.database_service.repository.MembroRepository;
import br.com.virabrequim.database_service.tempo.Calendario;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

// Membro = cliente da biblioteca. Entrar é direto, sem aprovação
@Service
public class MembroService {

    private final MembroRepository membroRepository;
    private final UsuarioService usuarioService;
    private final BibliotecaService bibliotecaService;
    private final Calendario calendario;

    public MembroService(MembroRepository membroRepository, UsuarioService usuarioService,
            BibliotecaService bibliotecaService, Calendario calendario) {
        this.membroRepository = membroRepository;
        this.usuarioService = usuarioService;
        this.bibliotecaService = bibliotecaService;
        this.calendario = calendario;
    }

    // Repetir não é erro: quem já é membro continua membro desde a data original
    public void tornarMembro(Long usuarioId, Long bibliotecaId) {
        usuarioService.buscarUsuarioComum(usuarioId);
        bibliotecaService.buscar(bibliotecaId);
        membroRepository.adicionar(usuarioId, bibliotecaId, calendario.diaHoje());
    }

    // Empréstimos ativos da biblioteca para ele continuam: o vínculo só é exigido na hora de emprestar
    public void deixarDeSerMembro(Long usuarioId, Long bibliotecaId) {
        usuarioService.buscarUsuarioComum(usuarioId);
        if (!membroRepository.remover(usuarioId, bibliotecaId)) {
            throw new NoSuchElementException(
                    "O usuario " + usuarioId + " não é membro da biblioteca " + bibliotecaId + ".");
        }
    }

    public List<BibliotecaDoUsuario> listarBibliotecas(Long usuarioId) {
        usuarioService.buscarUsuarioComum(usuarioId);
        return membroRepository.listarBibliotecas(usuarioId);
    }

    public List<Membro> listarMembros(Long bibliotecaId) {
        bibliotecaService.buscar(bibliotecaId);
        return membroRepository.listarMembros(bibliotecaId);
    }
}
