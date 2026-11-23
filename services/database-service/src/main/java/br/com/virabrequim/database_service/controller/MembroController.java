package br.com.virabrequim.database_service.controller;

import br.com.virabrequim.database_service.dto.BibliotecaDoUsuario;
import br.com.virabrequim.database_service.dto.Membro;
import br.com.virabrequim.database_service.service.MembroService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MembroController {

    private final MembroService membroService;

    public MembroController(MembroService membroService) {
        this.membroService = membroService;
    }

    // PUT e não POST: repetir a chamada dá o mesmo resultado (idempotente)
    @PutMapping("/usuario/{usuarioId}/biblioteca/{bibliotecaId}")
    public ResponseEntity<Void> tornarMembro(@PathVariable Long usuarioId, @PathVariable Long bibliotecaId) {
        membroService.tornarMembro(usuarioId, bibliotecaId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/usuario/{usuarioId}/biblioteca/{bibliotecaId}")
    public ResponseEntity<Void> deixarDeSerMembro(@PathVariable Long usuarioId, @PathVariable Long bibliotecaId) {
        membroService.deixarDeSerMembro(usuarioId, bibliotecaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/usuario/{usuarioId}/biblioteca")
    public List<BibliotecaDoUsuario> listarBibliotecas(@PathVariable Long usuarioId) {
        return membroService.listarBibliotecas(usuarioId);
    }

    @GetMapping("/biblioteca/{bibliotecaId}/membro")
    public List<Membro> listarMembros(@PathVariable Long bibliotecaId) {
        return membroService.listarMembros(bibliotecaId);
    }
}
