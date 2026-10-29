package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.node.BibliotecaNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    // ============================ CRUD ============================
    public UsuarioNode criar(String nome, String email) {
        UsuarioNode novoUsuario = new UsuarioNode(nome, email);

        garantirEmailLivre(novoUsuario.getEmail());

        return usuarioRepository.save(novoUsuario);
    }

    // Qualquer usuário, comum ou biblioteca. Mudança: continua aceitando bibliotecas de propósito, porque é usado
    // pelas rotas de estante/livro/empréstimo, que valem para os dois tipos (a separação fica só no CRUD).
    public UsuarioNode buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario " + id + " não encontrado."));
    }

    public UsuarioNode buscarUsuarioComum(Long id) {
        UsuarioNode usuario = buscar(id);
        if (usuario instanceof BibliotecaNode) {
            throw new NoSuchElementException("O id " + id + " é de uma biblioteca. Use /biblioteca/" + id + ".");
        }
        return usuario;
    }

    public UsuarioNode atualizar(Long id, String nome, String email) {
        UsuarioNode usuario = buscarUsuarioComum(id);

        if (nome != null) {
            usuario.setNome(nome);
        }
        if (email != null) {
            String emailNormalizado = UsuarioNode.normalizarEmail(email);
            if (!emailNormalizado.equals(usuario.getEmail())) {
                garantirEmailLivre(emailNormalizado);
            }
            usuario.setEmail(emailNormalizado);
        }

        return usuarioRepository.save(usuario);
    }

    // TODO (decisão de domínio): livro emprestado de um usuário apagado fica sem dono.
    public void apagar(Long id) {
        buscarUsuarioComum(id);
        usuarioRepository.apagarComEstantesELivros(id);
    }

    public UsuarioNode buscarDonoDoLivro(Long livroId) {
        return usuarioRepository.findDonoDoLivro(livroId)
                .orElseThrow(() -> new NoSuchElementException("Livro " + livroId + " não encontrado."));
    }

    private void garantirEmailLivre(String emailNormalizado) {
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new IllegalStateException("O email " + emailNormalizado + " já está em uso.");
        }
    }
}
