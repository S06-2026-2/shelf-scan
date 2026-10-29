package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.node.BibliotecaNode;
import br.com.virabrequim.database_service.domain.node.EstanteNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

// Spec: docs/specs/database-service/UsuarioService.md
// Stubs do TDD: a implementação vem depois dos testes gerados pelo agente
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    // ============================ CRUD ============================
    public UsuarioNode criar(String nome, String email) {
        EstanteNode estantePadraoInicial = new EstanteNode("Estante Padrao");

        UsuarioNode novoUsuario = new UsuarioNode(nome, email);

        novoUsuario.setEstantePadrao(estantePadraoInicial);

        return usuarioRepository.save(novoUsuario);
    }

    public BibliotecaNode criarBiblioteca(String nome, String email) {
        EstanteNode estantePadraoInicial = new EstanteNode("Livros sem Estante Demarcada");

        BibliotecaNode novaBiblioteca = new BibliotecaNode(nome, email);

        novaBiblioteca.setEstantePadrao(estantePadraoInicial);

        return usuarioRepository.save(novaBiblioteca);
    }

    public UsuarioNode buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario " + id + " não encontrado."));
        //TODO: tratamento para poder ser só usuario padrão
    }

    public UsuarioNode buscarBiblioteca(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario " + id + " não encontrado."));
        //TODO: tratamento para oder ser só usuario biblioteca
    }

    public void atualizar(Long id, UsuarioNode usuarioAtualizado){
        UsuarioNode usuarioAtual = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario " + id + " não encontrado."));
        //TODO: fazer uma atualização por campo?
        //TODO: criar atualização no banco
    }

    public void atualizarBiblioteca(Long id, BibliotecaNode usuarioAtualizado){
//        BibliotecaNode usuarioAtual = usuarioRepository.findById(id)
//                .orElseThrow(() -> new NoSuchElementException("Usuario " + id + " não encontrado."));

        //TODO: TROCAR PARA INSTANCIAR UMA BIBLIOTECA, PERGUNTAR SE NAO DA PROBLEMA O usuarioRepository voltar um UsuarioNode Padrao
        //TODO: criar atualização no banco
    }

    public void apagar(Long id){
        if(!usuarioRepository.existsById(id)){
            throw new NoSuchElementException("Usuario " + id + " não encontrado.");
        }
        usuarioRepository.deleteById(id);
    }

    public UsuarioNode buscarDonoDoLivro(Long livroId) {
        UsuarioNode usuarioDono = usuarioRepository.findDonoDoLivro(livroId)
                .orElseThrow(() -> new NoSuchElementException("Livro " + livroId + " não encontrado."));
        return usuarioDono;
    }
}
