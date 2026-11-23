package br.com.virabrequim.notification_service.ai;

import br.com.virabrequim.notification_service.cliente.Cliente;
import br.com.virabrequim.notification_service.cliente.ClienteService;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgenteDeFotosTest {

    private static final Long CHAT = 123L;
    private static final byte[] FOTO = {9, 9};

    private final ClienteOcr ocr = mock(ClienteOcr.class);
    private final ClienteBanco banco = mock(ClienteBanco.class);
    private final ClienteService clienteService = mock(ClienteService.class);
    // Relógio parado: todas as fotos do teste caem dentro do mesmo minuto.
    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);
    private final AtomicInteger downloads = new AtomicInteger();
    private final Supplier<byte[]> baixarFoto = () -> {
        downloads.incrementAndGet();
        return FOTO;
    };

    private AgenteDeFotos agente(int fotosPorMinuto) {
        return new AgenteDeFotos(Optional.of(ocr), Optional.of(banco), clienteService, relogio, fotosPorMinuto);
    }

    private void vincular(String usuarioId, boolean ativo) {
        Cliente cliente = new Cliente(usuarioId, CHAT, "Ana", "ana");
        cliente.setAtivo(ativo);
        when(clienteService.buscarPorChatId(CHAT)).thenReturn(Optional.of(cliente));
    }

    private static ClienteOcr.Leitura leituraEstruturada() {
        return new ClienteOcr.Leitura(true, "Memórias do Subsolo", List.of("Fiódor Dostoiévski"), null, "Editora 34",
                "MEMORIAS DO SUBSOLO");
    }

    @Test
    void semOcrOuSemBanco_estaDesativado() {
        AgenteDeFotos semOcr = new AgenteDeFotos(Optional.empty(), Optional.of(banco), clienteService, relogio, 3);
        AgenteDeFotos semBanco = new AgenteDeFotos(Optional.of(ocr), Optional.empty(), clienteService, relogio, 3);

        assertEquals(AgenteDeFotos.DESATIVADO, semOcr.processar(CHAT, baixarFoto));
        assertEquals(AgenteDeFotos.DESATIVADO, semBanco.processar(CHAT, baixarFoto));
        verifyNoInteractions(clienteService);
    }

    @Test
    void chatSemVinculo_pedeParaConectar_eNaoBaixaNada() {
        when(clienteService.buscarPorChatId(CHAT)).thenReturn(Optional.empty());

        assertEquals(AgenteDeFotos.SEM_VINCULO, agente(3).processar(CHAT, baixarFoto));

        assertEquals(0, downloads.get());
        verifyNoInteractions(ocr, banco);
    }

    @Test
    void vinculoDesativado_contaComoSemVinculo() {
        vincular("7", false);

        assertEquals(AgenteDeFotos.SEM_VINCULO, agente(3).processar(CHAT, baixarFoto));
    }

    @Test
    void usuarioDeExemploQueNaoEhIdDoBanco_naoGuarda() {
        vincular("teste-1", true);

        assertEquals(AgenteDeFotos.CONTA_SEM_ESTANTE, agente(3).processar(CHAT, baixarFoto));

        assertEquals(0, downloads.get());
        verifyNoInteractions(ocr, banco);
    }

    @Test
    void limiteEstourado_naoBaixaAFotoNemChamaOOcr() {
        vincular("7", true);
        when(ocr.ler(any())).thenReturn(new ClienteOcr.Leitura(false, null, List.of(), null, null, "x"));
        AgenteDeFotos agente = agente(2);

        agente.processar(CHAT, baixarFoto);
        agente.processar(CHAT, baixarFoto);
        String terceira = agente.processar(CHAT, baixarFoto);

        assertEquals(AgenteDeFotos.LIMITE_ATINGIDO, terceira);
        assertEquals(2, downloads.get());
    }

    @Test
    void fotoQueNaoBaixa_respondeFalhaNaLeitura() {
        vincular("7", true);
        Supplier<byte[]> falha = () -> {
            throw new IllegalStateException("sem rede");
        };

        assertEquals(AgenteDeFotos.FALHA_NA_LEITURA, agente(3).processar(CHAT, falha));
        verifyNoInteractions(ocr, banco);
    }

    @Test
    void ocrForaDoAr_respondeFalhaNaLeitura_eNaoSalva() {
        vincular("7", true);
        when(ocr.ler(any())).thenThrow(new IllegalStateException(new SocketTimeoutException("lento")));

        assertEquals(AgenteDeFotos.FALHA_NA_LEITURA, agente(3).processar(CHAT, baixarFoto));

        verifyNoInteractions(banco);
    }

    @Test
    void ocrSoComTextoBruto_naoSalvaNada() {
        vincular("7", true);
        when(ocr.ler(any())).thenReturn(new ClienteOcr.Leitura(false, null, List.of(), null, null, "FIO} DOR"));

        assertEquals(AgenteDeFotos.NAO_ESTRUTUROU, agente(3).processar(CHAT, baixarFoto));

        verifyNoInteractions(banco);
    }

    @Test
    void caminhoFeliz_guardaNaEstanteDoUsuario_eCitaOQueAIaCompletou() {
        vincular("7", true);
        when(ocr.ler(FOTO)).thenReturn(leituraEstruturada());
        when(banco.salvar(eq(7L), eq("Memórias do Subsolo"), eq(List.of("Fiódor Dostoiévski")), any(),
                eq("Editora 34"))).thenReturn(42L);
        when(banco.enriquecer(7L, 42L, "MEMORIAS DO SUBSOLO")).thenReturn(List.of("sinopse", "isbn", "paginas"));

        String resposta = agente(3).processar(CHAT, baixarFoto);

        assertEquals("Guardei na sua estante: Memórias do Subsolo, de Fiódor Dostoiévski (Editora 34). "
                + "A IA completou: sinopse, ISBN, páginas (confira no site).", resposta);
    }

    @Test
    void caminhoFeliz_semCamposNovos_pedeParaCorrigirNoSite() {
        vincular("7", true);
        when(ocr.ler(FOTO)).thenReturn(new ClienteOcr.Leitura(true, "Dom Casmurro", List.of(), null, null, "x"));
        when(banco.salvar(eq(7L), eq("Dom Casmurro"), eq(List.of()), any(), any())).thenReturn(5L);
        when(banco.enriquecer(eq(7L), eq(5L), anyString())).thenReturn(List.of());

        String resposta = agente(3).processar(CHAT, baixarFoto);

        assertEquals("Guardei na sua estante: Dom Casmurro. Se algo estiver errado, corrija no site.", resposta);
    }

    @Test
    void quandoAIaFalha_oLivroContinuaSalvo() {
        vincular("7", true);
        when(ocr.ler(FOTO)).thenReturn(leituraEstruturada());
        when(banco.salvar(eq(7L), anyString(), any(), any(), any())).thenReturn(42L);
        when(banco.enriquecer(eq(7L), eq(42L), any())).thenThrow(new IllegalStateException("503"));

        String resposta = agente(3).processar(CHAT, baixarFoto);

        assertTrue(resposta.startsWith("Guardei na sua estante: Memórias do Subsolo"));
        assertTrue(resposta.endsWith("Se algo estiver errado, corrija no site."));
    }

    @Test
    void quandoOBancoNaoGuarda_avisaComOTituloLido_eNaoPedeAIa() {
        vincular("7", true);
        when(ocr.ler(FOTO)).thenReturn(leituraEstruturada());
        when(banco.salvar(eq(7L), anyString(), any(), any(), any())).thenThrow(new IllegalStateException("fora"));

        String resposta = agente(3).processar(CHAT, baixarFoto);

        assertTrue(resposta.contains("\"Memórias do Subsolo\""));
        assertTrue(resposta.contains("não consegui guardá-lo"));
        verify(banco, never()).enriquecer(any(), any(), any());
    }
}
