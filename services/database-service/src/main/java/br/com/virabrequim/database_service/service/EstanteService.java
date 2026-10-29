package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.DestinoLivros;
import br.com.virabrequim.database_service.domain.node.EstanteNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.repository.EstanteRepository;
import br.com.virabrequim.database_service.repository.LivroRepository;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

@Service
public class EstanteService {

    private final UsuarioRepository usuarioRepository;
    private final EstanteRepository estanteRepository;
    private final LivroRepository livroRepository;

    public EstanteService(UsuarioRepository usuarioRepository, EstanteRepository estanteRepository,
            LivroRepository livroRepository) {
        this.usuarioRepository = usuarioRepository;
        this.estanteRepository = estanteRepository;
        this.livroRepository = livroRepository;
    }

    // As estantes vêm junto com o usuário (relacionamento HAS), incluindo a padrão
    public List<EstanteNode> listar(Long usuarioId) {
        return buscarUsuario(usuarioId).getEstantes();
    }

    public EstanteNode criar(Long usuarioId, String nome) {
        UsuarioNode usuario = buscarUsuario(usuarioId);
        EstanteNode novaEstante = new EstanteNode(nome);

        garantirNomeLivre(usuario, novaEstante.getNome(), null);

        usuario.getEstantes().add(novaEstante);
        usuarioRepository.save(usuario);
        // O SDN preenche o id gerado na própria instância salva, então ela já sai com id
        return novaEstante;
    }

    //Vale também para a estante padrão.
    public EstanteNode atualizar(Long usuarioId, Long estanteId, String nome) {
        UsuarioNode usuario = buscarUsuario(usuarioId);
        EstanteNode estante = buscarEstanteDoUsuario(usuario, estanteId);

        if (nome != null) {
            // A própria estante não conta como repetida: "Quarto" -> "quarto" é permitido.
            // Nome em branco passa aqui (não colide com nenhum) e é barrado pelo setNome.
            garantirNomeLivre(usuario, nome.strip(), estante);
            estante.setNome(nome);
        }

        usuarioRepository.save(usuario);
        return estante;
    }

    // Três escritas (livros, usuário e estante) numa transação só: se uma falhar, nada é gravado
    @Transactional
    public void apagar(Long usuarioId, Long estanteId, DestinoLivros destino) {
        UsuarioNode usuario = buscarUsuario(usuarioId);
        EstanteNode estante = buscarEstanteDoUsuario(usuario, estanteId);
        EstanteNode padrao = usuario.getEstantePadrao();

        if (Objects.equals(estante.getId(), padrao.getId())) {
            throw new IllegalStateException("A estante padrão não pode ser apagada.");
        }

        // MOVER: todos vão para a padrão. APAGAR: só os emprestados vão para a padrão (o dono continua existindo,
        // e o livro precisa de uma estante para voltar quando for devolvido); os outros são apagados.
        // TODO: Os emprestados vêm de uma query porque LivroNode.isEmprestado() ainda não está implementado.
        List<LivroNode> livros = livroRepository.findAllByEstanteId(estanteId);
        Set<Long> idsEmprestados = destino == DestinoLivros.APAGAR
                ? Set.copyOf(livroRepository.findIdsComEmprestimoAtivoByEstanteId(estanteId))
                : Set.of();

        List<LivroNode> livrosParaMover = new ArrayList<>();
        List<LivroNode> livrosParaApagar = new ArrayList<>();
        for (LivroNode livro : livros) {
            if (destino == DestinoLivros.MOVER || idsEmprestados.contains(livro.getId())) {
                livrosParaMover.add(livro);
            } else {
                livrosParaApagar.add(livro);
            }
        }

        // Os livros saem da estante ANTES dela ser apagada: o delete da estante remove os STORED_IN que sobrarem
        livrosParaMover.forEach(livro -> livro.setEstante(padrao));
        livroRepository.saveAll(livrosParaMover);
        livroRepository.deleteAll(livrosParaApagar);

        // Tirar da lista e salvar o usuário remove o HAS (o save sincroniza o grafo com o objeto)
        usuario.getEstantes().remove(estante);
        usuarioRepository.save(usuario);
        estanteRepository.delete(estante);
    }

    private UsuarioNode buscarUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario " + usuarioId + " não encontrado."));
    }

    private EstanteNode buscarEstanteDoUsuario(UsuarioNode usuario, Long estanteId) {
        return usuario.getEstantes().stream()
                .filter(estante -> Objects.equals(estante.getId(), estanteId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Estante " + estanteId + " não encontrada."));
    }

    private void garantirNomeLivre(UsuarioNode usuario, String nome, EstanteNode ignorar) {
        boolean repetido = usuario.getEstantes().stream()
                .filter(estante -> estante != ignorar)
                .anyMatch(estante -> estante.getNome().equalsIgnoreCase(nome));
        if (repetido) {
            throw new IllegalStateException("Já existe uma estante com o nome " + nome + ".");
        }
    }
}
