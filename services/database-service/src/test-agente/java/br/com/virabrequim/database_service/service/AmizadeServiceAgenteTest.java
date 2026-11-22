package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.EstadoAmizade;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.dto.Amigo;
import br.com.virabrequim.database_service.dto.AmizadeResposta;
import br.com.virabrequim.database_service.dto.SolicitacaoAmizade;
import br.com.virabrequim.database_service.repository.AmizadeRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AmizadeServiceAgenteTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 10);

    private AmizadeRepository amizadeRepository;
    private UsuarioService usuarioService;
    private AmizadeService service;

    private static UsuarioNode usuario(long id, String nome) {
        UsuarioNode u = new UsuarioNode();
        u.setId(id);
        u.setNome(nome);
        return u;
    }

    @BeforeEach
    void setUp() {
        amizadeRepository = mock(AmizadeRepository.class);
        usuarioService = mock(UsuarioService.class);
        Calendario calendario = mock(Calendario.class);
        when(calendario.diaHoje()).thenReturn(HOJE);
        service = new AmizadeService(amizadeRepository, usuarioService, calendario);

        when(usuarioService.buscarUsuarioComum(1L)).thenReturn(usuario(1L, "Ana"));
        when(usuarioService.buscarUsuarioComum(2L)).thenReturn(usuario(2L, "Bruno"));
        when(usuarioService.buscarUsuarioComum(4L)).thenThrow(new NoSuchElementException("biblioteca"));
    }

    private void verificarNadaGravado() {
        assertAll(
                () -> verify(amizadeRepository, never()).criarSolicitacao(anyLong(), anyLong(), any()),
                () -> verify(amizadeRepository, never()).trocarSolicitacaoPorAmizade(anyLong(), anyLong(), any()),
                () -> verify(amizadeRepository, never()).apagarSolicitacao(anyLong(), anyLong()),
                () -> verify(amizadeRepository, never()).apagarAmizade(anyLong(), anyLong()));
    }

    @Nested
    @DisplayName("Solicitar")
    class Solicitar {

        @Test
        @DisplayName("E1 (Z5): sem amizade nem pedidos cria a solicitação e responde PENDENTE com Bruno")
        void pendente() {
            AmizadeResposta resposta = service.solicitar(1L, 2L);
            assertAll(
                    () -> verify(amizadeRepository).criarSolicitacao(1L, 2L, HOJE),
                    () -> assertEquals(EstadoAmizade.PENDENTE, resposta.estado()),
                    () -> assertEquals(2L, resposta.usuario().id()),
                    () -> assertEquals("Bruno", resposta.usuario().nome()));
        }

        @Test
        @DisplayName("E2 (Z4): a outra pessoa já tinha pedido, vira AMIGOS na hora e não cria solicitação")
        void amigosNaHora() {
            when(amizadeRepository.trocarSolicitacaoPorAmizade(1L, 2L, HOJE)).thenReturn(true);
            AmizadeResposta resposta = service.solicitar(1L, 2L);
            assertAll(
                    () -> assertEquals(EstadoAmizade.AMIGOS, resposta.estado()),
                    () -> assertEquals(2L, resposta.usuario().id()),
                    () -> assertEquals("Bruno", resposta.usuario().nome()),
                    () -> verify(amizadeRepository, never()).criarSolicitacao(anyLong(), anyLong(), any()));
        }

        @Test
        @DisplayName("E3 (Z2): pedir amizade para si mesmo lança IllegalArgumentException e nada é gravado")
        void paraSiMesmo() {
            assertThrows(IllegalArgumentException.class, () -> service.solicitar(1L, 1L));
            verificarNadaGravado();
        }

        @Test
        @DisplayName("E4 (Z3): já são amigos lança IllegalStateException e não cria nem troca solicitação")
        void jaAmigos() {
            when(amizadeRepository.saoAmigos(1L, 2L)).thenReturn(true);
            assertThrows(IllegalStateException.class, () -> service.solicitar(1L, 2L));
            assertAll(
                    () -> verify(amizadeRepository, never()).criarSolicitacao(anyLong(), anyLong(), any()),
                    () -> verify(amizadeRepository, never()).trocarSolicitacaoPorAmizade(anyLong(), anyLong(), any()));
        }

        @Test
        @DisplayName("E5 (Z3): pedido já enviado lança IllegalStateException e nada é gravado")
        void jaEnviado() {
            when(amizadeRepository.existeSolicitacao(1L, 2L)).thenReturn(true);
            assertThrows(IllegalStateException.class, () -> service.solicitar(1L, 2L));
            verificarNadaGravado();
        }

        @Test
        @DisplayName("E6 (Z1): destinatário biblioteca lança NoSuchElementException e nada é gravado")
        void destinatarioBiblioteca() {
            assertThrows(NoSuchElementException.class, () -> service.solicitar(1L, 4L));
            verificarNadaGravado();
        }

        @Test
        @DisplayName("E6 (Z1): solicitante biblioteca lança NoSuchElementException e nada é gravado")
        void solicitanteBiblioteca() {
            assertThrows(NoSuchElementException.class, () -> service.solicitar(4L, 1L));
            verificarNadaGravado();
        }
    }

    @Nested
    @DisplayName("Aceitar")
    class Aceitar {

        @Test
        @DisplayName("E7 (Z6): quem recebeu aceita, troca o pedido por amizade e responde AMIGOS com Ana")
        void aceita() {
            when(amizadeRepository.existeSolicitacao(1L, 2L)).thenReturn(true);
            AmizadeResposta resposta = service.aceitar(2L, 1L);
            assertAll(
                    () -> verify(amizadeRepository).trocarSolicitacaoPorAmizade(2L, 1L, HOJE),
                    () -> assertEquals(EstadoAmizade.AMIGOS, resposta.estado()),
                    () -> assertEquals(1L, resposta.usuario().id()),
                    () -> assertEquals("Ana", resposta.usuario().nome()));
        }

        @Test
        @DisplayName("E8 (Z6): quem pediu não aceita o próprio pedido")
        void quemPediuNaoAceita() {
            when(amizadeRepository.existeSolicitacao(1L, 2L)).thenReturn(true);
            assertThrows(NoSuchElementException.class, () -> service.aceitar(1L, 2L));
            verify(amizadeRepository, never()).trocarSolicitacaoPorAmizade(anyLong(), anyLong(), any());
        }

        @Test
        @Tag("derivado")
        @DisplayName("Z1 (derivado): aceitar com biblioteca (usuário ou solicitante) lança NoSuchElementException e nada é gravado")
        void aceitarComBiblioteca() {
            when(amizadeRepository.existeSolicitacao(anyLong(), anyLong())).thenReturn(true);
            assertAll(
                    () -> assertThrows(NoSuchElementException.class, () -> service.aceitar(1L, 4L)),
                    () -> assertThrows(NoSuchElementException.class, () -> service.aceitar(4L, 1L)));
            verificarNadaGravado();
        }
    }

    @Nested
    @DisplayName("Apagar solicitação e desfazer amizade")
    class Apagar {

        @Test
        @DisplayName("E9 (Z7): apagar solicitação existente não lança nada")
        void apagaSolicitacao() {
            when(amizadeRepository.apagarSolicitacao(2L, 1L)).thenReturn(true);
            assertDoesNotThrow(() -> service.apagarSolicitacao(2L, 1L));
        }

        @Test
        @DisplayName("E9 (Z7): apagar solicitação inexistente lança NoSuchElementException")
        void apagaSolicitacaoInexistente() {
            when(amizadeRepository.apagarSolicitacao(2L, 1L)).thenReturn(false);
            assertThrows(NoSuchElementException.class, () -> service.apagarSolicitacao(2L, 1L));
        }

        @Test
        @DisplayName("E10 (Z9): desfazer amizade existente não lança nada")
        void desfaz() {
            when(amizadeRepository.apagarAmizade(2L, 1L)).thenReturn(true);
            assertDoesNotThrow(() -> service.desfazer(2L, 1L));
        }

        @Test
        @DisplayName("E10 (Z9): desfazer quando não eram amigos lança NoSuchElementException")
        void desfazInexistente() {
            when(amizadeRepository.apagarAmizade(2L, 1L)).thenReturn(false);
            assertThrows(NoSuchElementException.class, () -> service.desfazer(2L, 1L));
        }

        @Test
        @Tag("derivado")
        @DisplayName("Z1 (derivado): apagarSolicitacao com usuário biblioteca lança NoSuchElementException e nada é gravado")
        void apagarSolicitacaoBiblioteca() {
            when(amizadeRepository.apagarSolicitacao(anyLong(), anyLong())).thenReturn(true);
            assertThrows(NoSuchElementException.class, () -> service.apagarSolicitacao(4L, 1L));
            verificarNadaGravado();
        }

        @Test
        @Tag("derivado")
        @DisplayName("Z1 (derivado): desfazer com usuário biblioteca lança NoSuchElementException e nada é gravado")
        void desfazerBiblioteca() {
            when(amizadeRepository.apagarAmizade(anyLong(), anyLong())).thenReturn(true);
            assertThrows(NoSuchElementException.class, () -> service.desfazer(4L, 1L));
            verificarNadaGravado();
        }
    }

    @Nested
    @DisplayName("Listagens")
    class Listagens {

        @Test
        @DisplayName("E11 (Z8): listarAmigos devolve o resultado do repositório")
        void amigos() {
            List<Amigo> amigos = List.of(new Amigo(2L, "Bruno", LocalDate.of(2026, 2, 1), 0));
            when(amizadeRepository.listarAmigos(1L)).thenReturn(amigos);
            assertEquals(amigos, service.listarAmigos(1L));
        }

        @Test
        @DisplayName("E11 (Z8): listarRecebidas e listarEnviadas devolvem o resultado do repositório")
        void solicitacoes() {
            List<SolicitacaoAmizade> recebidas =
                    List.of(new SolicitacaoAmizade(2L, "Bruno", LocalDate.of(2026, 3, 1), 0));
            List<SolicitacaoAmizade> enviadas =
                    List.of(new SolicitacaoAmizade(2L, "Bruno", LocalDate.of(2026, 3, 2), 0));
            when(amizadeRepository.listarRecebidas(1L)).thenReturn(recebidas);
            when(amizadeRepository.listarEnviadas(1L)).thenReturn(enviadas);
            assertAll(
                    () -> assertEquals(recebidas, service.listarRecebidas(1L)),
                    () -> assertEquals(enviadas, service.listarEnviadas(1L)));
        }
    }
}
