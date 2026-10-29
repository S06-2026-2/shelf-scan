package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.StatusLeitura;
import br.com.virabrequim.database_service.domain.Textos;
import br.com.virabrequim.database_service.domain.node.AutorNode;
import br.com.virabrequim.database_service.domain.node.EstanteNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.node.TagNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;
import br.com.virabrequim.database_service.dto.AtualizacaoLivro;
import br.com.virabrequim.database_service.repository.AutorRepository;
import br.com.virabrequim.database_service.repository.LivroRepository;
import br.com.virabrequim.database_service.repository.TagRepository;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import br.com.virabrequim.database_service.tempo.Calendario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

@Service
public class LivroService {

    private final UsuarioRepository usuarioRepository;
    private final LivroRepository livroRepository;
    private final AutorRepository autorRepository;
    private final TagRepository tagRepository;
    private final Calendario calendario;

    public LivroService(UsuarioRepository usuarioRepository, LivroRepository livroRepository,
            AutorRepository autorRepository, TagRepository tagRepository, Calendario calendario) {
        this.usuarioRepository = usuarioRepository;
        this.livroRepository = livroRepository;
        this.autorRepository = autorRepository;
        this.tagRepository = tagRepository;
        this.calendario = calendario;
    }

    // ============================ CRUD ============================
    // A leitura inicial (opcional, status null = sem leitura) é aplicada antes do save: é uma operação só, e com
    // leitura inválida nada é criado. Em LENDO, página inválida vira 0 (regra do LivroNode).
    public LivroNode adicionar(Long usuarioId, LivroNode livro, Long estanteId, StatusLeitura status,
            Integer paginaAtual) {
        UsuarioNode usuario = buscarUsuario(usuarioId);
        if (livro == null) {
            throw new IllegalArgumentException("O livro é obrigatório.");
        }
        // Revalida título e páginas: o livro pode ter sido montado com o construtor vazio
        livro.setTitulo(livro.getTitulo());
        livro.setPaginas(livro.getPaginas());

        EstanteNode estante = estanteId == null
                ? usuario.getEstantePadrao()
                : buscarEstanteDoUsuario(usuario, estanteId);

        // O livro adicionado é sempre novo
        livro.setId(null);
        livro.setEmprestimos(new ArrayList<>());
        livro.setDataDeAdicao(ZonedDateTime.now(calendario.getClock()));
        livro.setEstante(estante);
        livro.setAutores(autoresDoBanco(livro.getAutores()));
        livro.setTags(tagsDoBanco(livro.getTags()));

        if (status != null) {
            livro.atualizarLeitura(status, paginaAtual, calendario.diaHoje());
        }

        return livroRepository.save(livro);
    }

    public LivroNode buscar(Long livroId) {
        return livroRepository.findById(livroId)
                .orElseThrow(() -> new NoSuchElementException("Livro " + livroId + " não encontrado."));
    }

    public List<LivroNode> listarDaEstante(Long usuarioId, Long estanteId) {
        UsuarioNode usuario = buscarUsuario(usuarioId);
        buscarEstanteDoUsuario(usuario, estanteId);
        return livroRepository.findAllByEstanteId(estanteId);
    }

    // PATCH: campo null = não mexer. Autores e tags substituem a lista inteira; o save do livro sincroniza os
    // relacionamentos (cria os novos WRITTEN_BY/HAS_TAG e apaga os que saíram), e no fim os autores e tags que
    // ficaram sem nenhum livro são apagados.
    @Transactional
    public LivroNode atualizar(Long usuarioId, Long livroId, AtualizacaoLivro dados) {
        LivroNode livro = buscarLivroDoUsuario(usuarioId, livroId);
        List<AutorNode> autoresAntes = List.copyOf(livro.getAutores());
        Set<TagNode> tagsAntes = Set.copyOf(livro.getTags());

        if (dados.titulo() != null) {
            livro.setTitulo(dados.titulo());
        }
        if (dados.isbn() != null) {
            livro.setIsbn(Textos.opcional(dados.isbn()));
        }
        if (dados.volume() != null) {
            livro.setVolume(Textos.opcional(dados.volume()));
        }
        if (dados.sinopse() != null) {
            livro.setSinopse(Textos.opcional(dados.sinopse()));
        }
        if (dados.paginas() != null) {
            // 0 remove o total; diminuir abaixo da página lida (LENDO) é conflito; LIDO acompanha (regra do LivroNode)
            livro.setPaginas(dados.paginas());
        }
        if (dados.autores() != null) {
            livro.setAutores(autoresDoBanco(dados.autores().stream().map(AutorNode::new).toList()));
        }
        if (dados.tags() != null) {
            livro.setTags(tagsDoBanco(dados.tags().stream().map(TagNode::new).toList()));
        }

        LivroNode salvo = livroRepository.save(livro);
        apagarOrfaos(autoresAntes, tagsAntes);
        return salvo;
    }

