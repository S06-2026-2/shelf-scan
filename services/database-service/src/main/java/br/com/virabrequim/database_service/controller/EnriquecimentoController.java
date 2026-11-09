package br.com.virabrequim.database_service.controller;

import br.com.virabrequim.database_service.dto.EnriquecimentoResposta;
import br.com.virabrequim.database_service.dto.LivroResposta;
import br.com.virabrequim.database_service.dto.PedidoEnriquecimento;
import br.com.virabrequim.database_service.service.EnriquecimentoService;
import br.com.virabrequim.database_service.service.UsuarioService;
import br.com.virabrequim.database_service.tempo.Calendario;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Agente enriquecedor: completa os campos vazios do livro (sinopse, ISBN, páginas, autores, tags) com IA.
// POST porque é uma ação com efeito (chama o LLM e grava), não um PATCH com dados enviados pelo cliente.
@RestController
public class EnriquecimentoController {

    private final EnriquecimentoService enriquecimentoService;
    private final UsuarioService usuarioService;
    private final Calendario calendario;

    public EnriquecimentoController(EnriquecimentoService enriquecimentoService, UsuarioService usuarioService,
            Calendario calendario) {
        this.enriquecimentoService = enriquecimentoService;
        this.usuarioService = usuarioService;
        this.calendario = calendario;
    }

    @PostMapping("/usuario/{usuarioId}/livro/{livroId}/enriquecimento")
    public EnriquecimentoResposta enriquecer(@PathVariable Long usuarioId, @PathVariable Long livroId,
            @Valid @RequestBody(required = false) PedidoEnriquecimento pedido) {
        EnriquecimentoService.Resultado resultado = enriquecimentoService.enriquecer(usuarioId, livroId,
                pedido == null ? null : pedido.textoBruto());

        LivroResposta livro = LivroResposta.de(resultado.livro(),
                usuarioService.buscarDonoDoLivro(resultado.livro().getId()), calendario.diaHoje());
        return new EnriquecimentoResposta(livro, resultado.camposPreenchidos(), resultado.livroIdentificado(),
                resultado.modelo(), EnriquecimentoResposta.AVISO);
    }
}
