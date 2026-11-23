package br.com.virabrequim.database_service;

import br.com.virabrequim.database_service.domain.EstadoAmizade;
import br.com.virabrequim.database_service.domain.node.BibliotecaNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import br.com.virabrequim.database_service.domain.node.UsuarioNode;
import br.com.virabrequim.database_service.dto.Amigo;
import br.com.virabrequim.database_service.dto.BibliotecaDoUsuario;
import br.com.virabrequim.database_service.dto.Membro;
import br.com.virabrequim.database_service.dto.SolicitacaoAmizade;
import br.com.virabrequim.database_service.repository.AmizadeRepository;
import br.com.virabrequim.database_service.repository.BibliotecaRepository;
import br.com.virabrequim.database_service.repository.LivroRepository;
import br.com.virabrequim.database_service.repository.UsuarioRepository;
import br.com.virabrequim.database_service.service.AmizadeService;
import br.com.virabrequim.database_service.service.BibliotecaService;
import br.com.virabrequim.database_service.service.EmprestimoService;
import br.com.virabrequim.database_service.service.EstanteService;
import br.com.virabrequim.database_service.service.MembroService;
import br.com.virabrequim.database_service.service.UsuarioService;
import br.com.virabrequim.database_service.tempo.Calendario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.time.LocalDate;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

