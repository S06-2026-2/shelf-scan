package br.com.virabrequim.database_service.controller;

import br.com.virabrequim.database_service.config.WebConfig;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;
import br.com.virabrequim.database_service.dto.AlertaEmprestimo;
import br.com.virabrequim.database_service.dto.Devolucao;
import br.com.virabrequim.database_service.dto.EmprestimoResposta;
import br.com.virabrequim.database_service.dto.NovoEmprestimo;
import br.com.virabrequim.database_service.service.EmprestimoService;
import br.com.virabrequim.database_service.service.LivroService;
import br.com.virabrequim.database_service.service.UsuarioService;
import br.com.virabrequim.database_service.tempo.Calendario;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
public class EmprestimoController {

    private final EmprestimoService emprestimoService;
    private final LivroService livroService;
    private final UsuarioService usuarioService;
    private final Calendario calendario;

    public EmprestimoController(EmprestimoService emprestimoService, LivroService livroService,
            UsuarioService usuarioService, Calendario calendario) {
        this.emprestimoService = emprestimoService;
        this.livroService = livroService;
        this.usuarioService = usuarioService;
        this.calendario = calendario;
    }

    @PostMapping("/usuario/{donoId}/livro/{livroId}/emprestimo")
    public ResponseEntity<EmprestimoResposta> emprestar(@PathVariable Long donoId, @PathVariable Long livroId,
            @Valid @RequestBody NovoEmprestimo novoEmprestimo) {
        EmprestimoRelationship emprestimo = emprestimoService.emprestar(
                donoId, livroId, novoEmprestimo.mutuarioId(), novoEmprestimo.dataPrevistaDevolucao());

        // Não há GET de um empréstimo só: o Location aponta para o histórico do livro
        URI local = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(WebConfig.PREFIXO_API + "/usuario/{donoId}/livro/{livroId}/emprestimo")
                .buildAndExpand(donoId, livroId)
                .toUri();

        return ResponseEntity.created(local).body(resposta(emprestimo, livroId));
    }

    // Ação com regra própria, por isso POST numa sub-rota e não PATCH no empréstimo. O corpo é opcional
    @PostMapping("/usuario/{donoId}/livro/{livroId}/emprestimo/devolucao")
    public EmprestimoResposta devolver(@PathVariable Long donoId, @PathVariable Long livroId,
            @RequestBody(required = false) Devolucao devolucao) {
        LocalDate dataDevolucao = devolucao == null ? null : devolucao.dataDevolucao();
        return resposta(emprestimoService.devolver(donoId, livroId, dataDevolucao), livroId);
    }

    @GetMapping("/usuario/{donoId}/livro/{livroId}/emprestimo")
    public List<EmprestimoResposta> historico(@PathVariable Long donoId, @PathVariable Long livroId) {
        List<EmprestimoRelationship> emprestimos = emprestimoService.historico(donoId, livroId);
        return EmprestimoResposta.de(emprestimos, livroService.buscar(livroId), calendario.diaHoje());
    }

    // Para o notification-service
    @GetMapping("/emprestimo/vencendo")
    public List<AlertaEmprestimo> listarVencendo() {
        return alertas(emprestimoService.listarVencendo());
    }

    @GetMapping("/emprestimo/vencidos")
    public List<AlertaEmprestimo> listarVencidos() {
        return alertas(emprestimoService.listarVencidos());
    }

    // O livro é buscado para a resposta ter o id e o total de páginas (a relação não conhece o livro)
    private EmprestimoResposta resposta(EmprestimoRelationship emprestimo, Long livroId) {
        return EmprestimoResposta.de(emprestimo, livroService.buscar(livroId), calendario.diaHoje());
    }

    private List<AlertaEmprestimo> alertas(List<LivroNode> livros) {
        LocalDate hoje = calendario.diaHoje();
        return livros.stream()
                .map(livro -> AlertaEmprestimo.de(livro, usuarioService.buscarDonoDoLivro(livro.getId()), hoje))
                .toList();
    }
}
