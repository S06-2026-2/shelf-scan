package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.StatusLeitura;
import br.com.virabrequim.database_service.domain.node.EstanteNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.domain.relationship.EmprestimoRelationship;
import br.com.virabrequim.database_service.repository.AutorRepository;
import br.com.virabrequim.database_service.repository.LivroRepository;
import br.com.virabrequim.database_service.repository.TagRepository;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import br.com.virabrequim.database_service.tempo.Calendario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LivroServiceAgenteTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 10);
    // 10/03/2026 14:30 em Brasília (-03:00)
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-03-10T17:30:00Z"),
            ZoneId.of("America/Sao_Paulo"));
    private static final OffsetDateTime MOMENTO_ATUAL = OffsetDateTime.of(2026, 3, 10, 14, 30, 0, 0,
            ZoneOffset.ofHours(-3));

    private UsuarioRepository usuarioRepository;
    private LivroRepository livroRepository;
    private LivroService service;

    private UsuarioNode ana;
    private UsuarioNode bruno;
    private UsuarioNode carla;
    private EstanteNode estante10;
    private EstanteNode estante11;
    private LivroNode livro5;

    private static UsuarioNode usuario(long id, String nome, EstanteNode padrao, EstanteNode... outras) {
        UsuarioNode u = new UsuarioNode();
        u.setId(id);
        u.setNome(nome);
        List<EstanteNode> estantes = new ArrayList<>();
        if (padrao != null) {
            estantes.add(padrao);
        }
        estantes.addAll(List.of(outras));
        u.setEstantes(estantes);
        u.setEstantePadrao(padrao);
        return u;
    }

    // O construtor de sistema do EmprestimoRelationship ainda não existe: estado montado por setters.
    private EmprestimoRelationship emprestimoAtivoPara(UsuarioNode usuario) {
        EmprestimoRelationship e = new EmprestimoRelationship();
        e.setUsuario(usuario);
        e.setDataEmprestimo(LocalDate.of(2026, 3, 1));
        e.setDataPrevistaDevolucao(LocalDate.of(2026, 3, 12));
        e.setStatusLeitura(StatusLeitura.QUERO_LER);
        e.setPaginaAtual(0);
        return e;
    }

    private EmprestimoRelationship emprestimoDevolvidoPara(UsuarioNode usuario) {
        EmprestimoRelationship e = emprestimoAtivoPara(usuario);
        e.setDataDevolucao(LocalDate.of(2026, 3, 5));
        return e;
    }

    private static LivroNode livroNovo(String titulo) {
        LivroNode livro = new LivroNode();
        livro.setTitulo(titulo);
        return livro;
    }

    private LivroNode capturarSalvo() {
        ArgumentCaptor<LivroNode> captor = ArgumentCaptor.forClass(LivroNode.class);
        verify(livroRepository).save(captor.capture());
        return captor.getValue();
    }

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        livroRepository = mock(LivroRepository.class);
        Calendario calendario = mock(Calendario.class);
        when(calendario.diaHoje()).thenReturn(HOJE);
        when(calendario.getClock()).thenReturn(RELOGIO);
        service = new LivroService(usuarioRepository, livroRepository, mock(AutorRepository.class),
                mock(TagRepository.class), calendario);

        estante10 = new EstanteNode(10L, "Minha estante");
        estante11 = new EstanteNode(11L, "Quarto");
        ana = usuario(1L, "Ana", estante10, estante11);
        bruno = usuario(2L, "Bruno", new EstanteNode(20L, "Minha estante"));
        carla = usuario(3L, "Carla", new EstanteNode(30L, "Minha estante"));

        livro5 = new LivroNode();
        livro5.setId(5L);
        livro5.setTitulo("Memórias do Subsolo");
        livro5.setPaginas(200);
        livro5.setEstante(estante10);
        livro5.setEmprestimos(new ArrayList<>());

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(ana));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(bruno));
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(carla));
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());
        when(usuarioRepository.findDonoDoLivro(5L)).thenReturn(Optional.of(ana));
        when(livroRepository.findById(5L)).thenReturn(Optional.of(livro5));
        when(livroRepository.findById(99L)).thenReturn(Optional.empty());
        when(livroRepository.save(any(LivroNode.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("Adicionar")
    class Adicionar {

        @Test
        @DisplayName("E1 (L4, L5, L6): sem estante vai para a padrão, com título sem espaços e data atual")
        void adicionaNaPadrao() {
            LivroNode retornadoPeloSave = new LivroNode();
            when(livroRepository.save(any(LivroNode.class))).thenReturn(retornadoPeloSave);

            LivroNode retornado = service.adicionar(1L, livroNovo("  Memórias do Subsolo "), null, null, null);

            LivroNode salvo = capturarSalvo();
            assertAll(
                    () -> assertEquals("Memórias do Subsolo", salvo.getTitulo()),
                    () -> assertEquals(10L, salvo.getEstante().getId()),
                    () -> assertEquals(MOMENTO_ATUAL, salvo.getDataDeAdicao().toOffsetDateTime()),
                    () -> assertSame(retornadoPeloSave, retornado));
        }

        @Test
        @DisplayName("E2 (L4): com estante 11 informada, o livro vai para ela")
        void adicionaNaEstanteInformada() {
            service.adicionar(1L, livroNovo("Livro"), 11L, null, null);
            assertEquals(11L, capturarSalvo().getEstante().getId());
        }

        @Test
        @DisplayName("E3 (L4): estante de outro usuário ou inexistente lança NoSuchElementException")
        void estanteInvalida() {
            assertAll(
                    () -> assertThrows(NoSuchElementException.class,
                            () -> service.adicionar(1L, livroNovo("Livro"), 20L, null, null)),
                    () -> assertThrows(NoSuchElementException.class,
                            () -> service.adicionar(1L, livroNovo("Livro"), 999L, null, null)),
                    () -> verify(livroRepository, never()).save(any()));
        }

        @Test
        @DisplayName("E4 (L5): id, empréstimos e data de adição recebidos são descartados")
        void livroSempreNovo() {
            LivroNode recebido = livroNovo("Livro");
            recebido.setId(50L);
            recebido.setEmprestimos(new ArrayList<>(List.of(emprestimoAtivoPara(bruno))));
            recebido.setDataDeAdicao(ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneId.of("America/Sao_Paulo")));

            service.adicionar(1L, recebido, null, null, null);

            LivroNode salvo = capturarSalvo();
            assertAll(
                    () -> assertNull(salvo.getId()),
                    () -> assertThat(salvo.getEmprestimos()).isNotNull().isEmpty(),
                    () -> assertEquals(MOMENTO_ATUAL, salvo.getDataDeAdicao().toOffsetDateTime()));
        }

        @Test
        @DisplayName("E5 (L2): livro null lança IllegalArgumentException")
        void livroNull() {
            assertThrows(IllegalArgumentException.class, () -> service.adicionar(1L, null, null, null, null));
            verify(livroRepository, never()).save(any());
        }

        @ParameterizedTest(name = "E5: título [{0}] lança IllegalArgumentException")
        @DisplayName("E5 (L2): título inválido")
        @CsvSource(value = {"NULL", "''", "'  '"}, nullValues = "NULL")
        void tituloInvalido(String titulo) {
            assertThrows(IllegalArgumentException.class, () -> service.adicionar(1L, livroNovo(titulo), null, null, null));
            verify(livroRepository, never()).save(any());
        }

        @Test
        @DisplayName("E6 (L3): páginas null é aceito")
        void paginasNull() {
            LivroNode livro = livroNovo("Livro");
            livro.setPaginas(null);
            assertDoesNotThrow(() -> service.adicionar(1L, livro, null, null, null));
        }

        @ParameterizedTest(name = "E6: páginas = {0} lança IllegalArgumentException")
        @DisplayName("E6 (L3): páginas zero ou negativo")
        @CsvSource({"0", "-5"})
        void paginasInvalidas(int paginas) {
            LivroNode livro = livroNovo("Livro");
            livro.setPaginas(paginas);
            assertThrows(IllegalArgumentException.class, () -> service.adicionar(1L, livro, null, null, null));
        }

        @Test
        @DisplayName("E7 (L1): usuário inexistente lança NoSuchElementException")
        void usuarioInexistente() {
            assertThrows(NoSuchElementException.class, () -> service.adicionar(99L, livroNovo("Livro"), null, null, null));
        }

        @Test
        @Tag("derivado")
        @DisplayName("L3 (derivado): páginas = 1 (menor valor válido) é aceito")
        void umaPagina() {
            LivroNode livro = livroNovo("Livro");
            livro.setPaginas(1);
            assertDoesNotThrow(() -> service.adicionar(1L, livro, null, null, null));
        }
    }

    @Nested
    @DisplayName("Buscar e listar")
    class BuscarEListar {

        @Test
        @DisplayName("E8 (L1): livro inexistente lança NoSuchElementException")
        void buscarInexistente() {
            when(livroRepository.findById(5L)).thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class, () -> service.buscar(5L));
        }

        @Test
        @DisplayName("E9 (L4): lista os livros de uma estante do usuário; estante de outro lança NoSuchElementException")
        void listarDaEstante() {
            LivroNode a = livroNovo("A");
            LivroNode b = livroNovo("B");
            when(livroRepository.findAllByEstanteId(11L)).thenReturn(List.of(a, b));
            assertAll(
                    () -> assertThat(service.listarDaEstante(1L, 11L)).containsExactly(a, b),
                    () -> assertThrows(NoSuchElementException.class, () -> service.listarDaEstante(1L, 20L)));
        }
    }

    @Nested
    @DisplayName("Mover")
    class Mover {

        @Test
        @DisplayName("E10 (L8): mover para a estante 11 troca a estante e salva")
        void mover() {
            service.mover(1L, 5L, 11L);
            assertAll(
                    () -> assertEquals(11L, livro5.getEstante().getId()),
                    () -> verify(livroRepository).save(livro5));
        }

        @Test
        @DisplayName("E11 (L8): estante de destino de outro usuário lança NoSuchElementException e nada é salvo")
        void destinoDeOutro() {
            assertThrows(NoSuchElementException.class, () -> service.mover(1L, 5L, 20L));
            verify(livroRepository, never()).save(any());
        }

        @Test
        @DisplayName("E12 (L7): usuário que não é dono lança NoSuchElementException")
        void naoDono() {
            assertThrows(NoSuchElementException.class, () -> service.mover(2L, 5L, 20L));
        }

        @Test
        @DisplayName("E13 (L8): mover livro emprestado é aceito")
        void moverEmprestado() {
            livro5.getEmprestimos().add(emprestimoAtivoPara(bruno));
            assertDoesNotThrow(() -> service.mover(1L, 5L, 11L));
        }

        @Test
        @Tag("derivado")
        @DisplayName("L8 (derivado): mover para a estante em que já está não é erro")
        void moverParaMesmaEstante() {
            service.mover(1L, 5L, 10L);
            assertAll(
                    () -> assertEquals(10L, livro5.getEstante().getId()),
                    () -> verify(livroRepository).save(livro5));
        }

        @Test
        @Tag("derivado")
        @DisplayName("L1 (derivado): mover livro inexistente lança NoSuchElementException")
        void moverInexistente() {
            assertThrows(NoSuchElementException.class, () -> service.mover(1L, 99L, 11L));
        }
    }

    @Nested
    @DisplayName("Remover")
    class Remover {

        @Test
        @DisplayName("E14 (L9): livro sem empréstimos é apagado")
        void semEmprestimos() {
            service.remover(1L, 5L);
            verify(livroRepository).delete(livro5);
        }

        @Test
        @DisplayName("E15 (L9): livro com empréstimo ativo lança IllegalStateException e não é apagado")
        void comEmprestimoAtivo() {
            livro5.getEmprestimos().add(emprestimoAtivoPara(bruno));
            assertThrows(IllegalStateException.class, () -> service.remover(1L, 5L));
            verify(livroRepository, never()).delete(any());
        }

        @Test
        @DisplayName("E16 (L9): livro só com empréstimos devolvidos é apagado")
        void soDevolvidos() {
            livro5.getEmprestimos().add(emprestimoDevolvidoPara(bruno));
            service.remover(1L, 5L);
            verify(livroRepository).delete(livro5);
        }

        @Test
        @DisplayName("E17 (L7): usuário que não é dono lança NoSuchElementException e não apaga")
        void naoDono() {
            assertThrows(NoSuchElementException.class, () -> service.remover(2L, 5L));
            verify(livroRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Leitura")
    class Leitura {

        private EmprestimoRelationship emprestimoDoBruno;

        @BeforeEach
        void emprestaParaBruno() {
            emprestimoDoBruno = emprestimoAtivoPara(bruno);
            livro5.getEmprestimos().add(emprestimoDoBruno);
        }

        @Test
        @DisplayName("E18 (L10): o dono atualiza a leitura do livro, não a do empréstimo")
        void donoAtualizaLivro() {
            service.atualizarLeitura(1L, 5L, StatusLeitura.LENDO, 80);
            assertAll(
                    () -> assertEquals(StatusLeitura.LENDO, livro5.getStatusLeitura()),
                    () -> assertEquals(80, livro5.getPaginaAtual()),
                    () -> assertEquals(40, livro5.getPorcentagemLeitura()),
                    () -> assertEquals(StatusLeitura.QUERO_LER, emprestimoDoBruno.getStatusLeitura()),
                    () -> assertEquals(0, emprestimoDoBruno.getPaginaAtual()),
                    () -> assertNull(emprestimoDoBruno.getDataConclusaoLeitura()),
                    () -> verify(livroRepository).save(livro5));
        }

        @Test
        @DisplayName("E19 (L10): quem pegou emprestado atualiza a leitura do empréstimo ativo, não a do livro")
        void mutuarioAtualizaEmprestimo() {
            service.atualizarLeitura(2L, 5L, StatusLeitura.LIDO, null);
            assertAll(
                    () -> assertEquals(StatusLeitura.LIDO, emprestimoDoBruno.getStatusLeitura()),
                    () -> assertEquals(200, emprestimoDoBruno.getPaginaAtual()),
                    () -> assertEquals(100, emprestimoDoBruno.porcentagemLeitura(200)),
                    () -> assertEquals(HOJE, emprestimoDoBruno.getDataConclusaoLeitura()),
                    () -> assertNull(livro5.getStatusLeitura()),
                    () -> assertNull(livro5.getPaginaAtual()),
                    () -> assertNull(livro5.getDataConclusaoLeitura()),
                    () -> verify(livroRepository).save(livro5));
        }

        @Test
        @DisplayName("E20 (L10): usuário sem relação com o livro lança NoSuchElementException e nada é salvo")
        void semRelacao() {
            assertThrows(NoSuchElementException.class,
                    () -> service.atualizarLeitura(3L, 5L, StatusLeitura.LENDO, 10));
            verify(livroRepository, never()).save(any());
        }

        @Test
        @DisplayName("E21 (L10): página acima do total lança IllegalArgumentException e nada é salvo")
        void paginaInvalida() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.atualizarLeitura(1L, 5L, StatusLeitura.LENDO, 250));
            verify(livroRepository, never()).save(any());
        }

        @Test
        @Tag("derivado")
        @DisplayName("L10 (derivado): quem só tem empréstimo devolvido do livro lança NoSuchElementException")
        void soEmprestimoDevolvido() {
            livro5.getEmprestimos().clear();
            livro5.getEmprestimos().add(emprestimoDevolvidoPara(bruno));
            assertThrows(NoSuchElementException.class,
                    () -> service.atualizarLeitura(2L, 5L, StatusLeitura.LENDO, 10));
            verify(livroRepository, never()).save(any());
        }
    }
}
