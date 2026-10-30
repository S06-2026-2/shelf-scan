package br.com.virabrequim.notification_service.notificacao;

import br.com.virabrequim.notification_service.vinculo.TelegramNaoConfiguradoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificacaoController.class)
class NotificacaoControllerTest {

    private static final String CORPO =
            "{\"usuarioId\":\"42\",\"titulo\":\"Dom Casmurro\",\"autor\":\"Machado de Assis\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificacaoService notificacaoService;

    @Test
    void livroSalvo_enviado_devolve200() throws Exception {
        when(notificacaoService.notificarLivroSalvo("42", "Dom Casmurro", "Machado de Assis"))
                .thenReturn(ResultadoNotificacao.ENVIADO);

        mockMvc.perform(post("/notificacoes/livro-salvo").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("ENVIADO"));
    }

    @Test
    void livroSalvo_semVinculo_tambemDevolve200() throws Exception {
        when(notificacaoService.notificarLivroSalvo("42", "Dom Casmurro", "Machado de Assis"))
                .thenReturn(ResultadoNotificacao.SEM_VINCULO);

        mockMvc.perform(post("/notificacoes/livro-salvo").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("SEM_VINCULO"));
    }

    @Test
    void livroSalvo_telegramRecusou_devolve502ParaQuemChamarPoderTentarDeNovo() throws Exception {
        when(notificacaoService.notificarLivroSalvo("42", "Dom Casmurro", "Machado de Assis"))
                .thenReturn(ResultadoNotificacao.FALHA_NO_ENVIO);

        mockMvc.perform(post("/notificacoes/livro-salvo").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.resultado").value("FALHA_NO_ENVIO"));
    }

    @Test
    void livroSalvo_botNaoConfigurado_devolve503() throws Exception {
        when(notificacaoService.notificarLivroSalvo("42", "Dom Casmurro", "Machado de Assis"))
                .thenThrow(new TelegramNaoConfiguradoException("TELEGRAM_BOT_TOKEN"));

        mockMvc.perform(post("/notificacoes/livro-salvo").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void livroSalvo_semTitulo_devolve400() throws Exception {
        mockMvc.perform(post("/notificacoes/livro-salvo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"42\",\"titulo\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void livroSalvo_autorEhOpcional() throws Exception {
        when(notificacaoService.notificarLivroSalvo("42", "Dom Casmurro", null))
                .thenReturn(ResultadoNotificacao.ENVIADO);

        mockMvc.perform(post("/notificacoes/livro-salvo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"42\",\"titulo\":\"Dom Casmurro\"}"))
                .andExpect(status().isOk());
    }
}