// Com Neo4j de verdade: as regras de amizade e membro dependem do Cypher, que mocks não exercitam
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RelacoesEntreUsuariosTest {

    @Autowired AmizadeService amizadeService;
    @Autowired MembroService membroService;
    @Autowired BibliotecaService bibliotecaService;
    @Autowired EmprestimoService emprestimoService;
    @Autowired UsuarioService usuarioService;
    @Autowired EstanteService estanteService;
    @Autowired AmizadeRepository amizadeRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired BibliotecaRepository bibliotecaRepository;
    @Autowired LivroRepository livroRepository;
    @Autowired Calendario calendario;
    @Autowired Neo4jClient neo4jClient;

    Long ana;
    Long bruno;
    Long carla;
    Long inatel;

    @BeforeEach
    void prepararBanco() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        ana = usuarioService.criar("Ana", "ana@email.com").getId();
        bruno = usuarioService.criar("Bruno", "bruno@email.com").getId();
        carla = usuarioService.criar("Carla", "carla@email.com").getId();
        inatel = bibliotecaRepository.save(new BibliotecaNode("Biblioteca Inatel", "contato@inatel.br")).getId();
    }

    private void tornarAmigos(Long a, Long b) {
        amizadeService.solicitar(a, b);
        amizadeService.aceitar(b, a);
    }

    private Long livroDe(Long usuarioId) {
        UsuarioNode dono = usuarioRepository.findById(usuarioId).orElseThrow();
        LivroNode livro = new LivroNode("Dom Casmurro");
        livro.setEstante(dono.getEstantePadrao());
        return livroRepository.save(livro).getId();
    }

    private long contar(String tipo) {
        return neo4jClient.query("MATCH ()-[r:" + tipo + "]->() RETURN count(r)")
                .fetchAs(Long.class).one().orElse(-1L);
    }

    private LocalDate amanha() {
        return calendario.diaHoje().plusDays(1);
    }

    // ============================ Amizade ============================
    @Test
    @DisplayName("O pedido fica pendente: recebido de um lado, enviado do outro, e ninguém é amigo ainda")
    void pedidoPendente() {
        assertThat(amizadeService.solicitar(ana, bruno).estado()).isEqualTo(EstadoAmizade.PENDENTE);

        assertThat(amizadeService.listarEnviadas(ana)).extracting(SolicitacaoAmizade::id).containsExactly(bruno);
        assertThat(amizadeService.listarRecebidas(bruno)).extracting(SolicitacaoAmizade::id).containsExactly(ana);
        assertThat(amizadeService.listarRecebidas(ana)).isEmpty();
        assertThat(amizadeService.listarAmigos(ana)).isEmpty();
    }

    @Test
    @DisplayName("Aceitar troca o pedido pela amizade, visível dos dois lados")
    void aceitar() {
        tornarAmigos(ana, bruno);

        assertThat(amizadeService.listarAmigos(ana)).containsExactly(
                new Amigo(bruno, "Bruno", calendario.diaHoje(), 0));
        assertThat(amizadeService.listarAmigos(bruno)).extracting(Amigo::id).containsExactly(ana);
        assertThat(contar("SOLICITOU_AMIZADE")).isZero();
        assertThat(contar("AMIGO")).isEqualTo(1);
    }

    @Test
    @DisplayName("Pedir para quem já tinha pedido vira amizade na hora")
    void pedidoCruzado() {
        amizadeService.solicitar(ana, bruno);

        assertThat(amizadeService.solicitar(bruno, ana).estado()).isEqualTo(EstadoAmizade.AMIGOS);
        assertThat(contar("SOLICITOU_AMIZADE")).isZero();
        assertThat(contar("AMIGO")).isEqualTo(1);
    }

    @Test
    @DisplayName("Dois pedidos simultâneos em sentidos opostos: aceitar limpa os dois e cria uma amizade só")
    void pedidosSimultaneos() {
        amizadeRepository.criarSolicitacao(ana, bruno, calendario.diaHoje());
        amizadeRepository.criarSolicitacao(bruno, ana, calendario.diaHoje());

        amizadeService.aceitar(ana, bruno);

        assertThat(contar("SOLICITOU_AMIZADE")).isZero();
        assertThat(contar("AMIGO")).isEqualTo(1);
    }

    @Test
    @DisplayName("Não pede para si mesmo, duas vezes, para amigo, para biblioteca nem para quem não existe")
    void pedidosInvalidos() {
        amizadeService.solicitar(ana, bruno);
        tornarAmigos(ana, carla);

        assertThatThrownBy(() -> amizadeService.solicitar(ana, ana)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> amizadeService.solicitar(ana, bruno)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> amizadeService.solicitar(carla, ana)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> amizadeService.solicitar(ana, inatel)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> amizadeService.solicitar(inatel, ana)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> amizadeService.solicitar(ana, 999_999L)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("Quem pediu não pode aceitar o próprio pedido")
    void soQuemRecebeuAceita() {
        amizadeService.solicitar(ana, bruno);

        assertThatThrownBy(() -> amizadeService.aceitar(ana, bruno)).isInstanceOf(NoSuchElementException.class);
        assertThat(contar("AMIGO")).isZero();
    }

    @Test
    @DisplayName("Quem recebeu recusa e quem enviou cancela; sem pedido, 404")
    void recusarECancelar() {
        amizadeService.solicitar(ana, bruno);
        amizadeService.apagarSolicitacao(bruno, ana);
        amizadeService.solicitar(ana, bruno);
        amizadeService.apagarSolicitacao(ana, bruno);

        assertThat(contar("SOLICITOU_AMIZADE")).isZero();
        assertThatThrownBy(() -> amizadeService.apagarSolicitacao(ana, bruno))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("Qualquer um dos dois desfaz a amizade, inclusive quem não criou a aresta")
    void desfazer() {
        tornarAmigos(ana, bruno);
        amizadeService.desfazer(ana, bruno);
        tornarAmigos(ana, bruno);
        amizadeService.desfazer(bruno, ana);

        assertThat(contar("AMIGO")).isZero();
        assertThatThrownBy(() -> amizadeService.desfazer(ana, bruno)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("Conta os amigos em comum nos pedidos e na lista de amigos")
    void amigosEmComum() {
        tornarAmigos(ana, carla);
        tornarAmigos(carla, bruno);
        amizadeService.solicitar(bruno, ana);

        assertThat(amizadeService.listarRecebidas(ana)).extracting(SolicitacaoAmizade::amigosEmComum)
                .containsExactly(1);
        amizadeService.aceitar(ana, bruno);
        assertThat(amizadeService.listarAmigos(ana)).extracting(Amigo::nome, Amigo::amigosEmComum)
                .containsExactly(tuple("Bruno", 1),
                        tuple("Carla", 1));
    }

    // ============================ Membro ============================
    @Test
    @DisplayName("Tornar-se membro é direto e repetir não duplica")
    void tornarMembro() {
        membroService.tornarMembro(ana, inatel);
        membroService.tornarMembro(ana, inatel);

        assertThat(membroService.listarMembros(inatel)).containsExactly(
                new Membro(ana, "Ana", calendario.diaHoje()));
        assertThat(membroService.listarBibliotecas(ana)).extracting(BibliotecaDoUsuario::id).containsExactly(inatel);
        assertThat(contar("MEMBRO_DE")).isEqualTo(1);
    }

    @Test
    @DisplayName("Só usuário comum vira membro, e só de biblioteca")
    void membroInvalido() {
        assertThatThrownBy(() -> membroService.tornarMembro(ana, bruno)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> membroService.tornarMembro(inatel, inatel)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> membroService.deixarDeSerMembro(ana, inatel))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("Deixar de ser membro")
    void deixarDeSerMembro() {
        membroService.tornarMembro(ana, inatel);
        membroService.deixarDeSerMembro(ana, inatel);

        assertThat(membroService.listarMembros(inatel)).isEmpty();
    }

    // ============================ Empréstimo ============================
    @Test
    @DisplayName("Sem amizade (ou só com pedido pendente) não empresta; com amizade empresta nos dois sentidos")
    void emprestarEntreAmigos() {
        Long livroDaAna = livroDe(ana);
        Long livroDoBruno = livroDe(bruno);
        amizadeService.solicitar(ana, bruno);
        assertThatThrownBy(() -> emprestimoService.emprestar(ana, livroDaAna, bruno, amanha()))
                .isInstanceOf(IllegalStateException.class);

        amizadeService.aceitar(bruno, ana);

        assertThat(emprestimoService.emprestar(ana, livroDaAna, bruno, amanha()).isEmprestado()).isTrue();
        assertThat(emprestimoService.emprestar(bruno, livroDoBruno, ana, amanha()).isEmprestado()).isTrue();
    }

    @Test
    @DisplayName("Biblioteca empresta só para membro, e o membro não empresta para a biblioteca")
    void emprestarDaBiblioteca() {
        Long livroDaBiblioteca = livroDe(inatel);
        Long livroDaAna = livroDe(ana);
        assertThatThrownBy(() -> emprestimoService.emprestar(inatel, livroDaBiblioteca, ana, amanha()))
                .isInstanceOf(IllegalStateException.class);

        membroService.tornarMembro(ana, inatel);

        assertThat(emprestimoService.emprestar(inatel, livroDaBiblioteca, ana, amanha()).isEmprestado()).isTrue();
        assertThatThrownBy(() -> emprestimoService.emprestar(ana, livroDaAna, inatel, amanha()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Desfazer a amizade não impede a devolução do livro que já estava emprestado")
    void devolverDepoisDeDesfazer() {
        Long livro = livroDe(ana);
        tornarAmigos(ana, bruno);
        emprestimoService.emprestar(ana, livro, bruno, amanha());
        amizadeService.desfazer(ana, bruno);

        assertThat(emprestimoService.devolver(ana, livro, null).isEmprestado()).isFalse();
    }

    // ============================ Listas de usuários e bibliotecas ============================
    @Test
    @DisplayName("Lista usuários comuns e bibliotecas separados, em ordem alfabética sem contar acentos")
    void listarTodos() {
        Long agata = usuarioService.criar("Ágata", "agata@email.com").getId();
        Long alvares = bibliotecaRepository.save(new BibliotecaNode("Álvares", "alvares@email.com")).getId();

        assertThat(usuarioService.listarUsuariosComuns()).extracting(UsuarioNode::getId)
                .containsExactly(agata, ana, bruno, carla);
        assertThat(bibliotecaService.listar()).extracting(BibliotecaNode::getId).containsExactly(alvares, inatel);
    }

    // ============================ Relacionamentos fora do node ============================
    @Test
    @DisplayName("Salvar usuário, estante, biblioteca e livro não apaga amizades, pedidos nem membros")
    void saveNaoApagaRelacoes() {
        tornarAmigos(ana, bruno);
        amizadeService.solicitar(ana, carla);
        membroService.tornarMembro(ana, inatel);
        Long livro = livroDe(ana);

        usuarioService.atualizar(ana, "Ana Souza", null);
        estanteService.criar(ana, "Sala");
        bibliotecaRepository.save(bibliotecaRepository.findById(inatel).orElseThrow());
        emprestimoService.emprestar(ana, livro, bruno, amanha());

        assertThat(contar("AMIGO")).isEqualTo(1);
        assertThat(contar("SOLICITOU_AMIZADE")).isEqualTo(1);
        assertThat(contar("MEMBRO_DE")).isEqualTo(1);
    }

    @Test
    @DisplayName("Apagar o usuário leva junto as relações dele")
    void apagarUsuario() {
        tornarAmigos(ana, bruno);
        amizadeService.solicitar(carla, ana);
        membroService.tornarMembro(ana, inatel);

        usuarioService.apagar(ana);

        assertThat(amizadeService.listarAmigos(bruno)).isEmpty();
        assertThat(amizadeService.listarEnviadas(carla)).isEmpty();
        assertThat(membroService.listarMembros(inatel)).isEmpty();
    }
}
