package br.com.virabrequim.database_service.controller;

import br.com.virabrequim.database_service.config.WebConfig;
import br.com.virabrequim.database_service.domain.node.BibliotecaNode;
import br.com.virabrequim.database_service.dto.AtualizacaoBiblioteca;
import br.com.virabrequim.database_service.dto.BibliotecaResposta;
import br.com.virabrequim.database_service.dto.NovaBiblioteca;
import br.com.virabrequim.database_service.service.BibliotecaService;
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

@RestController
public class BibliotecaController {

    private final BibliotecaService bibliotecaService;

    public BibliotecaController(BibliotecaService bibliotecaService) {
        this.bibliotecaService = bibliotecaService;
    }

    // ============================ CRUD ============================
    @PostMapping("/biblioteca")
    public ResponseEntity<BibliotecaResposta> criar(@Valid @RequestBody NovaBiblioteca novaBiblioteca) {
        BibliotecaNode biblioteca = bibliotecaService.criar(novaBiblioteca.paraBiblioteca());

        URI local = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(WebConfig.PREFIXO_API + "/biblioteca/{id}")
                .buildAndExpand(biblioteca.getId())
                .toUri();
        return ResponseEntity.created(local).body(BibliotecaResposta.de(biblioteca));
    }

    @GetMapping("/biblioteca/{id}")
    public BibliotecaResposta buscar(@PathVariable Long id) {
        return BibliotecaResposta.de(bibliotecaService.buscar(id));
    }

    @PatchMapping("/biblioteca/{id}")
    public BibliotecaResposta atualizar(@PathVariable Long id, @Valid @RequestBody AtualizacaoBiblioteca dados) {
        return BibliotecaResposta.de(bibliotecaService.atualizar(id, dados));
    }

    @DeleteMapping("/biblioteca/{id}")
    public ResponseEntity<Void> apagar(@PathVariable Long id) {
        bibliotecaService.apagar(id);
        return ResponseEntity.noContent().build();
    }
}
