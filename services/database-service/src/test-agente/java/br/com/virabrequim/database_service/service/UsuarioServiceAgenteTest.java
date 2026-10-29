package br.com.virabrequim.database_service.service;

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
}
