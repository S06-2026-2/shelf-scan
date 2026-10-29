package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.StatusLeitura;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
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
    private AutorRepository autorRepository;
    private TagRepository tagRepository;
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
        autorRepository = mock(AutorRepository.class);
        tagRepository = mock(TagRepository.class);
        service = new LivroService(usuarioRepository, livroRepository, autorRepository, tagRepository, calendario);

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

        @ParameterizedTest(name = "E5: livro do construtor vazio com título [{0}] lança IllegalArgumentException")
        @DisplayName("E5 (L2): o adicionar revalida o título do livro montado pelo construtor vazio")
        @CsvSource(value = {"NULL", "''", "'  '"}, nullValues = "NULL")
        void tituloInvalidoRevalidado(String titulo) throws Exception {
            // O setTitulo já recusa esses valores; o campo é preenchido por reflexão para que a exceção venha do
            // próprio adicionar (com null, o construtor vazio já deixa o título null).
            LivroNode livro = new LivroNode();
            Field campo = LivroNode.class.getDeclaredField("titulo");
            campo.setAccessible(true);
            campo.set(livro, titulo);
            assertThrows(IllegalArgumentException.class, () -> service.adicionar(1L, livro, null, null, null));
            verify(livroRepository, never()).save(any());
        }

        @Test
        @Tag("derivado")
        @DisplayName("L2 (derivado): o adicionar remove os espaços das pontas de um título que chegou sem passar pelo setter")
        void tituloComEspacosRevalidado() throws Exception {
            LivroNode livro = new LivroNode();
            Field campo = LivroNode.class.getDeclaredField("titulo");
            campo.setAccessible(true);
            campo.set(livro, "  Duna ");
            service.adicionar(1L, livro, null, null, null);
            assertEquals("Duna", capturarSalvo().getTitulo());
        }

        @Test
        @DisplayName("E6 (L3): páginas = 0 é aceito e fica null")
        void paginasZero() {
            LivroNode livro = livroNovo("Livro");
            livro.setPaginas(0);
            assertDoesNotThrow(() -> service.adicionar(1L, livro, null, null, null));
            assertNull(capturarSalvo().getPaginas());
        }

        @Test
        @DisplayName("E6 (L3): páginas = -5 lança IllegalArgumentException no adicionar")
        void paginasNegativas() throws Exception {
            // O setPaginas já recusa -5; o campo é preenchido por reflexão para simular um livro que chegou com
            // páginas inválidas e verificar que o próprio adicionar revalida.
            LivroNode livro = livroNovo("Livro");
            Field paginas = LivroNode.class.getDeclaredField("paginas");
            paginas.setAccessible(true);
            paginas.set(livro, -5);
            assertThrows(IllegalArgumentException.class, () -> service.adicionar(1L, livro, null, null, null));
            verify(livroRepository, never()).save(any());
        }

        @Test
        @DisplayName("E1 (L6): sem status, o livro salvo não tem leitura")
        void semLeitura() {
            service.adicionar(1L, livroNovo("Livro"), null, null, null);
            LivroNode salvo = capturarSalvo();
            assertAll(
                    () -> assertNull(salvo.getStatusLeitura()),
                    () -> assertNull(salvo.getPaginaAtual()),
                    () -> assertNull(salvo.getDataConclusaoLeitura()));
        }

        @Test
        @DisplayName("E8 (L6): LENDO na página 80 de 200 é aplicado antes do save")
        void adicionaLendo() {
            LivroNode livro = livroNovo("Livro");
            livro.setPaginas(200);
            service.adicionar(1L, livro, null, StatusLeitura.LENDO, 80);
            LivroNode salvo = capturarSalvo();
            assertAll(
                    () -> assertEquals(StatusLeitura.LENDO, salvo.getStatusLeitura()),
                    () -> assertEquals(80, salvo.getPaginaAtual()),
                    () -> assertEquals(40, salvo.getPorcentagemLeitura()));
        }

        @Test
        @DisplayName("E8 (L6): LENDO na página 500 de 200 fica na página 0")
        void adicionaLendoPaginaInvalida() {
            LivroNode livro = livroNovo("Livro");
            livro.setPaginas(200);
            service.adicionar(1L, livro, null, StatusLeitura.LENDO, 500);
            LivroNode salvo = capturarSalvo();
            assertAll(
                    () -> assertEquals(StatusLeitura.LENDO, salvo.getStatusLeitura()),
                    () -> assertEquals(0, salvo.getPaginaAtual()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("L6 (derivado): adicionar como LIDO usa hoje do calendário como conclusão")
        void adicionaLido() {
            LivroNode livro = livroNovo("Livro");
            livro.setPaginas(200);
            service.adicionar(1L, livro, null, StatusLeitura.LIDO, null);
            LivroNode salvo = capturarSalvo();
            assertAll(
                    () -> assertEquals(StatusLeitura.LIDO, salvo.getStatusLeitura()),
                    () -> assertEquals(200, salvo.getPaginaAtual()),
                    () -> assertEquals(HOJE, salvo.getDataConclusaoLeitura()));
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
        @DisplayName("L8 (derivado): mover retorna o objeto devolvido pelo save")
        void moverRetornaSalvo() {
            LivroNode retornadoPeloSave = new LivroNode();
            when(livroRepository.save(any(LivroNode.class))).thenReturn(retornadoPeloSave);
            assertSame(retornadoPeloSave, service.mover(1L, 5L, 11L));
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
        @DisplayName("E30 (L10): página acima do total fica LENDO na página 0, sem exceção, e o livro é salvo")
        void paginaInvalida() {
            assertDoesNotThrow(() -> service.atualizarLeitura(1L, 5L, StatusLeitura.LENDO, 250));
            assertAll(
                    () -> assertEquals(StatusLeitura.LENDO, livro5.getStatusLeitura()),
                    () -> assertEquals(0, livro5.getPaginaAtual()),
                    () -> verify(livroRepository).save(livro5));
        }

        @Test
        @Tag("derivado")
        @DisplayName("L10 (derivado): atualizarLeitura retorna o objeto devolvido pelo save")
        void atualizarLeituraRetornaSalvo() {
            LivroNode retornadoPeloSave = new LivroNode();
            when(livroRepository.save(any(LivroNode.class))).thenReturn(retornadoPeloSave);
            assertSame(retornadoPeloSave, service.atualizarLeitura(1L, 5L, StatusLeitura.LENDO, 80));
        }

        @Test
        @DisplayName("E31 (L10): status null lança IllegalArgumentException e nada é salvo")
        void statusNull() {
            assertThrows(IllegalArgumentException.class, () -> service.atualizarLeitura(1L, 5L, null, 10));
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

    @Nested
    @DisplayName("Autores e tags (adicionar)")
    class AutoresETags {

        @Test
        @DisplayName("E9 (L11): autor com o mesmo nome no banco é reaproveitado")
        void autorDoBanco() {
            AutorNode doBanco = new AutorNode("Fiódor Dostoiévski");
            doBanco.setId(7L);
            when(autorRepository.findFirstByNomeIgnoreCase("fiódor dostoiévski")).thenReturn(Optional.of(doBanco));
            LivroNode livro = livroNovo("Livro");
            livro.setAutores(new ArrayList<>(List.of(new AutorNode("fiódor   dostoiévski"))));

            service.adicionar(1L, livro, null, null, null);

            assertThat(capturarSalvo().getAutores()).singleElement().extracting(AutorNode::getId).isEqualTo(7L);
        }

        @Test
        @DisplayName("E10 (L11): autores repetidos sem diferenciar maiúsculas viram um só")
        void autoresRepetidos() {
            LivroNode livro = livroNovo("Livro");
            livro.setAutores(new ArrayList<>(List.of(new AutorNode("Ana Maria"), new AutorNode("ana maria"))));

            service.adicionar(1L, livro, null, null, null);

            assertThat(capturarSalvo().getAutores()).singleElement().extracting(AutorNode::getNome)
                    .isEqualTo("Ana Maria");
        }

        @Test
        @DisplayName("E11 (L11): tags com o mesmo nome normalizado viram a tag do banco")
        void tagDoBanco() {
            TagNode doBanco = new TagNode("Ficção");
            when(tagRepository.findById("ficcao")).thenReturn(Optional.of(doBanco));
            LivroNode livro = livroNovo("Livro");
            livro.setTags(new java.util.HashSet<>(List.of(new TagNode("FICÇÃO"), new TagNode("ficcao"))));

            service.adicionar(1L, livro, null, null, null);

            assertThat(capturarSalvo().getTags()).singleElement().isSameAs(doBanco);
        }
    }

    @Nested
    @DisplayName("Atualizar (PATCH)")
    class Atualizar {

        private AutorNode autorX;
        private TagNode romance;

        @BeforeEach
        void livroComAutorETag() {
            autorX = new AutorNode("Autor X");
            autorX.setId(7L);
            romance = new TagNode("romance");
            livro5.setAutores(new ArrayList<>(List.of(autorX)));
            livro5.setTags(new java.util.HashSet<>(Set.of(romance)));
        }

        private AtualizacaoLivro dados(String titulo, List<String> autores, List<String> tags, String isbn,
                String volume, Integer paginas, String sinopse) {
            return new AtualizacaoLivro(titulo, autores, tags, isbn, volume, paginas, sinopse);
        }

        @Test
        @DisplayName("E14 (L12): só o título muda; autores, tags, páginas e leitura continuam")
        void soTitulo() {
            service.atualizar(1L, 5L, dados("Novo", null, null, null, null, null, null));
            LivroNode salvo = capturarSalvo();
            assertAll(
                    () -> assertEquals("Novo", salvo.getTitulo()),
                    () -> assertThat(salvo.getAutores()).containsExactly(autorX),
                    () -> assertThat(salvo.getTags()).containsExactly(romance),
                    () -> assertEquals(200, salvo.getPaginas()),
                    () -> assertNull(salvo.getStatusLeitura()),
                    () -> assertNull(salvo.getPaginaAtual()));
        }

        @Test
        @DisplayName("E15 (L12, L13): autores e tags substituem a lista inteira e os antigos vão para limpeza de órfãos")
        void substituiAutoresETags() {
            service.atualizar(1L, 5L, dados(null, List.of("Autor Y"), List.of(), null, null, null, null));
            LivroNode salvo = capturarSalvo();
            assertAll(
                    () -> assertThat(salvo.getAutores()).singleElement().extracting(AutorNode::getNome)
                            .isEqualTo("Autor Y"),
                    () -> assertThat(salvo.getTags()).isEmpty(),
                    () -> verify(autorRepository).apagarOrfaos(argThat(ids -> ids.size() == 1 && ids.contains(7L))),
                    () -> verify(tagRepository).apagarOrfas(
                            argThat(nomes -> nomes.size() == 1 && nomes.contains("romance"))));
        }

        @Test
        @DisplayName("E16 (L12): LENDO na 150 e páginas = 100 lança IllegalStateException e não salva")
        void paginasConflitam() {
            livro5.atualizarLeitura(StatusLeitura.LENDO, 150, HOJE);
            assertThrows(IllegalStateException.class,
                    () -> service.atualizar(1L, 5L, dados(null, null, null, null, null, 100, null)));
            verify(livroRepository, never()).save(any());
        }

        @Test
        @DisplayName("E16 (L12): LENDO na 150 e páginas = 0 remove o total")
        void paginasZeroRemoveTotal() {
            livro5.atualizarLeitura(StatusLeitura.LENDO, 150, HOJE);
            service.atualizar(1L, 5L, dados(null, null, null, null, null, 0, null));
            assertNull(capturarSalvo().getPaginas());
        }

        @Test
        @DisplayName("E17 (L12): ISBN vazio apaga o ISBN")
        void isbnVazio() {
            livro5.setIsbn("123");
            service.atualizar(1L, 5L, dados(null, null, null, "", null, null, null));
            assertNull(capturarSalvo().getIsbn());
        }

        @Test
        @DisplayName("E18 (L7): usuário que não é dono lança NoSuchElementException e não salva")
        void naoDono() {
            assertThrows(NoSuchElementException.class,
                    () -> service.atualizar(2L, 5L, dados("Novo", null, null, null, null, null, null)));
            verify(livroRepository, never()).save(any());
        }

        @Test
        @Tag("derivado")
        @DisplayName("L12 (derivado): atualizar retorna o objeto devolvido pelo save")
        void atualizarRetornaSalvo() {
            LivroNode retornadoPeloSave = new LivroNode();
            when(livroRepository.save(any(LivroNode.class))).thenReturn(retornadoPeloSave);
            assertSame(retornadoPeloSave, service.atualizar(1L, 5L, dados("Novo", null, null, null, null, null, null)));
        }

        @Test
        @Tag("derivado")
        @DisplayName("L12 (derivado): volume e sinopse só com espaços viram null")
        void volumeESinopseEmBranco() {
            livro5.setVolume("2");
            livro5.setSinopse("Uma sinopse");
            service.atualizar(1L, 5L, dados(null, null, null, null, "  ", null, "   "));
            LivroNode salvo = capturarSalvo();
            assertAll(
                    () -> assertNull(salvo.getVolume()),
                    () -> assertNull(salvo.getSinopse()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("L12 (derivado): lista de autores vazia remove todos os autores")
        void autoresVazio() {
            service.atualizar(1L, 5L, dados(null, List.of(), null, null, null, null, null));
            assertThat(capturarSalvo().getAutores()).isEmpty();
        }

        @Test
        @Tag("derivado")
        @DisplayName("L11 (derivado): no atualizar, autor existente no banco é reaproveitado")
        void autorDoBancoNoAtualizar() {
            AutorNode doBanco = new AutorNode("Autor Z");
            doBanco.setId(8L);
            when(autorRepository.findFirstByNomeIgnoreCase("Autor Z")).thenReturn(Optional.of(doBanco));
            service.atualizar(1L, 5L, dados(null, List.of("Autor Z"), null, null, null, null, null));
            assertThat(capturarSalvo().getAutores()).singleElement().isSameAs(doBanco);
        }

        @Test
        @Tag("derivado")
        @DisplayName("L1 (derivado): atualizar livro inexistente lança NoSuchElementException")
        void livroInexistente() {
            assertThrows(NoSuchElementException.class,
                    () -> service.atualizar(1L, 99L, dados("Novo", null, null, null, null, null, null)));
        }
    }

    @Nested
    @DisplayName("Remover: órfãos")
    class RemoverOrfaos {

        @Test
        @DisplayName("E23 (L9, L13): apaga o livro e depois limpa o autor 7 e a tag romance")
        void apagaELimpaOrfaos() {
            AutorNode autor = new AutorNode("Autor X");
            autor.setId(7L);
            livro5.setAutores(new ArrayList<>(List.of(autor)));
            livro5.setTags(new java.util.HashSet<>(Set.of(new TagNode("romance"))));

            service.remover(1L, 5L);

            InOrder ordem = inOrder(livroRepository, autorRepository, tagRepository);
            ordem.verify(livroRepository).delete(livro5);
            ordem.verify(autorRepository).apagarOrfaos(argThat(ids -> ids.size() == 1 && ids.contains(7L)));
            ordem.verify(tagRepository).apagarOrfas(argThat(nomes -> nomes.size() == 1 && nomes.contains("romance")));
        }

        @Test
        @Tag("derivado")
        @DisplayName("L13 (derivado): livro sem autores nem tags não chama a limpeza de órfãos")
        void semAutoresNemTags() {
            service.remover(1L, 5L);
            assertAll(
                    () -> verify(autorRepository, never()).apagarOrfaos(anyCollection()),
                    () -> verify(tagRepository, never()).apagarOrfas(anyCollection()));
        }
    }
}