    public LivroNode mover(Long usuarioId, Long livroId, Long estanteDestinoId) {
        LivroNode livro = buscarLivroDoUsuario(usuarioId, livroId);
        EstanteNode destino = buscarEstanteDoUsuario(buscarUsuario(usuarioId), estanteDestinoId);

        livro.setEstante(destino);
        return livroRepository.save(livro);
    }

    // TODO (PERDA DE DADOS): apagar o livro apaga junto TODO o histórico de empréstimos dele (LENT_TO devolvidos) e as
    //  leituras de quem pegou emprestado, que ficam guardadas nesses empréstimos.
    @Transactional
    public void remover(Long usuarioId, Long livroId) {
        LivroNode livro = buscarLivroDoUsuario(usuarioId, livroId);
        if (livro.isEmprestado()) {
            throw new IllegalStateException("O livro está emprestado e não pode ser removido.");
        }
        List<AutorNode> autores = List.copyOf(livro.getAutores());
        Set<TagNode> tags = Set.copyOf(livro.getTags());

        livroRepository.delete(livro);
        apagarOrfaos(autores, tags);
    }

    // ============================ Leitura ============================
    // A leitura é de quem chama: do dono (guardada no livro) ou de quem está com o empréstimo ativo (guardada no
    // empréstimo). Em LENDO, página inválida vira 0.
    public LivroNode atualizarLeitura(Long usuarioId, Long livroId, StatusLeitura status, Integer paginaAtual) {
        buscarUsuario(usuarioId);
        LivroNode livro = buscar(livroId);

        if (ehDono(usuarioId, livroId)) {
            livro.atualizarLeitura(status, paginaAtual, calendario.diaHoje());
        } else {
            EmprestimoRelationship emprestimo = livro.emprestimoAtivo()
                    .filter(ativo -> Objects.equals(ativo.getUsuario().getId(), usuarioId))
                    .orElseThrow(() -> new NoSuchElementException("Livro " + livroId + " não encontrado."));
            emprestimo.atualizarLeitura(status, paginaAtual, livro.getPaginas(), calendario.diaHoje());
        }

        return livroRepository.save(livro);
    }

    // ============================ Auxiliares ============================
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

    private boolean ehDono(Long usuarioId, Long livroId) {
        return usuarioRepository.findDonoDoLivro(livroId)
                .map(dono -> Objects.equals(dono.getId(), usuarioId))
                .orElse(false);
    }

    private LivroNode buscarLivroDoUsuario(Long usuarioId, Long livroId) {
        buscarUsuario(usuarioId);
        LivroNode livro = buscar(livroId);
        if (!ehDono(usuarioId, livroId)) {
            throw new NoSuchElementException("Livro " + livroId + " não encontrado.");
        }
        return livro;
    }

    // Find-or-create dos autores pelo nome (sem diferenciar maiúsculas; o AutorNode já normalizou os espaços).
    // Nomes repetidos na mesma lista viram um autor só. O autor do banco mantém o nome do primeiro cadastro.
    private List<AutorNode> autoresDoBanco(Collection<AutorNode> informados) {
        if (informados == null) {
            return new ArrayList<>();
        }
        Map<String, AutorNode> porNome = new LinkedHashMap<>();
        for (AutorNode autor : informados) {
            porNome.putIfAbsent(autor.getNome().toLowerCase(Locale.ROOT), autor);
        }
        List<AutorNode> autores = new ArrayList<>();
        for (AutorNode autor : porNome.values()) {
            autores.add(autorRepository.findFirstByNomeIgnoreCase(autor.getNome()).orElse(autor));
        }
        return autores;
    }

    // Find-or-create das tags pelo nome normalizado (@Id). Usar a tag do banco evita que o save sobrescreva o
    // nomeExibicao do primeiro cadastro.
    private Set<TagNode> tagsDoBanco(Collection<TagNode> informadas) {
        Set<TagNode> tags = new HashSet<>();
        if (informadas == null) {
            return tags;
        }
        Map<String, TagNode> porNome = new LinkedHashMap<>();
        for (TagNode tag : informadas) {
            porNome.putIfAbsent(tag.getNome(), tag);
        }
        for (TagNode tag : porNome.values()) {
            tags.add(tagRepository.findById(tag.getNome()).orElse(tag));
        }
        return tags;
    }

    // Apaga, entre os autores e tags que estavam ligados ao livro, os que ficaram sem nenhum livro
    private void apagarOrfaos(Collection<AutorNode> autores, Collection<TagNode> tags) {
        List<Long> idsAutores = autores.stream().map(AutorNode::getId).filter(Objects::nonNull).toList();
        List<String> nomesTags = tags.stream().map(TagNode::getNome).toList();
        if (!idsAutores.isEmpty()) {
            autorRepository.apagarOrfaos(idsAutores);
        }
        if (!nomesTags.isEmpty()) {
            tagRepository.apagarOrfas(nomesTags);
        }
    }
}
