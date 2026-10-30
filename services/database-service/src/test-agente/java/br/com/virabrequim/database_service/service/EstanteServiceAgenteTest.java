package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.DestinoLivros;
import br.com.virabrequim.database_service.domain.node.EstanteNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.repository.AutorRepository;
import br.com.virabrequim.database_service.repository.EstanteRepository;
import br.com.virabrequim.database_service.repository.LivroRepository;
import br.com.virabrequim.database_service.repository.TagRepository;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EstanteServiceAgenteTest {

    private UsuarioRepository usuarioRepository;
    private EstanteRepository estanteRepository;
    private LivroRepository livroRepository;
    private EstanteService service;

    private UsuarioNode ana;
    private UsuarioNode bruno;

    private static EstanteNode estante(long id, String nome) {
        return new EstanteNode(id, nome);
    }

    private static UsuarioNode usuario(long id, String nome, EstanteNode padrao, EstanteNode... outras) {
        UsuarioNode u = new UsuarioNode();
        u.setId(id);
        u.setNome(nome);
        List<EstanteNode> estantes = new ArrayList<>();
        estantes.add(padrao);
        estantes.addAll(List.of(outras));
        u.setEstantes(estantes);
        u.setEstantePadrao(padrao);
        return u;
    }

    private static EstanteNode estanteDe(UsuarioNode u, long id) {
        return u.getEstantes().stream().filter(e -> e.getId() == id).findFirst().orElseThrow();
    }

    private static List<Long> ids(List<EstanteNode> estantes) {
        return estantes.stream().map(EstanteNode::getId).toList();
    }

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        estanteRepository = mock(EstanteRepository.class);
        livroRepository = mock(LivroRepository.class);
        service = new EstanteService(usuarioRepository, estanteRepository, livroRepository,
                mock(AutorRepository.class), mock(TagRepository.class));

        ana = usuario(1L, "Ana", estante(10L, "Minha estante"), estante(11L, "Quarto"), estante(12L, "Sala"));
        bruno = usuario(2L, "Bruno", estante(20L, "Minha estante"), estante(21L, "Quarto"));

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(ana));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(bruno));
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(UsuarioNode.class))).thenAnswer(inv -> inv.getArgument(0));
        when(livroRepository.findAllByEstanteId(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("E1 (S2): listar retorna todas as estantes do usuário, incluindo a padrão")
    void listar() {
        assertThat(ids(service.listar(1L))).containsExactlyInAnyOrder(10L, 11L, 12L);
    }

    @Test
    @DisplayName("E2 (S1): usuário inexistente lança NoSuchElementException em todos os métodos")
    void usuarioInexistente() {
        assertAll(
                () -> assertThrows(NoSuchElementException.class, () -> service.listar(99L)),
                () -> assertThrows(NoSuchElementException.class, () -> service.criar(99L, "X")),
                () -> assertThrows(NoSuchElementException.class, () -> service.atualizar(99L, 11L, "X")),
                () -> assertThrows(NoSuchElementException.class, () -> service.apagar(99L, 11L, DestinoLivros.MOVER)));
    }

    @Nested
    @DisplayName("Criar")
    class Criar {

        @Test
        @DisplayName("E3 (S3, S5): cria estante com nome sem espaços nas pontas, que não é a padrão")
        void cria() {
            EstanteNode criada = service.criar(1L, "  Escritório ");
            assertAll(
                    () -> assertThat(ana.getEstantes()).hasSize(4),
                    () -> assertEquals("Escritório", criada.getNome()),
                    () -> assertThat(ana.getEstantes()).containsOnlyOnce(criada),
                    () -> verify(usuarioRepository).save(ana),
                    () -> assertEquals(10L, ana.getEstantePadrao().getId()));
        }

        @ParameterizedTest(name = "E4: nome [{0}] lança IllegalArgumentException")
        @DisplayName("E4 (S3): nome inválido")
        @CsvSource(value = {"NULL", "''", "'  '"}, nullValues = "NULL")
        void nomeInvalido(String nome) {
            assertThrows(IllegalArgumentException.class, () -> service.criar(1L, nome));
        }

        @Test
        @DisplayName("E5 (S4): nome repetido sem diferenciar maiúsculas lança IllegalStateException")
        void nomeRepetido() {
            assertThrows(IllegalStateException.class, () -> service.criar(1L, "QUARTO"));
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("E6 (S4): outro usuário pode ter estante com o mesmo nome")
        void mesmoNomeOutroUsuario() {
            EstanteNode criada = service.criar(2L, "Sala");
            assertEquals("Sala", criada.getNome());
        }

        @Test
        @DisplayName("S4 (caso de borda): criar \"Minha estante\" quando a padrão já se chama assim é nome repetido")
        void nomeDaPadrao() {
            assertThrows(IllegalStateException.class, () -> service.criar(1L, "Minha estante"));
        }
    }

    @Nested
    @DisplayName("Renomear")
    class Renomear {

        @Test
        @DisplayName("E7 (S4, S6): renomear para nome de outra estante do usuário lança IllegalStateException")
        void nomeDeOutraEstante() {
            assertThrows(IllegalStateException.class, () -> service.atualizar(1L, 11L, "Sala"));
        }

        @Test
        @DisplayName("E8 (S4): manter o próprio nome mudando maiúsculas é permitido")
        void proprioNome() {
            service.atualizar(1L, 11L, "quarto");
            assertEquals("quarto", estanteDe(ana, 11L).getNome());
        }

        @Test
        @DisplayName("E9 (S6): estante de outro usuário ou inexistente lança NoSuchElementException")
        void estanteDeOutro() {
            assertAll(
                    () -> assertThrows(NoSuchElementException.class, () -> service.atualizar(1L, 21L, "X")),
                    () -> assertThrows(NoSuchElementException.class, () -> service.atualizar(1L, 999L, "X")));
        }

        @Test
        @DisplayName("E10 (S7): a padrão pode ser renomeada e continua sendo a padrão")
        void renomearPadrao() {
            service.atualizar(1L, 10L, "Estante principal");
            assertAll(
                    () -> assertEquals("Estante principal", estanteDe(ana, 10L).getNome()),
                    () -> assertEquals(10L, ana.getEstantePadrao().getId()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("S3 (derivado): renomear remove os espaços nas pontas")
        void renomearTrim() {
            service.atualizar(1L, 11L, "  Escritório  ");
            assertEquals("Escritório", estanteDe(ana, 11L).getNome());
        }

        @Test
        @Tag("derivado")
        @DisplayName("S3 (derivado): renomear para nome só com espaços lança IllegalArgumentException")
        void renomearEmBranco() {
            assertThrows(IllegalArgumentException.class, () -> service.atualizar(1L, 11L, "  "));
        }
    }

    @Nested
    @DisplayName("Apagar")
    class Apagar {

        @Test
        @DisplayName("E11 (S8): a estante padrão não pode ser apagada e nada é alterado")
        void apagarPadrao() {
            assertThrows(IllegalStateException.class, () -> service.apagar(1L, 10L, DestinoLivros.MOVER));
            assertAll(
                    () -> verify(estanteRepository, never()).delete(any()),
                    () -> verify(livroRepository, never()).saveAll(any()),
                    () -> verify(usuarioRepository, never()).save(any()));
        }

        @Test
        @DisplayName("E12 (S9, S10): livros vão para a padrão e são salvos antes de apagar a estante")
        void apagarComLivros() {
            EstanteNode quarto = estanteDe(ana, 11L);
            LivroNode a = new LivroNode();
            a.setEstante(quarto);
            LivroNode b = new LivroNode();
            b.setEstante(quarto);
            when(livroRepository.findAllByEstanteId(11L)).thenReturn(List.of(a, b));

            service.apagar(1L, 11L, DestinoLivros.MOVER);

            InOrder ordem = inOrder(livroRepository, estanteRepository);
            assertAll(
                    () -> assertEquals(10L, a.getEstante().getId()),
                    () -> assertEquals(10L, b.getEstante().getId()),
                    () -> verify(livroRepository).saveAll(argThat(livros -> {
                        List<LivroNode> lista = StreamSupport.stream(livros.spliterator(), false).toList();
                        return lista.size() == 2 && lista.contains(a) && lista.contains(b);
                    })),
                    () -> {
                        ordem.verify(livroRepository).saveAll(any());
                        ordem.verify(estanteRepository).delete(argThat(e -> e.getId() == 11L));
                    },
                    () -> assertThat(ids(ana.getEstantes())).doesNotContain(11L),
                    () -> verify(usuarioRepository).save(ana),
                    () -> verify(estanteRepository).delete(argThat(e -> e.getId() == 11L)));
        }

        @Test
        @DisplayName("E13 (S9, S10): estante vazia é apagada normalmente")
        void apagarVazia() {
            when(livroRepository.findAllByEstanteId(12L)).thenReturn(List.of());
            service.apagar(1L, 12L, DestinoLivros.MOVER);
            verify(estanteRepository).delete(argThat(e -> e.getId() == 12L));
        }

        @Test
        @DisplayName("E14 (S6): estante de outro usuário lança NoSuchElementException e nada é apagado")
        void apagarDeOutro() {
            assertThrows(NoSuchElementException.class, () -> service.apagar(1L, 21L, DestinoLivros.MOVER));
            verify(estanteRepository, never()).delete(any());
        }

        @Test
        @Tag("derivado")
        @DisplayName("S6 (derivado): apagar estante inexistente lança NoSuchElementException e nada é apagado")
        void apagarInexistente() {
            assertThrows(NoSuchElementException.class, () -> service.apagar(1L, 999L, DestinoLivros.MOVER));
            verify(estanteRepository, never()).delete(any());
        }
    }
}
