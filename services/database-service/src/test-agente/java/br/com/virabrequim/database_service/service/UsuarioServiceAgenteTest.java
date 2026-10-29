package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.node.BibliotecaNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioServiceAgenteTest {

    private UsuarioRepository usuarioRepository;
    private UsuarioService service;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        when(usuarioRepository.save(any(UsuarioNode.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new UsuarioService(usuarioRepository);
    }

    private UsuarioNode capturarSalvo() {
        ArgumentCaptor<UsuarioNode> captor = ArgumentCaptor.forClass(UsuarioNode.class);
        verify(usuarioRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Criar usuário")
    class Criar {

        @Test
        @DisplayName("E1 (U1, U2, U4, U5): cria usuário com nome e email normalizados e estante padrão")
        void criaUsuario() {
            UsuarioNode retornadoPeloSave = new UsuarioNode();
            when(usuarioRepository.existsByEmail("ana@email.com")).thenReturn(false);
            when(usuarioRepository.save(any(UsuarioNode.class))).thenReturn(retornadoPeloSave);

            UsuarioNode retornado = service.criar("  Ana Souza ", "  Ana@Email.COM ");

            UsuarioNode salvo = capturarSalvo();
            assertAll(
                    () -> assertEquals("Ana Souza", salvo.getNome()),
                    () -> assertEquals("ana@email.com", salvo.getEmail()),
                    () -> assertThat(salvo.getEstantes()).hasSize(1),
                    () -> assertEquals("Minha estante", salvo.getEstantes().get(0).getNome()),
                    () -> assertSame(salvo.getEstantes().get(0), salvo.getEstantePadrao()),
                    () -> assertSame(retornadoPeloSave, retornado));
        }

        @ParameterizedTest(name = "E2: nome [{0}] lança IllegalArgumentException")
        @DisplayName("E2 (U1): nome inválido")
        @CsvSource(value = {"NULL", "''", "'   '"}, nullValues = "NULL")
        void nomeInvalido(String nome) {
            assertThrows(IllegalArgumentException.class, () -> service.criar(nome, "ana@email.com"));
            verify(usuarioRepository, never()).save(any());
        }

        @ParameterizedTest(name = "E3: email [{0}] lança IllegalArgumentException")
        @DisplayName("E3 (U2): email inválido")
        @CsvSource(value = {"NULL", "'  '"}, nullValues = "NULL")
        void emailInvalido(String email) {
            assertThrows(IllegalArgumentException.class, () -> service.criar("Ana", email));
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("E4 (U2, U3): email já existente (após normalizar) lança IllegalStateException")
        void emailRepetido() {
            when(usuarioRepository.existsByEmail("ana@email.com")).thenReturn(true);
            assertThrows(IllegalStateException.class, () -> service.criar("Ana", "ANA@email.com"));
            verify(usuarioRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Buscar")
    class Buscar {

        @Test
        @DisplayName("E7 (U7): buscar retorna o usuário encontrado")
        void encontrado() {
            UsuarioNode ana = new UsuarioNode();
            ana.setId(1L);
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(ana));
            assertSame(ana, service.buscar(1L));
        }

        @Test
        @DisplayName("E8 (U7): usuário inexistente lança NoSuchElementException")
        void inexistente() {
            when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class, () -> service.buscar(99L));
        }

        @Test
        @DisplayName("E9 (U8): buscarDonoDoLivro retorna o dono e não salva nada")
        void donoDoLivro() {
            UsuarioNode ana = new UsuarioNode();
            ana.setId(1L);
            when(usuarioRepository.findDonoDoLivro(5L)).thenReturn(Optional.of(ana));
            assertSame(ana, service.buscarDonoDoLivro(5L));
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("E10 (U8): livro sem dono lança NoSuchElementException")
        void semDono() {
            when(usuarioRepository.findDonoDoLivro(99L)).thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class, () -> service.buscarDonoDoLivro(99L));
        }
    }

    private UsuarioNode anaCadastrada() {
        UsuarioNode ana = new UsuarioNode("Ana", "ana@email.com");
        ana.setId(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(ana));
        return ana;
    }

    private BibliotecaNode bibliotecaCadastrada() {
        BibliotecaNode inatel = new BibliotecaNode("Biblioteca Inatel", "contato@inatel.br");
        inatel.setId(2L);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(inatel));
        return inatel;
    }

    @Nested
    @DisplayName("Criar (complementos)")
    class CriarComplementos {

        @ParameterizedTest(name = "E2: nome [{0}] não consulta existsByEmail")
        @DisplayName("E2 (U1): nome inválido não consulta existsByEmail")
        @CsvSource(value = {"NULL", "''", "'  '"}, nullValues = "NULL")
        void nomeInvalidoNaoConsulta(String nome) {
            assertThrows(IllegalArgumentException.class, () -> service.criar(nome, "ana@email.com"));
            verify(usuarioRepository, never()).existsByEmail(anyString());
        }
    }

    @Nested
    @DisplayName("Buscar usuário comum ou biblioteca")
    class BuscarComplementos {

        @Test
        @DisplayName("E6 (U4): buscar aceita biblioteca")
        void buscarBiblioteca() {
            BibliotecaNode inatel = bibliotecaCadastrada();
            assertSame(inatel, service.buscar(2L));
        }

        @Test
        @DisplayName("E7 (U4): buscarUsuarioComum inexistente lança NoSuchElementException")
        void buscarUsuarioComumInexistente() {
            when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class, () -> service.buscarUsuarioComum(99L));
        }

        @Test
        @DisplayName("E8 (U5): buscarUsuarioComum com biblioteca lança NoSuchElementException indicando /biblioteca/2")
        void buscarUsuarioComumBiblioteca() {
            bibliotecaCadastrada();
            NoSuchElementException e = assertThrows(NoSuchElementException.class,
                    () -> service.buscarUsuarioComum(2L));
            assertThat(e.getMessage()).contains("/biblioteca/2");
        }

        @Test
        @DisplayName("U5 (derivado): buscarUsuarioComum retorna usuário comum")
        @Tag("derivado")
        void buscarUsuarioComumEncontrado() {
            UsuarioNode ana = anaCadastrada();
            assertSame(ana, service.buscarUsuarioComum(1L));
        }
    }

    @Nested
    @DisplayName("Atualizar")
    class Atualizar {

        @Test
        @DisplayName("E9 (U5): atualizar biblioteca lança NoSuchElementException e não salva")
        void atualizarBiblioteca() {
            bibliotecaCadastrada();
            assertThrows(NoSuchElementException.class, () -> service.atualizar(2L, "X", null));
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("E10 (U6, U7): só o nome muda e existsByEmail não é chamado")
        void soNome() {
            anaCadastrada();
            service.atualizar(1L, "  Ana Lima ", null);
            UsuarioNode salvo = capturarSalvo();
            assertAll(
                    () -> assertEquals("Ana Lima", salvo.getNome()),
                    () -> assertEquals("ana@email.com", salvo.getEmail()),
                    () -> verify(usuarioRepository, never()).existsByEmail(anyString()));
        }

        @Test
        @DisplayName("E11 (U6): nome e email null salva sem mudanças")
        void semMudancas() {
            UsuarioNode ana = anaCadastrada();
            service.atualizar(1L, null, null);
            UsuarioNode salvo = capturarSalvo();
            assertAll(
                    () -> assertSame(ana, salvo),
                    () -> assertEquals("Ana", salvo.getNome()),
                    () -> assertEquals("ana@email.com", salvo.getEmail()));
        }

        @Test
        @DisplayName("E12 (U7): nome em branco lança IllegalArgumentException e não salva")
        void nomeEmBranco() {
            anaCadastrada();
            assertThrows(IllegalArgumentException.class, () -> service.atualizar(1L, "   ", null));
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("E13 (U8): email novo é salvo normalizado")
        void emailNovo() {
            anaCadastrada();
            when(usuarioRepository.existsByEmail("novo@email.com")).thenReturn(false);
            service.atualizar(1L, null, " NOVO@Email.com ");
            assertEquals("novo@email.com", capturarSalvo().getEmail());
        }

        @Test
        @DisplayName("E14 (U8): email de outro usuário lança IllegalStateException e não salva")
        void emailRepetido() {
            anaCadastrada();
            when(usuarioRepository.existsByEmail("bruno@email.com")).thenReturn(true);
            assertThrows(IllegalStateException.class, () -> service.atualizar(1L, null, "bruno@email.com"));
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("E15 (U8): mesmo email (após normalizar) não é conflito")
        void mesmoEmail() {
            anaCadastrada();
            service.atualizar(1L, null, "ANA@Email.com");
            assertAll(
                    () -> verify(usuarioRepository, never()).existsByEmail(anyString()),
                    () -> assertEquals("ana@email.com", capturarSalvo().getEmail()));
        }

        @Test
        @DisplayName("U8 (derivado): email repetido é consultado já normalizado")
        @Tag("derivado")
        void emailRepetidoConsultadoNormalizado() {
            anaCadastrada();
            when(usuarioRepository.existsByEmail("bruno@email.com")).thenReturn(true);
            assertThrows(IllegalStateException.class,
                    () -> service.atualizar(1L, null, " BRUNO@Email.com "));
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("U8 (derivado): atualizar retorna o objeto retornado por save")
        @Tag("derivado")
        void retornaObjetoDoSave() {
            anaCadastrada();
            UsuarioNode retornadoPeloSave = new UsuarioNode();
            when(usuarioRepository.save(any(UsuarioNode.class))).thenReturn(retornadoPeloSave);
            assertSame(retornadoPeloSave, service.atualizar(1L, "Ana Lima", null));
        }

        @Test
        @DisplayName("U5 (derivado): atualizar inexistente lança NoSuchElementException e não salva")
        @Tag("derivado")
        void atualizarInexistente() {
            when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class, () -> service.atualizar(99L, "X", null));
            verify(usuarioRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Apagar")
    class Apagar {

        @Test
        @DisplayName("E9 (U5): apagar biblioteca lança NoSuchElementException e não apaga")
        void apagarBiblioteca() {
            bibliotecaCadastrada();
            assertThrows(NoSuchElementException.class, () -> service.apagar(2L));
            verify(usuarioRepository, never()).apagarComEstantesELivros(anyLong());
        }

        @Test
        @DisplayName("E16 (U9): apagar usuário comum chama apagarComEstantesELivros")
        void apaga() {
            anaCadastrada();
            service.apagar(1L);
            verify(usuarioRepository).apagarComEstantesELivros(1L);
        }

        @Test
        @DisplayName("E17 (U5, U9): apagar inexistente lança NoSuchElementException e não apaga")
        void inexistente() {
            when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class, () -> service.apagar(99L));
            verify(usuarioRepository, never()).apagarComEstantesELivros(anyLong());
        }
    }
}
