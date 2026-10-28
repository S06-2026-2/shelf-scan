package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.StatusLeitura;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;
import br.com.virabrequim.database_service.repository.LivroRepository;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import br.com.virabrequim.database_service.tempo.Calendario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmprestimoServiceAgenteTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 10);
    private static final LocalDate PREVISTA = LocalDate.of(2026, 3, 24);

    private UsuarioRepository usuarioRepository;
    private LivroRepository livroRepository;
    private EmprestimoService service;

    private UsuarioNode ana;
    private UsuarioNode bruno;
    private UsuarioNode carla;
    private LivroNode livro5;

    private static LocalDate dia(int dia, int mes) {
        return LocalDate.of(2026, mes, dia);
    }

    private static UsuarioNode usuario(long id, String nome) {
        UsuarioNode u = new UsuarioNode();
        u.setId(id);
        u.setNome(nome);
        return u;
    }

    // O construtor de sistema do EmprestimoRelationship ainda não existe: estado montado por setters.
    private static EmprestimoRelationship emprestimo(UsuarioNode usuario, LocalDate dataEmprestimo,
            LocalDate prevista, LocalDate devolucao) {
        EmprestimoRelationship e = new EmprestimoRelationship();
        e.setUsuario(usuario);
        e.setDataEmprestimo(dataEmprestimo);
        e.setDataPrevistaDevolucao(prevista);
        e.setDataDevolucao(devolucao);
        return e;
    }

    private static LivroNode livroCom(long id, EmprestimoRelationship... emprestimos) {
        LivroNode livro = new LivroNode();
        livro.setId(id);
        livro.setEmprestimos(new ArrayList<>(List.of(emprestimos)));
        return livro;
    }

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        livroRepository = mock(LivroRepository.class);
        Calendario calendario = mock(Calendario.class);
        when(calendario.diaHoje()).thenReturn(HOJE);
        service = new EmprestimoService(usuarioRepository, livroRepository, calendario);

        ana = usuario(1L, "Ana");
        bruno = usuario(2L, "Bruno");
        carla = usuario(3L, "Carla");
        livro5 = livroCom(5L);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(ana));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(bruno));
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(carla));
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());
        when(usuarioRepository.findDonoDoLivro(5L)).thenReturn(Optional.of(ana));
        when(usuarioRepository.findDonoDoLivro(99L)).thenReturn(Optional.empty());
        when(livroRepository.findById(5L)).thenReturn(Optional.of(livro5));
        when(livroRepository.findById(99L)).thenReturn(Optional.empty());
        when(livroRepository.save(any(LivroNode.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("Emprestar")
    class Emprestar {

        @Test
        @DisplayName("E1 (M4, M5): empresta a partir de hoje para Bruno, com leitura QUERO_LER na página 0")
        void empresta() {
            EmprestimoRelationship retornado = service.emprestar(1L, 5L, 2L, PREVISTA);

            assertThat(livro5.getEmprestimos()).hasSize(1);
            EmprestimoRelationship criado = livro5.getEmprestimos().get(0);
            assertAll(
                    () -> assertTrue(criado.isEmprestado()),
                    () -> assertEquals(2L, criado.getUsuario().getId()),
                    () -> assertEquals(HOJE, criado.getDataEmprestimo()),
                    () -> assertEquals(PREVISTA, criado.getDataPrevistaDevolucao()),
                    () -> assertEquals(StatusLeitura.QUERO_LER, criado.getStatusLeitura()),
                    () -> assertEquals(0, criado.getPaginaAtual()),
                    () -> verify(livroRepository).save(livro5),
                    () -> assertSame(criado, retornado));
        }

        @Test
        @DisplayName("E2 (M5): o novo empréstimo é acrescentado e o histórico continua")
        void mantemHistorico() {
            EmprestimoRelationship antigo1 = emprestimo(bruno, dia(1, 1), dia(10, 1), dia(9, 1));
            EmprestimoRelationship antigo2 = emprestimo(bruno, dia(1, 2), dia(10, 2), dia(9, 2));
            livro5.getEmprestimos().addAll(List.of(antigo1, antigo2));

            service.emprestar(1L, 5L, 3L, PREVISTA);

            assertAll(
                    () -> assertThat(livro5.getEmprestimos()).hasSize(3),
                    () -> assertThat(livro5.getEmprestimos()).contains(antigo1, antigo2));
        }

        @Test
        @DisplayName("E3 (M3): livro já emprestado lança IllegalStateException e nada é salvo")
        void jaEmprestado() {
            livro5.getEmprestimos().add(emprestimo(bruno, dia(1, 3), dia(12, 3), null));
            assertThrows(IllegalStateException.class, () -> service.emprestar(1L, 5L, 3L, PREVISTA));
            verify(livroRepository, never()).save(any());
        }

        @Test
        @DisplayName("E4 (M2): emprestar para si mesmo lança IllegalArgumentException")
        void paraSiMesmo() {
            assertThrows(IllegalArgumentException.class, () -> service.emprestar(1L, 5L, 1L, PREVISTA));
        }

        @Test
        @DisplayName("E5 (M1): quem não é o dono lança NoSuchElementException")
        void naoDono() {
            assertThrows(NoSuchElementException.class, () -> service.emprestar(2L, 5L, 3L, PREVISTA));
        }

        @Test
        @DisplayName("E6 (M1): mutuário inexistente lança NoSuchElementException")
        void mutuarioInexistente() {
            assertThrows(NoSuchElementException.class, () -> service.emprestar(1L, 5L, 99L, PREVISTA));
        }

        @Test
        @DisplayName("E7 (M4): data prevista null ou igual a hoje lança IllegalArgumentException e nada é salvo")
        void dataPrevistaInvalida() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> service.emprestar(1L, 5L, 2L, null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> service.emprestar(1L, 5L, 2L, HOJE)),
                    () -> verify(livroRepository, never()).save(any()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("M1 (derivado): livro inexistente lança NoSuchElementException")
        void livroInexistente() {
            assertThrows(NoSuchElementException.class, () -> service.emprestar(1L, 99L, 2L, PREVISTA));
        }

        @Test
        @Tag("derivado")
        @DisplayName("M1 (derivado): dono inexistente lança NoSuchElementException")
        void donoInexistente() {
            assertThrows(NoSuchElementException.class, () -> service.emprestar(99L, 5L, 2L, PREVISTA));
        }
    }

    @Nested
    @DisplayName("Devolver")
    class Devolver {

        private EmprestimoRelationship ativo;

        @BeforeEach
        void emprestaParaBruno() {
            ativo = emprestimo(bruno, dia(1, 3), dia(12, 3), null);
            livro5.getEmprestimos().add(ativo);
        }

        @Test
        @DisplayName("E8 (M6): data null devolve hoje, salva e retorna o empréstimo")
        void devolveHoje() {
            EmprestimoRelationship retornado = service.devolver(1L, 5L, null);
            assertAll(
                    () -> assertEquals(HOJE, ativo.getDataDevolucao()),
                    () -> assertTrue(ativo.isDevolvido()),
                    () -> verify(livroRepository).save(livro5),
                    () -> assertSame(ativo, retornado));
        }

        @Test
        @DisplayName("E9 (M6): devolução em 05/03/2026")
        void devolveNaData() {
            service.devolver(1L, 5L, dia(5, 3));
            assertEquals(dia(5, 3), ativo.getDataDevolucao());
        }

        @Test
        @DisplayName("E10 (M6): devolução amanhã lança IllegalArgumentException e nada é salvo")
        void devolveAmanha() {
            assertThrows(IllegalArgumentException.class, () -> service.devolver(1L, 5L, dia(11, 3)));
            verify(livroRepository, never()).save(any());
        }

        @Test
        @DisplayName("E11 (M6): livro sem empréstimo ativo lança IllegalStateException")
        void semEmprestimoAtivo() {
            livro5.getEmprestimos().clear();
            assertThrows(IllegalStateException.class, () -> service.devolver(1L, 5L, null));
        }

        @Test
        @DisplayName("E12 (M6, M1): quem pegou emprestado não registra a devolução")
        void mutuarioNaoDevolve() {
            assertThrows(NoSuchElementException.class, () -> service.devolver(2L, 5L, null));
        }

        @Test
        @Tag("derivado")
        @DisplayName("M6 (derivado): devolver livro inexistente lança NoSuchElementException")
        void livroInexistente() {
            assertThrows(NoSuchElementException.class, () -> service.devolver(1L, 99L, null));
        }
    }

    @Nested
    @DisplayName("Histórico")
    class Historico {

        @Test
        @DisplayName("E16 (M10): ordena do mais recente para o mais antigo sem alterar a lista do livro")
        void ordena() {
            EmprestimoRelationship x = emprestimo(bruno, dia(1, 2), dia(10, 2), dia(9, 2));
            EmprestimoRelationship y = emprestimo(carla, dia(1, 3), dia(12, 3), null);
            EmprestimoRelationship w = emprestimo(bruno, dia(15, 2), dia(25, 2), dia(20, 2));
            livro5.getEmprestimos().addAll(List.of(x, y, w));

            List<EmprestimoRelationship> historico = service.historico(1L, 5L);

            assertAll(
                    () -> assertThat(historico).containsExactly(y, w, x),
                    () -> assertThat(livro5.getEmprestimos()).containsExactly(x, y, w),
                    () -> verify(livroRepository, never()).save(any()));
        }

        @Test
        @DisplayName("E17 (M10): empate na data, o acrescentado depois vem primeiro")
        void empate() {
            EmprestimoRelationship p = emprestimo(bruno, HOJE, dia(20, 3), HOJE);
            EmprestimoRelationship q = emprestimo(carla, HOJE, dia(20, 3), null);
            livro5.getEmprestimos().addAll(List.of(p, q));

            assertThat(service.historico(1L, 5L)).containsExactly(q, p);
        }

        @Test
        @DisplayName("E18 (M10): livro sem empréstimos retorna lista vazia")
        void vazio() {
            assertThat(service.historico(1L, 5L)).isEmpty();
        }

        @Test
        @DisplayName("E19 (M10, M1): quem não é o dono ou livro inexistente lança NoSuchElementException")
        void naoDono() {
            assertAll(
                    () -> assertThrows(NoSuchElementException.class, () -> service.historico(2L, 5L)),
                    () -> assertThrows(NoSuchElementException.class, () -> service.historico(1L, 99L)));
        }
    }

    @Nested
    @DisplayName("Listagens")
    class Listagens {

        private LivroNode livroA;
        private LivroNode livroB;
        private LivroNode livroC;
        private LivroNode livroD;

        @BeforeEach
        void livros() {
            livroA = livroCom(101L, emprestimo(bruno, dia(1, 3), dia(12, 3), null));
            livroB = livroCom(102L, emprestimo(bruno, dia(1, 3), dia(30, 3), null));
            livroC = livroCom(103L, emprestimo(bruno, dia(1, 3), dia(8, 3), null));
            livroD = livroCom(104L, emprestimo(bruno, dia(1, 3), null, null));
        }

        @Test
        @DisplayName("E13 (M7): lista os livros emprestados para o usuário; usuário inexistente lança NoSuchElementException")
        void emprestadosPara() {
            LivroNode livro6 = livroCom(6L);
            when(livroRepository.findAllComEmprestimoAtivoPara(2L)).thenReturn(List.of(livro5, livro6));
            assertAll(
                    () -> assertThat(service.listarEmprestadosPara(2L)).containsExactly(livro5, livro6),
                    () -> assertThrows(NoSuchElementException.class, () -> service.listarEmprestadosPara(99L)));
        }

        @Test
        @DisplayName("E14 (M8): vencendo = [A] e vencidos = [C]")
        void vencendoEVencidos() {
            when(livroRepository.findAllComEmprestimoAtivo()).thenReturn(List.of(livroA, livroB, livroC));
            assertAll(
                    () -> assertThat(service.listarVencendo()).containsExactly(livroA),
                    () -> assertThat(service.listarVencidos()).containsExactly(livroC));
        }

        @Test
        @DisplayName("E15 (M9): livro com empréstimo sem data prevista é ignorado em listarVencendo")
        void ignoraInconsistenteEmVencendo() {
            when(livroRepository.findAllComEmprestimoAtivo()).thenReturn(List.of(livroA, livroD));
            List<LivroNode> vencendo = assertDoesNotThrow(() -> service.listarVencendo());
            assertThat(vencendo).containsExactly(livroA);
        }

        @Test
        @Tag("derivado")
        @DisplayName("M9 (derivado): livro com empréstimo sem data prevista é ignorado em listarVencidos")
        void ignoraInconsistenteEmVencidos() {
            when(livroRepository.findAllComEmprestimoAtivo()).thenReturn(List.of(livroD, livroC));
            List<LivroNode> vencidos = assertDoesNotThrow(() -> service.listarVencidos());
            assertThat(vencidos).containsExactly(livroC);
        }

        @Test
        @Tag("derivado")
        @DisplayName("M8 (derivado): livro que vence hoje está em vencendo e não em vencidos")
        void venceHoje() {
            LivroNode venceHoje = livroCom(105L, emprestimo(bruno, dia(1, 3), HOJE, null));
            when(livroRepository.findAllComEmprestimoAtivo()).thenReturn(List.of(venceHoje));
            assertAll(
                    () -> assertThat(service.listarVencendo()).containsExactly(venceHoje),
                    () -> assertThat(service.listarVencidos()).isEmpty());
        }
    }
}
