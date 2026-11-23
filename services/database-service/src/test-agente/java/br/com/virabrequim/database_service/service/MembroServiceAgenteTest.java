package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.node.BibliotecaNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.dto.BibliotecaDoUsuario;
import br.com.virabrequim.database_service.dto.Membro;
import br.com.virabrequim.database_service.repository.MembroRepository;
import br.com.virabrequim.database_service.tempo.Calendario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MembroServiceAgenteTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 10);

    private MembroRepository membroRepository;
    private UsuarioService usuarioService;
    private BibliotecaService bibliotecaService;
    private MembroService service;

    @BeforeEach
    void setUp() {
        membroRepository = mock(MembroRepository.class);
        usuarioService = mock(UsuarioService.class);
        bibliotecaService = mock(BibliotecaService.class);
        Calendario calendario = mock(Calendario.class);
        when(calendario.diaHoje()).thenReturn(HOJE);
        service = new MembroService(membroRepository, usuarioService, bibliotecaService, calendario);

        UsuarioNode ana = new UsuarioNode("Ana", "ana@email.com");
        ana.setId(1L);
        BibliotecaNode inatel = new BibliotecaNode("Biblioteca Inatel", "contato@inatel.br");
        inatel.setId(4L);
        when(usuarioService.buscarUsuarioComum(1L)).thenReturn(ana);
        when(usuarioService.buscarUsuarioComum(4L)).thenThrow(new NoSuchElementException("biblioteca"));
        when(bibliotecaService.buscar(4L)).thenReturn(inatel);
        when(bibliotecaService.buscar(2L)).thenThrow(new NoSuchElementException("não é biblioteca"));
    }

    @Nested
    @DisplayName("Entrar e sair")
    class EntrarESair {

        @Test
        @DisplayName("E1 (C2): tornarMembro adiciona com a data de hoje")
        void tornaMembro() {
            service.tornarMembro(1L, 4L);
            verify(membroRepository).adicionar(1L, 4L, HOJE);
        }

        @Test
        @DisplayName("E2 (C1): biblioteca como usuário lança NoSuchElementException e não adiciona")
        void usuarioBiblioteca() {
            assertThrows(NoSuchElementException.class, () -> service.tornarMembro(4L, 4L));
            verify(membroRepository, never()).adicionar(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("E3 (C1): biblioteca inexistente lança NoSuchElementException e não adiciona")
        void bibliotecaInexistente() {
            assertThrows(NoSuchElementException.class, () -> service.tornarMembro(1L, 2L));
            verify(membroRepository, never()).adicionar(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("E4 (C3): deixar de ser membro quando era membro não lança nada")
        void deixa() {
            when(membroRepository.remover(1L, 4L)).thenReturn(true);
            assertDoesNotThrow(() -> service.deixarDeSerMembro(1L, 4L));
        }

        @Test
        @DisplayName("E4 (C3): deixar de ser membro quando não era membro lança NoSuchElementException")
        void naoEraMembro() {
            when(membroRepository.remover(1L, 4L)).thenReturn(false);
            assertThrows(NoSuchElementException.class, () -> service.deixarDeSerMembro(1L, 4L));
        }

        @Test
        @Tag("derivado")
        @DisplayName("C2 (derivado): tornarMembro repetido não é erro")
        void repetido() {
            assertDoesNotThrow(() -> {
                service.tornarMembro(1L, 4L);
                service.tornarMembro(1L, 4L);
            });
            verify(membroRepository, times(2)).adicionar(1L, 4L, HOJE);
        }

        @Test
        @Tag("derivado")
        @DisplayName("C1 (derivado): deixarDeSerMembro com biblioteca como usuário lança NoSuchElementException e não remove")
        void deixarComUsuarioBiblioteca() {
            when(membroRepository.remover(anyLong(), anyLong())).thenReturn(true);
            assertThrows(NoSuchElementException.class, () -> service.deixarDeSerMembro(4L, 4L));
            verify(membroRepository, never()).remover(anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("Listagens")
    class Listagens {

        @Test
        @DisplayName("E5 (C4): listarBibliotecas devolve o resultado do repositório")
        void bibliotecas() {
            List<BibliotecaDoUsuario> bibliotecas = List.of(
                    new BibliotecaDoUsuario(4L, "Biblioteca Inatel", "Santa Rita do Sapucaí", "MG", HOJE));
            when(membroRepository.listarBibliotecas(1L)).thenReturn(bibliotecas);
            assertEquals(bibliotecas, service.listarBibliotecas(1L));
        }

        @Test
        @DisplayName("E6 (C4): listarMembros devolve o resultado do repositório")
        void membros() {
            List<Membro> membros = List.of(new Membro(1L, "Ana", HOJE));
            when(membroRepository.listarMembros(4L)).thenReturn(membros);
            assertEquals(membros, service.listarMembros(4L));
        }

        @Test
        @Tag("derivado")
        @DisplayName("C1 (derivado): listarMembros de biblioteca inexistente lança NoSuchElementException")
        void membrosBibliotecaInexistente() {
            assertThrows(NoSuchElementException.class, () -> service.listarMembros(2L));
        }

        @Test
        @Tag("derivado")
        @DisplayName("C1 (derivado): listarBibliotecas com biblioteca como usuário lança NoSuchElementException")
        void bibliotecasDeBiblioteca() {
            assertThrows(NoSuchElementException.class, () -> service.listarBibliotecas(4L));
        }
    }
}
