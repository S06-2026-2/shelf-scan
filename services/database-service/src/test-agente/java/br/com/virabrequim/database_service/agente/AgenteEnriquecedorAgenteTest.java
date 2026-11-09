package br.com.virabrequim.database_service.agente;

import br.com.virabrequim.database_service.domain.node.AutorNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgenteEnriquecedorAgenteTest {

    private ClienteLlm clienteLlm;
    private AgenteEnriquecedor agente;
    private LivroNode livro;

    @BeforeEach
    void setUp() {
        clienteLlm = mock(ClienteLlm.class);
        agente = new AgenteEnriquecedor(clienteLlm, new JsonMapper());
        livro = new LivroNode("Memórias do Subsolo");
        livro.setAutores(new ArrayList<>(List.of(new AutorNode("Fiódor Dostoiévski"))));
    }

    private void llmDevolve(String json) {
        when(clienteLlm.gerarJson(anyString(), any())).thenReturn(json);
    }

    private SugestaoEnriquecimento sugerirCom(String json) {
        llmDevolve(json);
        return agente.sugerir(livro, null);
    }

    private String promptRecebido() {
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(clienteLlm, times(1)).gerarJson(prompt.capture(), any());
        return prompt.getValue();
    }

    @Nested
    @DisplayName("Resposta")
    class Resposta {

        @Test
        @DisplayName("E1 (A4–A9): livro identificado devolve todos os campos já tratados")
        void completo() {
            SugestaoEnriquecimento s = sugerirCom("""
                    {"livroIdentificado": true, "sinopse": "Um homem amargurado narra sua vida.",
                     "isbn": "978-85-7326-158-5", "paginas": 152, "autores": ["Fiódor Dostoiévski"],
                     "tags": ["romance", "clássico"]}""");
            assertAll(
                    () -> assertTrue(s.livroIdentificado()),
                    () -> assertEquals("Um homem amargurado narra sua vida.", s.sinopse()),
                    () -> assertEquals("9788573261585", s.isbn()),
                    () -> assertEquals(152, s.paginas()),
                    () -> assertEquals(List.of("Fiódor Dostoiévski"), s.autores()),
                    () -> assertEquals(List.of("romance", "clássico"), s.tags()));
        }

        @Test
        @DisplayName("E2 (A3): livroIdentificado false devolve a sugestão vazia mesmo com campos preenchidos")
        void naoIdentificado() {
            SugestaoEnriquecimento s = sugerirCom("""
                    {"livroIdentificado": false, "sinopse": "Inventada", "isbn": null, "paginas": 100,
                     "autores": [], "tags": ["romance"]}""");
            assertEquals(SugestaoEnriquecimento.vazia(), s);
        }

        @Test
        @DisplayName("E3 (A3): sem livroIdentificado devolve a sugestão vazia")
        void semLivroIdentificado() {
            assertEquals(SugestaoEnriquecimento.vazia(), sugerirCom("{\"sinopse\": \"Algo\", \"paginas\": 100}"));
        }

        @Test
        @DisplayName("E12 (A2): texto que não é JSON lança AgenteIndisponivelException")
        void jsonInvalido() {
            llmDevolve("isso não é json");
            assertThrows(AgenteIndisponivelException.class, () -> agente.sugerir(livro, null));
        }

        @Test
        @DisplayName("E13 (A2): exceção do ClienteLlm não é capturada")
        void excecaoDoCliente() {
            AgenteIndisponivelException erro = new AgenteIndisponivelException("sem chave");
            when(clienteLlm.gerarJson(anyString(), any())).thenThrow(erro);
            assertSame(erro, assertThrows(AgenteIndisponivelException.class, () -> agente.sugerir(livro, null)));
        }

        @Test
        @Tag("derivado")
        @DisplayName("A2 (derivado): outra RuntimeException do ClienteLlm também não é capturada")
        void outraExcecaoDoCliente() {
            IllegalStateException erro = new IllegalStateException("falha");
            when(clienteLlm.gerarJson(anyString(), any())).thenThrow(erro);
            assertSame(erro, assertThrows(IllegalStateException.class, () -> agente.sugerir(livro, null)));
        }
    }

    @Nested
    @DisplayName("Travas")
    class Travas {

        @Test
        @DisplayName("E4 (A5, A6, A7): sinopse longa, ISBN com dígito errado e 0 páginas viram null")
        void travasInvalidas() {
            SugestaoEnriquecimento s = sugerirCom("{\"livroIdentificado\": true, \"sinopse\": \"" + "a".repeat(1501)
                    + "\", \"isbn\": \"9788535914848\", \"paginas\": 0}");
            assertAll(
                    () -> assertNull(s.sinopse()),
                    () -> assertNull(s.isbn()),
                    () -> assertNull(s.paginas()));
        }

        @Test
        @DisplayName("E5 (A5): sinopse sem espaços nas pontas")
        void sinopseComEspacos() {
            assertEquals("Texto curto.",
                    sugerirCom("{\"livroIdentificado\": true, \"sinopse\": \"  Texto curto.  \"}").sinopse());
        }

        @Test
        @DisplayName("E5 (A5): sinopse em branco vira null")
        void sinopseEmBranco() {
            assertNull(sugerirCom("{\"livroIdentificado\": true, \"sinopse\": \"   \"}").sinopse());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A5 (derivado): sinopse com exatamente 1500 caracteres é mantida")
        void sinopse1500() {
            String sinopse = "a".repeat(1500);
            assertEquals(sinopse,
                    sugerirCom("{\"livroIdentificado\": true, \"sinopse\": \"" + sinopse + "\"}").sinopse());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A5 (derivado): 1500 caracteres mais espaços nas pontas é mantida sem os espaços")
        void sinopse1500ComEspacos() {
            String sinopse = "a".repeat(1500);
            assertEquals(sinopse,
                    sugerirCom("{\"livroIdentificado\": true, \"sinopse\": \"  " + sinopse + "  \"}").sinopse());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A5 (derivado): sinopse que não é texto vira null")
        void sinopseNaoTexto() {
            assertNull(sugerirCom("{\"livroIdentificado\": true, \"sinopse\": 123}").sinopse());
        }

        @Test
        @DisplayName("E6 (A7): 10000 páginas é aceito")
        void paginas10000() {
            assertEquals(10000, sugerirCom("{\"livroIdentificado\": true, \"paginas\": 10000}").paginas());
        }

        @Test
        @DisplayName("E6 (A7): 10001 páginas vira null")
        void paginas10001() {
            assertNull(sugerirCom("{\"livroIdentificado\": true, \"paginas\": 10001}").paginas());
        }

        @Test
        @DisplayName("E6 (A7): páginas como texto vira null")
        void paginasTexto() {
            assertNull(sugerirCom("{\"livroIdentificado\": true, \"paginas\": \"152\"}").paginas());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A7 (derivado): 1 página (limite inferior) é aceito")
        void umaPagina() {
            assertEquals(1, sugerirCom("{\"livroIdentificado\": true, \"paginas\": 1}").paginas());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A7 (derivado): páginas decimal vira null")
        void paginasDecimal() {
            assertNull(sugerirCom("{\"livroIdentificado\": true, \"paginas\": 152.5}").paginas());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A7 (derivado): páginas null vira null")
        void paginasNull() {
            assertNull(sugerirCom("{\"livroIdentificado\": true, \"paginas\": null}").paginas());
        }

        @Test
        @DisplayName("E7 (A8, A9): tags repetidas sem acento/caixa e em branco são descartadas; no máximo 5")
        void tags() {
            SugestaoEnriquecimento s = sugerirCom("""
                    {"livroIdentificado": true, "tags": ["Ficção", "ficcao", " ", "drama", "romance", "clássico",
                     "russo", "filosofia"]}""");
            assertEquals(List.of("Ficção", "drama", "romance", "clássico", "russo"), s.tags());
        }

        @Test
        @DisplayName("E8 (A8): autores com espaços normalizados, sem repetidos e sem itens que não são texto")
        void autores() {
            SugestaoEnriquecimento s = sugerirCom(
                    "{\"livroIdentificado\": true, \"autores\": [\"  Ana   Maria \", \"ANA MARIA\", 42]}");
            assertEquals(List.of("Ana Maria"), s.autores());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A8 (derivado): tag com 40 caracteres é mantida e com 41 é descartada")
        void limiteTag() {
            String t40 = "b".repeat(40);
            String t41 = "c".repeat(41);
            SugestaoEnriquecimento s = sugerirCom(
                    "{\"livroIdentificado\": true, \"tags\": [\"" + t40 + "\", \"" + t41 + "\"]}");
            assertEquals(List.of(t40), s.tags());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A8 (derivado): autor com 120 caracteres é mantido e com 121 é descartado")
        void limiteAutor() {
            String a120 = "b".repeat(120);
            String a121 = "c".repeat(121);
            SugestaoEnriquecimento s = sugerirCom(
                    "{\"livroIdentificado\": true, \"autores\": [\"" + a120 + "\", \"" + a121 + "\"]}");
            assertEquals(List.of(a120), s.autores());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A8 (derivado): tag em branco e tag que não é texto são descartadas")
        void tagsInvalidas() {
            SugestaoEnriquecimento s = sugerirCom("{\"livroIdentificado\": true, \"tags\": [\"\", true, \"drama\"]}");
            assertEquals(List.of("drama"), s.tags());
        }

        @Test
        @DisplayName("E9 (A9): sem autores e tags, as listas são vazias")
        void semListas() {
            SugestaoEnriquecimento s = sugerirCom("{\"livroIdentificado\": true}");
            assertAll(
                    () -> assertThat(s.autores()).isNotNull().isEmpty(),
                    () -> assertThat(s.tags()).isNotNull().isEmpty());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A9 (derivado): autores e tags null viram listas vazias")
        void listasNull() {
            SugestaoEnriquecimento s = sugerirCom("{\"livroIdentificado\": true, \"autores\": null, \"tags\": null}");
            assertAll(
                    () -> assertThat(s.autores()).isNotNull().isEmpty(),
                    () -> assertThat(s.tags()).isNotNull().isEmpty());
        }

        @Test
        @Tag("derivado")
        @DisplayName("A9 (derivado): 6 autores válidos ficam os 5 primeiros")
        void seisAutores() {
            SugestaoEnriquecimento s = sugerirCom(
                    "{\"livroIdentificado\": true, \"autores\": [\"A\", \"B\", \"C\", \"D\", \"E\", \"F\"]}");
            assertEquals(List.of("A", "B", "C", "D", "E"), s.autores());
        }
    }

    @Nested
    @DisplayName("Prompt")
    class Prompt {

        @BeforeEach
        void naoIdentificado() {
            llmDevolve("{\"livroIdentificado\": false}");
        }

        @Test
        @DisplayName("E10 (A1): o prompt contém título, autor e texto bruto")
        void promptComTextoBruto() {
            agente.sugerir(livro, "MEMORIAS DO SUBSOLO editora 34");
            String prompt = promptRecebido();
            assertAll(
                    () -> assertThat(prompt).contains("Memórias do Subsolo"),
                    () -> assertThat(prompt).contains("Fiódor Dostoiévski"),
                    () -> assertThat(prompt).contains("MEMORIAS DO SUBSOLO editora 34"));
        }

        @Test
        @DisplayName("E11 (A1): texto bruto em branco não entra no prompt")
        void textoBrutoEmBranco() {
            agente.sugerir(livro, "   ");
            assertThat(promptRecebido()).doesNotContain("textoDoOcr");
        }

        @Test
        @Tag("derivado")
        @DisplayName("A1 (derivado): o prompt contém o volume e o ISBN cadastrados")
        void promptComVolumeEIsbn() {
            livro.setVolume("Volume-Dois");
            livro.setIsbn("9788573261585");
            agente.sugerir(livro, null);
            String prompt = promptRecebido();
            assertAll(
                    () -> assertThat(prompt).contains("Volume-Dois"),
                    () -> assertThat(prompt).contains("9788573261585"));
        }

        @Test
        @Tag("derivado")
        @DisplayName("A1 (derivado): texto bruto com mais de 2000 caracteres é cortado nos 2000 primeiros")
        void textoBrutoCortado() {
            String inicio = "q".repeat(2000);
            agente.sugerir(livro, inicio + "ZWK");
            String prompt = promptRecebido();
            assertAll(
                    () -> assertThat(prompt).contains(inicio),
                    () -> assertThat(prompt).doesNotContain("qZWK"),
                    () -> assertThat(prompt).doesNotContain("ZWK"));
        }

        @Test
        @Tag("derivado")
        @DisplayName("A1 (derivado): o texto bruto entra sem os espaços das pontas, e o corte vale depois deles")
        void textoBrutoSemEspacos() {
            String texto = "w".repeat(2000);
            agente.sugerir(livro, "     " + texto + "     ");
            String prompt = promptRecebido();
            assertAll(
                    () -> assertThat(prompt).contains(texto),
                    () -> assertThat(prompt).doesNotContain(" " + texto),
                    () -> assertThat(prompt).doesNotContain(texto + " "));
        }
    }

    @Test
    @DisplayName("E14 (A10): modelo() repassa o modelo do ClienteLlm")
    void modelo() {
        when(clienteLlm.modelo()).thenReturn("falso");
        assertEquals("falso", agente.modelo());
    }
}
