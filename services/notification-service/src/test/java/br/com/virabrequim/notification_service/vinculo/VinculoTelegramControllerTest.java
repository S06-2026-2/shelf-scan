package br.com.virabrequim.notification_service.vinculo;

import br.com.virabrequim.notification_service.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VinculoTelegramController.class)
@Import(CorsConfig.class)
@TestPropertySource(properties = "app.cors.origens-permitidas=http://localhost:5173")
class VinculoTelegramControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConexaoTelegramService conexaoTelegramService;

    @Test
    void post_devolveOLinkDeConexao() throws Exception {
        when(conexaoTelegramService.gerarLink("42")).thenReturn("https://t.me/bot?start=ABC");

        mockMvc.perform(post("/telegram/vinculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"42\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.link").value("https://t.me/bot?start=ABC"));
    }

    @Test
    void post_semUsuarioId_devolve400() throws Exception {
        mockMvc.perform(post("/telegram/vinculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_comBotNaoConfigurado_devolve503() throws Exception {
        when(conexaoTelegramService.gerarLink("42")).thenThrow(new TelegramNaoConfiguradoException());

        mockMvc.perform(post("/telegram/vinculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"42\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void get_informaSeEstaConectado() throws Exception {
        when(conexaoTelegramService.estaConectado("42")).thenReturn(true);

        mockMvc.perform(get("/telegram/vinculos/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conectado").value(true));
    }

    @Test
    void delete_desconectaEDevolve204() throws Exception {
        mockMvc.perform(delete("/telegram/vinculos/42"))
                .andExpect(status().isNoContent());

        verify(conexaoTelegramService).desconectar("42");
    }

    @Test
    void cors_liberaAOrigemDoFront() throws Exception {
        mockMvc.perform(options("/telegram/vinculos")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void cors_naoLiberaOutraOrigem() throws Exception {
        mockMvc.perform(options("/telegram/vinculos")
                        .header("Origin", "http://site-qualquer.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
