package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.EstadoAmizade;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.dto.Amigo;
import br.com.virabrequim.database_service.dto.AmizadeResposta;
import br.com.virabrequim.database_service.dto.Referencia;
import br.com.virabrequim.database_service.dto.SolicitacaoAmizade;
import br.com.virabrequim.database_service.repository.AmizadeRepository;
import br.com.virabrequim.database_service.tempo.Calendario;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

// Amizade só existe entre usuários comuns: biblioteca tem membros (ver MembroService)
@Service
public class AmizadeService {

    private final AmizadeRepository amizadeRepository;
    private final UsuarioService usuarioService;
    private final Calendario calendario;

    public AmizadeService(AmizadeRepository amizadeRepository, UsuarioService usuarioService, Calendario calendario) {
        this.amizadeRepository = amizadeRepository;
        this.usuarioService = usuarioService;
        this.calendario = calendario;
    }

    // ============================ Solicitações ============================
    public AmizadeResposta solicitar(Long usuarioId, Long destinatarioId) {
        usuarioService.buscarUsuarioComum(usuarioId);
        UsuarioNode destinatario = usuarioService.buscarUsuarioComum(destinatarioId);
        if (usuarioId.equals(destinatarioId)) {
            throw new IllegalArgumentException("Não é possível pedir amizade para si mesmo.");
        }
        if (amizadeRepository.saoAmigos(usuarioId, destinatarioId)) {
            throw new IllegalStateException("Vocês já são amigos.");
        }
        if (amizadeRepository.existeSolicitacao(usuarioId, destinatarioId)) {
            throw new IllegalStateException("A solicitação de amizade já foi enviada.");
        }

        // A outra pessoa já tinha pedido: os dois querem, então vira amizade na hora
        if (amizadeRepository.trocarSolicitacaoPorAmizade(usuarioId, destinatarioId, calendario.diaHoje())) {
            return new AmizadeResposta(EstadoAmizade.AMIGOS, Referencia.de(destinatario));
        }
        amizadeRepository.criarSolicitacao(usuarioId, destinatarioId, calendario.diaHoje());
        return new AmizadeResposta(EstadoAmizade.PENDENTE, Referencia.de(destinatario));
    }

    // Só quem recebeu o pedido aceita
    public AmizadeResposta aceitar(Long usuarioId, Long solicitanteId) {
        usuarioService.buscarUsuarioComum(usuarioId);
        UsuarioNode solicitante = usuarioService.buscarUsuarioComum(solicitanteId);
        if (!amizadeRepository.existeSolicitacao(solicitanteId, usuarioId)) {
            throw new NoSuchElementException("Não há solicitação de amizade do usuario " + solicitanteId + ".");
        }

        amizadeRepository.trocarSolicitacaoPorAmizade(usuarioId, solicitanteId, calendario.diaHoje());
        return new AmizadeResposta(EstadoAmizade.AMIGOS, Referencia.de(solicitante));
    }

    // Recusar (quem recebeu) ou cancelar (quem enviou)
    public void apagarSolicitacao(Long usuarioId, Long outroId) {
        usuarioService.buscarUsuarioComum(usuarioId);
        if (!amizadeRepository.apagarSolicitacao(usuarioId, outroId)) {
            throw new NoSuchElementException("Não há solicitação de amizade com o usuario " + outroId + ".");
        }
    }

    public List<SolicitacaoAmizade> listarRecebidas(Long usuarioId) {
        usuarioService.buscarUsuarioComum(usuarioId);
        return amizadeRepository.listarRecebidas(usuarioId);
    }

    public List<SolicitacaoAmizade> listarEnviadas(Long usuarioId) {
        usuarioService.buscarUsuarioComum(usuarioId);
        return amizadeRepository.listarEnviadas(usuarioId);
    }

    // ============================ Amigos ============================
    public List<Amigo> listarAmigos(Long usuarioId) {
        usuarioService.buscarUsuarioComum(usuarioId);
        return amizadeRepository.listarAmigos(usuarioId);
    }

    // Empréstimos ativos entre os dois continuam: o vínculo só é exigido na hora de emprestar
    public void desfazer(Long usuarioId, Long amigoId) {
        usuarioService.buscarUsuarioComum(usuarioId);
        if (!amizadeRepository.apagarAmizade(usuarioId, amigoId)) {
            throw new NoSuchElementException("O usuario " + amigoId + " não é seu amigo.");
        }
    }
}
