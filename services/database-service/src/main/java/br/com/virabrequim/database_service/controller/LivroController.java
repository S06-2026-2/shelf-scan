package br.com.virabrequim.database_service.controller;

import br.com.virabrequim.database_service.config.WebConfig;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.dto.AtualizacaoLivro;
import br.com.virabrequim.database_service.dto.Leitura;
import br.com.virabrequim.database_service.dto.LivroResposta;
import br.com.virabrequim.database_service.dto.MoverLivro;
import br.com.virabrequim.database_service.dto.NovoLivro;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
public class LivroController {

    private final LivroService livroService;
    private final UsuarioService usuarioService;
    private final Calendario calendario;

    public LivroController(LivroService livroService, UsuarioService usuarioService, Calendario calendario) {
        this.livroService = livroService;
        this.usuarioService = usuarioService;
        this.calendario = calendario;
    }

    @PostMapping("/usuario/{usuarioId}/livro")
    public ResponseEntity<LivroResposta> adicionar(
            @PathVariable Long usuarioId,
            @Valid @RequestBody NovoLivro novoLivro
    ) {
        // A leitura inicial é opcional e vai junto no adicionar (uma operação só)
        Leitura leitura = novoLivro.leitura();
        LivroNode livro = livroService.adicionar(usuarioId, novoLivro.paraLivro(), novoLivro.estanteId(),
                leitura == null ? null : leitura.status(),
                leitura == null ? null : leitura.paginaAtual());

        URI local = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(WebConfig.PREFIXO_API + "/livro/{livroId}")
                .buildAndExpand(livro.getId())
                .toUri();

        return ResponseEntity.created(local).body(resposta(livro));
    }

    @GetMapping("/livro/{livroId}")
    public LivroResposta buscar(@PathVariable Long livroId) {
        return resposta(livroService.buscar(livroId));
    }

    // PATCH genérico dos dados do livro (só o dono). Estante e leitura têm rotas próprias
    @PatchMapping("/usuario/{usuarioId}/livro/{livroId}")
    public LivroResposta atualizar(@PathVariable Long usuarioId, @PathVariable Long livroId,
            @Valid @RequestBody AtualizacaoLivro dados) {
        return resposta(livroService.atualizar(usuarioId, livroId, dados));
    }

    @PatchMapping("/usuario/{usuarioId}/livro/{livroId}/estante")
    public LivroResposta mover(@PathVariable Long usuarioId, @PathVariable Long livroId,
            @Valid @RequestBody MoverLivro moverLivro) {
        return resposta(livroService.mover(usuarioId, livroId, moverLivro.estanteId()));
    }

    // Atualiza a leitura de quem chama: do dono (no livro) ou de quem está com ele (no empréstimo)
    @PutMapping("/usuario/{usuarioId}/livro/{livroId}/leitura")
    public LivroResposta atualizarLeitura(@PathVariable Long usuarioId, @PathVariable Long livroId,
            @Valid @RequestBody Leitura leitura) {
        return resposta(livroService.atualizarLeitura(usuarioId, livroId, leitura.status(), leitura.paginaAtual()));
    }

    @DeleteMapping("/usuario/{usuarioId}/livro/{livroId}")
    public ResponseEntity<Void> remover(@PathVariable Long usuarioId, @PathVariable Long livroId) {
        livroService.remover(usuarioId, livroId);
        return ResponseEntity.noContent().build();
    }

    private LivroResposta resposta(LivroNode livro) {
        return LivroResposta.de(livro, usuarioService.buscarDonoDoLivro(livro.getId()), calendario.diaHoje());
    }
}
