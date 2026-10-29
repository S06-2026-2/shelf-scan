package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.node.BibliotecaNode;
import br.com.virabrequim.database_service.domain.node.CidadeNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.dto.AtualizacaoBiblioteca;
import br.com.virabrequim.database_service.dto.Endereco;
import br.com.virabrequim.database_service.repository.BibliotecaRepository;
import br.com.virabrequim.database_service.repository.CidadeRepository;
import br.com.virabrequim.database_service.repository.EstadoRepository;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class BibliotecaService {

    private final BibliotecaRepository bibliotecaRepository;
    // Só para a unicidade do email, que vale entre usuários comuns e bibliotecas
    private final UsuarioRepository usuarioRepository;
    private final CidadeRepository cidadeRepository;
    private final EstadoRepository estadoRepository;

    public BibliotecaService(BibliotecaRepository bibliotecaRepository, UsuarioRepository usuarioRepository,
            CidadeRepository cidadeRepository, EstadoRepository estadoRepository) {
        this.bibliotecaRepository = bibliotecaRepository;
        this.usuarioRepository = usuarioRepository;
        this.cidadeRepository = cidadeRepository;
        this.estadoRepository = estadoRepository;
    }

    // ============================ CRUD ============================
    public BibliotecaNode criar(BibliotecaNode novaBiblioteca) {
        garantirEmailLivre(novaBiblioteca.getEmail());

        novaBiblioteca.setCidade(cidadeDoBanco(novaBiblioteca.getCidade()));

        return bibliotecaRepository.save(novaBiblioteca);
    }

    // O BibliotecaRepository só acha nós com label Biblioteca: id de usuário comum dá 404
    public BibliotecaNode buscar(Long id) {
        return bibliotecaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Biblioteca " + id + " não encontrada."));
    }

    public BibliotecaNode atualizar(Long id, AtualizacaoBiblioteca dados) {
        BibliotecaNode biblioteca = buscar(id);

        if (dados.nome() != null) {
            biblioteca.setNome(dados.nome());
        }
        if (dados.email() != null) {
            String emailNormalizado = UsuarioNode.normalizarEmail(dados.email());
            if (!emailNormalizado.equals(biblioteca.getEmail())) {
                garantirEmailLivre(emailNormalizado);
            }
            biblioteca.setEmail(emailNormalizado);
        }
        if (dados.endereco() != null) {
            Endereco endereco = dados.endereco();
            biblioteca.setLogradouro(endereco.logradouro());
            biblioteca.setNumero(endereco.numero());
            biblioteca.setCep(endereco.cep());
            biblioteca.setCidade(cidadeDoBanco(endereco.paraCidade()));
        }
        if (dados.horarioAbertura() != null) {
            biblioteca.setHorarioAbertura(dados.horarioAbertura());
        }
        if (dados.horarioFechamento() != null) {
            biblioteca.setHorarioFechamento(dados.horarioFechamento());
        }

        return bibliotecaRepository.save(biblioteca);
    }

    // TODO (decisão de domínio): igual ao usuário comum, estantes e livros da biblioteca ficam órfãos
    public void apagar(Long id) {
        if (!bibliotecaRepository.existsById(id)) {
            throw new NoSuchElementException("Biblioteca " + id + " não encontrada.");
        }
        bibliotecaRepository.deleteById(id);
    }

    private CidadeNode cidadeDoBanco(CidadeNode cidadeInformada) {
        if (cidadeInformada == null) {
            return null;
        }
        return cidadeRepository.findById(cidadeInformada.getCodigoIbge())
                .orElseGet(() -> {
                    estadoRepository.findById(cidadeInformada.getEstado().getCodigoIbge())
                            .ifPresent(cidadeInformada::setEstado);
                    return cidadeInformada;
                });
    }

    private void garantirEmailLivre(String emailNormalizado) {
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new IllegalStateException("O email " + emailNormalizado + " já está em uso.");
        }
    }
}
