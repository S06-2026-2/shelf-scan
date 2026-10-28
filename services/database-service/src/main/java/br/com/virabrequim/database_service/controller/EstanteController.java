package br.com.virabrequim.database_service.controller;

import br.com.virabrequim.database_service.config.WebConfig;
import br.com.virabrequim.database_service.domain.node.EstanteNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.dto.EstanteResposta;
import br.com.virabrequim.database_service.dto.LivroResumo;
import br.com.virabrequim.database_service.dto.NomeEstante;
import br.com.virabrequim.database_service.service.EmprestimoService;
import br.com.virabrequim.database_service.service.EstanteService;
import br.com.virabrequim.database_service.service.LivroService;
import br.com.virabrequim.database_service.service.UsuarioService;
import br.com.virabrequim.database_service.tempo.Calendario;
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
import java.util.ArrayList;
import java.util.List;

@RestController
public class EstanteController {

    private final EstanteService estanteService;
    private final UsuarioService usuarioService;
    private final LivroService livroService;
    private final EmprestimoService emprestimoService;
    private final Calendario calendario;

    public EstanteController(EstanteService estanteService, UsuarioService usuarioService, LivroService livroService,
            EmprestimoService emprestimoService, Calendario calendario) {
        this.estanteService = estanteService;
        this.usuarioService = usuarioService;
        this.livroService = livroService;
        this.emprestimoService = emprestimoService;
        this.calendario = calendario;
    }

    // Estantes reais e, por último, a virtual "Emprestados comigo"
    @GetMapping("/usuario/{usuarioId}/estante")
    public List<EstanteResposta> listar(@PathVariable Long usuarioId) {
        UsuarioNode usuario = usuarioService.buscar(usuarioId);

        List<EstanteResposta> estantes = new ArrayList<>();
        for (EstanteNode estante : estanteService.listar(usuarioId)) {
            estantes.add(EstanteResposta.de(estante, usuario));
        }
        estantes.add(EstanteResposta.emprestadosComigo());
        return estantes;
    }

    @PostMapping("/usuario/{usuarioId}/estante")
    public ResponseEntity<EstanteResposta> criar(@PathVariable Long usuarioId,
            @Valid @RequestBody NomeEstante nomeEstante) {
        EstanteNode estante = estanteService.criar(usuarioId, nomeEstante.nome());

        // Não há GET de uma estante só: o Location aponta para os livros dela
        URI local = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(WebConfig.PREFIXO_API + "/usuario/{usuarioId}/estante/{estanteId}/livro")
                .buildAndExpand(usuarioId, estante.getId())
                .toUri();

        return ResponseEntity.created(local).body(resposta(usuarioId, estante));
    }

    @PatchMapping("/usuario/{usuarioId}/estante/{estanteId}")
    public EstanteResposta renomear(@PathVariable Long usuarioId, @PathVariable Long estanteId,
            @Valid @RequestBody NomeEstante nomeEstante) {
        EstanteNode estante = estanteService.renomear(usuarioId, estanteId, nomeEstante.nome());
        return resposta(usuarioId, estante);
    }

    // Os livros da estante apagada vão para a padrão (regra do service)
    @DeleteMapping("/usuario/{usuarioId}/estante/{estanteId}")
    public ResponseEntity<Void> apagar(@PathVariable Long usuarioId, @PathVariable Long estanteId) {
        estanteService.apagar(usuarioId, estanteId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/usuario/{usuarioId}/estante/{estanteId}/livro")
    public List<LivroResumo> listarLivros(@PathVariable Long usuarioId, @PathVariable Long estanteId) {
        return LivroResumo.doDono(livroService.listarDaEstante(usuarioId, estanteId), calendario.diaHoje());
    }

    // Conteúdo da estante virtual: livros com empréstimo ativo para o usuário
    @GetMapping("/usuario/{usuarioId}/emprestados-comigo")
    public List<LivroResumo> listarEmprestadosComigo(@PathVariable Long usuarioId) {
        return LivroResumo.doMutuario(emprestimoService.listarEmprestadosPara(usuarioId), calendario.diaHoje());
    }

    // O usuário é buscado de novo para saber qual é a estante padrão
    private EstanteResposta resposta(Long usuarioId, EstanteNode estante) {
        return EstanteResposta.de(estante, usuarioService.buscar(usuarioId));
    }
}
