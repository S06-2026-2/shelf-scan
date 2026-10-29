package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;
import br.com.virabrequim.database_service.repository.LivroRepository;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import br.com.virabrequim.database_service.tempo.Calendario;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiPredicate;

@Service
public class EmprestimoService {

    private final UsuarioRepository usuarioRepository;
    private final LivroRepository livroRepository;
    private final Calendario calendario;

    public EmprestimoService(UsuarioRepository usuarioRepository, LivroRepository livroRepository,
            Calendario calendario) {
        this.usuarioRepository = usuarioRepository;
        this.livroRepository = livroRepository;
        this.calendario = calendario;
    }

    public EmprestimoRelationship emprestar(
            Long donoId,
            Long livroId,
            Long mutuarioId,
            LocalDate dataPrevistaDevolucao
    ) {
        LivroNode livro = buscarLivroDoDono(donoId, livroId);
        UsuarioNode mutuario = usuarioRepository.findById(mutuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario " + mutuarioId + " não encontrado."));
        if (mutuario.getId().equals(donoId)) {
            throw new IllegalArgumentException("O dono não pode emprestar o livro para si mesmo.");
        }
        if (livro.isEmprestado()) {
            throw new IllegalStateException("O livro " + livroId + " já está emprestado.");
        }

        LocalDate hoje = calendario.diaHoje();
        EmprestimoRelationship emprestimo = new EmprestimoRelationship(mutuario, hoje, dataPrevistaDevolucao);
        ultimoEmprestimoPara(livro, mutuarioId).ifPresent(emprestimo::continuarLeitura);

        livro.getEmprestimos().add(emprestimo);
        livroRepository.save(livro);
        return emprestimo;
    }

    public EmprestimoRelationship devolver(Long donoId, Long livroId, LocalDate dataDevolucao) {
        LivroNode livro = buscarLivroDoDono(donoId, livroId);
        EmprestimoRelationship ativo = livro.emprestimoAtivo()
                .orElseThrow(() -> new IllegalStateException("O livro " + livroId + " não está emprestado."));

        LocalDate hoje = calendario.diaHoje();
        ativo.registrarDevolucao(dataDevolucao == null ? hoje : dataDevolucao, hoje);

        livroRepository.save(livro);
        return ativo;
    }

    public List<EmprestimoRelationship> historico(Long donoId, Long livroId) {
        LivroNode livro = buscarLivroDoDono(donoId, livroId);
        return maisRecentesPrimeiro(livro.getEmprestimos());
    }

    public List<LivroNode> listarEmprestadosPara(Long usuarioId) {
        usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario " + usuarioId + " não encontrado."));
        return livroRepository.findAllComEmprestimoAtivoPara(usuarioId);
    }

    // Empréstimos ativos do usuário dos dois lados (livros dele com outra pessoa + livros que ele pegou),
    // do vencimento mais próximo para o mais distante
    public List<LivroNode> listarAtivosDoUsuario(Long usuarioId) {
        return ativosDoUsuario(usuarioId);
    }

    public List<LivroNode> listarVencendoDoUsuario(Long usuarioId) {
        return filtrar(ativosDoUsuario(usuarioId), EmprestimoRelationship::estaVencendo);
    }

    public List<LivroNode> listarVencidosDoUsuario(Long usuarioId) {
        return filtrar(ativosDoUsuario(usuarioId), EmprestimoRelationship::isVencido);
    }

    // O banco todo, para o notification-service
    public List<LivroNode> listarVencendo() {
        return filtrar(livroRepository.findAllComEmprestimoAtivo(), EmprestimoRelationship::estaVencendo);
    }

    public List<LivroNode> listarVencidos() {
        return filtrar(livroRepository.findAllComEmprestimoAtivo(), EmprestimoRelationship::isVencido);
    }

    private LivroNode buscarLivroDoDono(Long donoId, Long livroId) {
        usuarioRepository.findById(donoId)
                .orElseThrow(() -> new NoSuchElementException("Usuario " + donoId + " não encontrado."));
        LivroNode livro = livroRepository.findById(livroId)
                .orElseThrow(() -> new NoSuchElementException("Livro " + livroId + " não encontrado."));
        boolean doDono = usuarioRepository.findDonoDoLivro(livroId)
                .map(dono -> dono.getId().equals(donoId))
                .orElse(false);
        if (!doDono) {
            throw new NoSuchElementException("Livro " + livroId + " não encontrado para o usuario " + donoId + ".");
        }
        return livro;
    }

    // Ordena por data do empréstimo, mais recente primeiro. No empate, o criado depois vem primeiro.
    // O Neo4j não guarda a ordem dos relacionamentos (a lista volta do banco em ordem qualquer), então o desempate é
    // pelo id: maior id = criado depois; sem id (ainda não salvo) = o mais novo. Por último, a posição na lista
    // (o mais ao fim primeiro), que só vale para objetos em memória (testes).
    // TODO: o id interno do Neo4j pode ser reaproveitado depois de apagar dados; se isso virar problema, guardar a
    //  data/hora de criação do empréstimo.
    private static List<EmprestimoRelationship> maisRecentesPrimeiro(List<EmprestimoRelationship> emprestimos) {
        if (emprestimos == null) {
            return new ArrayList<>();
        }
        List<EmprestimoRelationship> ordenados = new ArrayList<>(emprestimos);
        Collections.reverse(ordenados);
        ordenados.sort(Comparator
                .comparing(EmprestimoRelationship::getDataEmprestimo, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(EmprestimoRelationship::getId, Comparator.nullsFirst(Comparator.reverseOrder())));
        return ordenados;
    }

    private static Optional<EmprestimoRelationship> ultimoEmprestimoPara(LivroNode livro, Long usuarioId) {
        return maisRecentesPrimeiro(livro.getEmprestimos()).stream()
                .filter(emprestimo -> emprestimo.getUsuario() != null
                        && usuarioId.equals(emprestimo.getUsuario().getId()))
                .findFirst();
    }

    private List<LivroNode> ativosDoUsuario(Long usuarioId) {
        usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario " + usuarioId + " não encontrado."));
        List<LivroNode> livros = new ArrayList<>(livroRepository.findAllEmprestadosPor(usuarioId));
        livros.addAll(livroRepository.findAllComEmprestimoAtivoPara(usuarioId));
        livros.sort(Comparator.comparing(EmprestimoService::dataPrevistaAtiva,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return livros;
    }

    private static LocalDate dataPrevistaAtiva(LivroNode livro) {
        return livro.emprestimoAtivo().map(EmprestimoRelationship::getDataPrevistaDevolucao).orElse(null);
    }

    private List<LivroNode> filtrar(List<LivroNode> livros, BiPredicate<EmprestimoRelationship, LocalDate> condicao) {
        LocalDate hoje = calendario.diaHoje();
        return livros.stream()
                .filter(livro -> {
                    try {
                        return livro.emprestimoAtivo()
                                .map(emprestimo -> condicao.test(emprestimo, hoje))
                                .orElse(false);
                    } catch (RuntimeException dadoInconsistente) {
                        return false;
                    }
                })
                .toList();
    }
}
