package br.com.virabrequim.database_service.service;

import br.com.virabrequim.database_service.domain.node.BibliotecaNode;
import br.com.virabrequim.database_service.domain.node.CidadeNode;
import br.com.virabrequim.database_service.domain.node.EstadoNode;
import br.com.virabrequim.database_service.dto.AtualizacaoBiblioteca;
import br.com.virabrequim.database_service.dto.Endereco;
import br.com.virabrequim.database_service.repository.BibliotecaRepository;
import br.com.virabrequim.database_service.repository.CidadeRepository;
import br.com.virabrequim.database_service.repository.EstadoRepository;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BibliotecaServiceAgenteTest {

    private static final int SANTA_RITA = 3159605;

    private BibliotecaRepository bibliotecaRepository;
    private UsuarioRepository usuarioRepository;
    private CidadeRepository cidadeRepository;
    private EstadoRepository estadoRepository;
    private BibliotecaService service;

    @BeforeEach
    void setUp() {
        bibliotecaRepository = mock(BibliotecaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        cidadeRepository = mock(CidadeRepository.class);
        estadoRepository = mock(EstadoRepository.class);
        when(bibliotecaRepository.save(any(BibliotecaNode.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new BibliotecaService(bibliotecaRepository, usuarioRepository, cidadeRepository, estadoRepository);
    }

    private BibliotecaNode novaBiblioteca() {
        return new BibliotecaNode("Biblioteca Inatel", "contato@inatel.br");
    }

    private CidadeNode santaRita() {
        return new CidadeNode(SANTA_RITA, "Santa Rita do Sapucaí", new EstadoNode(31, "MG"));
    }

    /** Biblioteca Inatel já cadastrada (id 2), em Santa Rita, aberta das 08:00 às 22:00. */
    private BibliotecaNode inatelCadastrada() {
        BibliotecaNode inatel = novaBiblioteca();
        inatel.setId(2L);
        inatel.setLogradouro("Av. João de Camargo");
        inatel.setNumero("510");
        inatel.setCep("37540-000");
        inatel.setCidade(santaRita());
        inatel.setHorarioAbertura("08:00");
        inatel.setHorarioFechamento("22:00");
        when(bibliotecaRepository.findById(2L)).thenReturn(Optional.of(inatel));
        return inatel;
    }

    private AtualizacaoBiblioteca atualizacao(String nome, String email, Endereco endereco,
                                              String abertura, String fechamento) {
        return new AtualizacaoBiblioteca(nome, email, endereco, abertura, fechamento);
    }

    private BibliotecaNode capturarSalva() {
        ArgumentCaptor<BibliotecaNode> captor = ArgumentCaptor.forClass(BibliotecaNode.class);
        verify(bibliotecaRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Criar")
    class Criar {

        @Test
        @DisplayName("E1 (B1, B2): salva a biblioteca e retorna o objeto do save")
        void cria() {
            BibliotecaNode biblioteca = novaBiblioteca();
            BibliotecaNode retornadaPeloSave = novaBiblioteca();
            when(usuarioRepository.existsByEmail("contato@inatel.br")).thenReturn(false);
            when(bibliotecaRepository.save(any(BibliotecaNode.class))).thenReturn(retornadaPeloSave);

            BibliotecaNode retornada = service.criar(biblioteca);

            assertAll(
                    () -> assertSame(biblioteca, capturarSalva()),
                    () -> assertSame(retornadaPeloSave, retornada));
        }

        @Test
        @DisplayName("E2 (B1): email já existente lança IllegalStateException e não salva")
        void emailRepetido() {
            when(usuarioRepository.existsByEmail("contato@inatel.br")).thenReturn(true);
            assertThrows(IllegalStateException.class, () -> service.criar(novaBiblioteca()));
            verify(bibliotecaRepository, never()).save(any());
        }

        @Test
        @DisplayName("E3 (B3): cidade já cadastrada é a do banco e o estado não é consultado")
        void cidadeDoBanco() {
            CidadeNode cidadeDoBanco = santaRita();
            when(cidadeRepository.findById(SANTA_RITA)).thenReturn(Optional.of(cidadeDoBanco));
            BibliotecaNode biblioteca = novaBiblioteca();
            biblioteca.setCidade(new CidadeNode(SANTA_RITA, "SRS", new EstadoNode(31, "MG")));

            service.criar(biblioteca);

            BibliotecaNode salva = capturarSalva();
            assertAll(
                    () -> assertSame(cidadeDoBanco, salva.getCidade()),
                    () -> assertEquals("Santa Rita do Sapucaí", salva.getCidade().getNome()),
                    () -> verify(estadoRepository, never()).findById(anyInt()));
        }

        @Test
        @DisplayName("E4 (B3): cidade nova usa o estado do banco")
        void cidadeNovaComEstadoDoBanco() {
            EstadoNode mgDoBanco = new EstadoNode(31, "MG");
            when(cidadeRepository.findById(SANTA_RITA)).thenReturn(Optional.empty());
            when(estadoRepository.findById(31)).thenReturn(Optional.of(mgDoBanco));
            CidadeNode informada = santaRita();
            BibliotecaNode biblioteca = novaBiblioteca();
            biblioteca.setCidade(informada);

            service.criar(biblioteca);

            BibliotecaNode salva = capturarSalva();
            assertAll(
                    () -> assertSame(informada, salva.getCidade()),
                    () -> assertSame(mgDoBanco, salva.getCidade().getEstado()));
        }

        @Test
        @DisplayName("E5 (B3): cidade e estado novos são os informados")
        void cidadeEEstadoNovos() {
            when(cidadeRepository.findById(SANTA_RITA)).thenReturn(Optional.empty());
            when(estadoRepository.findById(31)).thenReturn(Optional.empty());
            CidadeNode informada = santaRita();
            EstadoNode estadoInformado = informada.getEstado();
            BibliotecaNode biblioteca = novaBiblioteca();
            biblioteca.setCidade(informada);

            service.criar(biblioteca);

            BibliotecaNode salva = capturarSalva();
            assertAll(
                    () -> assertSame(informada, salva.getCidade()),
                    () -> assertSame(estadoInformado, salva.getCidade().getEstado()));
        }

        @Test
        @DisplayName("B3 (derivado): biblioteca sem cidade continua sem cidade e não consulta cidade nem estado")
        @Tag("derivado")
        void semCidade() {
            service.criar(novaBiblioteca());

            BibliotecaNode salva = capturarSalva();
            assertAll(
                    () -> assertNull(salva.getCidade()),
                    () -> verify(cidadeRepository, never()).findById(anyInt()),
                    () -> verify(estadoRepository, never()).findById(anyInt()));
        }
    }

    @Nested
    @DisplayName("Buscar")
    class Buscar {

        @Test
        @DisplayName("E6 (B4): buscar retorna a biblioteca encontrada")
        void encontrada() {
            BibliotecaNode inatel = inatelCadastrada();
            assertSame(inatel, service.buscar(2L));
        }

        @Test
        @DisplayName("E7 (B4): buscar inexistente lança NoSuchElementException")
        void buscarInexistente() {
            when(bibliotecaRepository.findById(1L)).thenReturn(Optional.empty());
            assertThrows(NoSuchElementException.class, () -> service.buscar(1L));
            verify(bibliotecaRepository, never()).save(any());
        }

        @Test
        @DisplayName("E7 (B4): atualizar inexistente lança NoSuchElementException e não salva")
        void atualizarInexistente() {
            when(bibliotecaRepository.findById(1L)).thenReturn(Optional.empty());
            AtualizacaoBiblioteca dados = atualizacao("Nova", null, null, null, null);
            assertThrows(NoSuchElementException.class, () -> service.atualizar(1L, dados));
            verify(bibliotecaRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Atualizar")
    class Atualizar {

        @Test
        @DisplayName("E8 (B5, B8): só horário de fechamento muda")
        void soFechamento() {
            BibliotecaNode inatel = inatelCadastrada();
            CidadeNode cidadeAntes = inatel.getCidade();

            service.atualizar(2L, atualizacao(null, null, null, null, "18:30"));

            BibliotecaNode salva = capturarSalva();
            assertAll(
                    () -> assertEquals("18:30", salva.getHorarioFechamento()),
                    () -> assertEquals("Biblioteca Inatel", salva.getNome()),
                    () -> assertEquals("contato@inatel.br", salva.getEmail()),
                    () -> assertEquals("Av. João de Camargo", salva.getLogradouro()),
                    () -> assertEquals("510", salva.getNumero()),
                    () -> assertEquals("37540-000", salva.getCep()),
                    () -> assertSame(cidadeAntes, salva.getCidade()),
                    () -> assertEquals("08:00", salva.getHorarioAbertura()));
        }

        @Test
        @DisplayName("E9 (B6): email de outro usuário lança IllegalStateException e não salva")
        void emailRepetido() {
            inatelCadastrada();
            when(usuarioRepository.existsByEmail("ana@email.com")).thenReturn(true);
            AtualizacaoBiblioteca dados = atualizacao(null, "ANA@email.com", null, null, null);
            assertThrows(IllegalStateException.class, () -> service.atualizar(2L, dados));
            verify(bibliotecaRepository, never()).save(any());
        }

        @Test
        @DisplayName("E10 (B6): mesmo email (após normalizar) não é conflito e não consulta existsByEmail")
        void mesmoEmail() {
            inatelCadastrada();
            service.atualizar(2L, atualizacao(null, "Contato@Inatel.br", null, null, null));
            verify(usuarioRepository, never()).existsByEmail(anyString());
        }

        @Test
        @DisplayName("E11 (B7): endereço é substituído inteiro, inclusive número null")
        void substituiEndereco() {
            inatelCadastrada();
            when(cidadeRepository.findById(3152501)).thenReturn(Optional.empty());
            Endereco endereco = new Endereco("Av. Dr. Lisboa", null, "37550-000", 3152501, "Pouso Alegre", "MG");

            service.atualizar(2L, atualizacao(null, null, endereco, null, null));

            BibliotecaNode salva = capturarSalva();
            assertAll(
                    () -> assertEquals("Av. Dr. Lisboa", salva.getLogradouro()),
                    () -> assertNull(salva.getNumero()),
                    () -> assertEquals("37550-000", salva.getCep()),
                    () -> assertEquals(3152501, salva.getCidade().getCodigoIbge()),
                    () -> assertEquals("Pouso Alegre", salva.getCidade().getNome()));
        }

        @Test
        @DisplayName("E12 (B8): horário inválido lança IllegalArgumentException e não salva")
        void horarioInvalido() {
            inatelCadastrada();
            AtualizacaoBiblioteca dados = atualizacao(null, null, null, "25:00", null);
            assertThrows(IllegalArgumentException.class, () -> service.atualizar(2L, dados));
            verify(bibliotecaRepository, never()).save(any());
        }

        @Test
        @DisplayName("B5 (derivado): todos os campos null salva sem mudanças e retorna o objeto do save")
        @Tag("derivado")
        void todosNull() {
            BibliotecaNode inatel = inatelCadastrada();
            BibliotecaNode retornadaPeloSave = novaBiblioteca();
            when(bibliotecaRepository.save(any(BibliotecaNode.class))).thenReturn(retornadaPeloSave);

            BibliotecaNode retornada = service.atualizar(2L, atualizacao(null, null, null, null, null));

            BibliotecaNode salva = capturarSalva();
            assertAll(
                    () -> assertSame(inatel, salva),
                    () -> assertEquals("Biblioteca Inatel", salva.getNome()),
                    () -> assertEquals("contato@inatel.br", salva.getEmail()),
                    () -> assertEquals("510", salva.getNumero()),
                    () -> assertEquals("08:00", salva.getHorarioAbertura()),
                    () -> assertEquals("22:00", salva.getHorarioFechamento()),
                    () -> assertSame(retornadaPeloSave, retornada));
        }

        @Test
        @DisplayName("B6 (derivado): nome novo é aplicado sem espaços nas pontas")
        @Tag("derivado")
        void nomeNovo() {
            inatelCadastrada();
            service.atualizar(2L, atualizacao("  Biblioteca Central ", null, null, null, null));
            assertEquals("Biblioteca Central", capturarSalva().getNome());
        }

        @Test
        @DisplayName("B6 (derivado): nome em branco lança IllegalArgumentException e não salva")
        @Tag("derivado")
        void nomeEmBranco() {
            inatelCadastrada();
            AtualizacaoBiblioteca dados = atualizacao("   ", null, null, null, null);
            assertThrows(IllegalArgumentException.class, () -> service.atualizar(2L, dados));
            verify(bibliotecaRepository, never()).save(any());
        }

        @Test
        @DisplayName("B6 (derivado): email novo e livre é salvo normalizado")
        @Tag("derivado")
        void emailNovoLivre() {
            inatelCadastrada();
            when(usuarioRepository.existsByEmail("novo@inatel.br")).thenReturn(false);
            service.atualizar(2L, atualizacao(null, " NOVO@Inatel.br ", null, null, null));
            assertEquals("novo@inatel.br", capturarSalva().getEmail());
        }

        @Test
        @DisplayName("B7 (derivado): endereço com cidade já cadastrada usa a cidade do banco")
        @Tag("derivado")
        void enderecoComCidadeDoBanco() {
            inatelCadastrada();
            CidadeNode pousoAlegreDoBanco = new CidadeNode(3152501, "Pouso Alegre", new EstadoNode(31, "MG"));
            when(cidadeRepository.findById(3152501)).thenReturn(Optional.of(pousoAlegreDoBanco));
            Endereco endereco = new Endereco("Av. Dr. Lisboa", "10", "37550-000", 3152501, "P. Alegre", "MG");

            service.atualizar(2L, atualizacao(null, null, endereco, null, null));

            assertSame(pousoAlegreDoBanco, capturarSalva().getCidade());
        }

        @Test
        @DisplayName("B8 (derivado): horário de abertura válido é alterado e atualizar retorna o objeto do save")
        @Tag("derivado")
        void aberturaValida() {
            inatelCadastrada();
            BibliotecaNode retornadaPeloSave = novaBiblioteca();
            when(bibliotecaRepository.save(any(BibliotecaNode.class))).thenReturn(retornadaPeloSave);

            BibliotecaNode retornada = service.atualizar(2L, atualizacao(null, null, null, "07:00", null));

            assertAll(
                    () -> assertEquals("07:00", capturarSalva().getHorarioAbertura()),
                    () -> assertSame(retornadaPeloSave, retornada));
        }
    }

    @Nested
    @DisplayName("Apagar")
    class Apagar {

        @Test
        @DisplayName("E13 (B9): apagar biblioteca existente chama apagarComEstantesELivros")
        void apaga() {
            when(bibliotecaRepository.existsById(2L)).thenReturn(true);
            service.apagar(2L);
            verify(usuarioRepository).apagarComEstantesELivros(2L);
        }

        @Test
        @DisplayName("E14 (B9): apagar inexistente lança NoSuchElementException e não apaga")
        void inexistente() {
            when(bibliotecaRepository.existsById(1L)).thenReturn(false);
            assertThrows(NoSuchElementException.class, () -> service.apagar(1L));
            verify(usuarioRepository, never()).apagarComEstantesELivros(anyLong());
        }
    }
}
