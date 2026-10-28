package br.com.virabrequim.database_service.controller;

import br.com.virabrequim.database_service.config.WebConfig;
import br.com.virabrequim.database_service.domain.node.LivroNode;
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
    public ResponseEntity<LivroResposta> adicionar(@PathVariable Long usuarioId,
            @Valid @RequestBody NovoLivro novoLivro) {
        LivroNode livro = livroService.adicionar(usuarioId, novoLivro.paraLivro(), novoLivro.estanteId());

        // TODO(design): a leitura inicial não está no LivroService.adicionar (spec), então vai numa segunda chamada.
        // Não é atômico: se a leitura for inválida, o livro já foi criado.
        Leitura leitura = novoLivro.leitura();
        if (leitura != null) {
            livro = livroService.atualizarLeitura(usuarioId, livro.getId(), leitura.status(), leitura.paginaAtual());
        }

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
