package br.com.virabrequim.notification_service.ai;

import br.com.virabrequim.notification_service.cliente.Cliente;
import br.com.virabrequim.notification_service.cliente.ClienteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Trata a foto de um livro que o usuário manda ao bot: OCR, estante e enriquecimento.
 * Devolve sempre o texto da resposta; quem chama só precisa enviá-lo.
 */
@Component
public class AgenteDeFotos {

    static final String DESATIVADO =
            "Ainda não consigo ler fotos por aqui. Tente de novo mais tarde.";

    static final String SEM_VINCULO =
            "Para eu ler suas fotos, conecte sua conta pelo botão \"Conectar Telegram\" no site do Shelf Scan.";

    static final String CONTA_SEM_ESTANTE =
            "Sua conta ainda não está ligada a uma estante, então não consigo guardar o livro.";

    static final String LIMITE_ATINGIDO =
            "Calma! Você mandou fotos demais em pouco tempo. Tente de novo em um minuto.";

    static final String FALHA_NA_LEITURA =
            "Não consegui ler essa foto agora. Tente de novo daqui a pouco.";

    static final String NAO_ESTRUTUROU =
            "Li o texto da foto, mas não consegui separar título e autor. Tente uma foto mais de perto e bem "
                    + "iluminada, ou cadastre o livro pelo site.";

    private static final Map<String, String> ROTULOS_DOS_CAMPOS = Map.of(
            "sinopse", "sinopse", "isbn", "ISBN", "paginas", "páginas",
            "editora", "editora", "autores", "autores", "tags", "gêneros");

    private static final Logger log = LoggerFactory.getLogger(AgenteDeFotos.class);

    private final Optional<ClienteOcr> ocr;
    private final Optional<ClienteBanco> banco;
    private final ClienteService clienteService;
    private final LimiteDeMensagens limite;

    public AgenteDeFotos(
            Optional<ClienteOcr> ocr,
            Optional<ClienteBanco> banco,
            ClienteService clienteService,
            Clock relogio,
            @Value("${ocr.fotos-por-minuto:3}") int fotosPorMinuto) {
        this.ocr = ocr;
        this.banco = banco;
        this.clienteService = clienteService;
        this.limite = new LimiteDeMensagens(fotosPorMinuto, relogio);
    }

    /**
     * @param baixarFoto baixa a foto do Telegram; só é chamada depois de as regras baratas passarem
     */
    public String processar(Long chatId, Supplier<byte[]> baixarFoto) {
        if (ocr.isEmpty() || banco.isEmpty()) {
            return DESATIVADO;
        }

        Optional<Cliente> cliente = clienteService.buscarPorChatId(chatId).filter(Cliente::isAtivo);
        if (cliente.isEmpty()) {
            return SEM_VINCULO;
        }
        Long usuarioId = idDoBanco(cliente.get());
        if (usuarioId == null) {
            return CONTA_SEM_ESTANTE;
        }
        if (!limite.permitir(chatId)) {
            return LIMITE_ATINGIDO;
        }

        ClienteOcr.Leitura leitura;
        try {
            leitura = ocr.get().ler(baixarFoto.get());
        } catch (RuntimeException falha) {
            log.warn("Não foi possível ler a foto do chat {}: {}", chatId, falha.getMessage());
            return FALHA_NA_LEITURA;
        }
        if (!leitura.estruturado()) {
            return NAO_ESTRUTUROU;
        }

        Long livroId;
        try {
            livroId = banco.get().salvar(usuarioId, leitura.titulo(), leitura.autores(), leitura.volume(),
                    leitura.editora());
        } catch (RuntimeException falha) {
            log.warn("Não foi possível guardar o livro do chat {}: {}", chatId, falha.getMessage());
            return "Li o livro \"" + leitura.titulo() + "\", mas não consegui guardá-lo na sua estante agora. "
                    + "Tente de novo daqui a pouco.";
        }

        List<String> campos = completarComIa(usuarioId, livroId, leitura.textoBruto());
        return resposta(leitura, campos);
    }

    // O livro já está salvo: se a IA falhar, o usuário só fica sem os dados extras.
    private List<String> completarComIa(Long usuarioId, Long livroId, String textoBruto) {
        try {
            return banco.get().enriquecer(usuarioId, livroId, textoBruto);
        } catch (RuntimeException falha) {
            log.warn("O enriquecimento do livro {} falhou: {}", livroId, falha.getMessage());
            return List.of();
        }
    }

    // O usuário do bot é o mesmo id do banco (texto numérico); ids de exemplo, como "teste-1", não servem.
    private static Long idDoBanco(Cliente cliente) {
        try {
            return Long.valueOf(cliente.getUsuarioId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String resposta(ClienteOcr.Leitura leitura, List<String> campos) {
        StringBuilder texto = new StringBuilder("Guardei na sua estante: ").append(leitura.titulo());
        if (!leitura.autores().isEmpty()) {
            texto.append(", de ").append(String.join(", ", leitura.autores()));
        }
        if (leitura.editora() != null && !leitura.editora().isBlank()) {
            texto.append(" (").append(leitura.editora()).append(")");
        }
        texto.append(".");
        if (campos.isEmpty()) {
            texto.append(" Se algo estiver errado, corrija no site.");
        } else {
            texto.append(" A IA completou: ")
                    .append(String.join(", ", campos.stream().map(c -> ROTULOS_DOS_CAMPOS.getOrDefault(c, c)).toList()))
                    .append(" (confira no site).");
        }
        return texto.toString();
    }
}
