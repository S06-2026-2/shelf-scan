package br.com.virabrequim.database_service.agente.catalogo;

import br.com.virabrequim.database_service.agente.ServidorFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogoLivrosTest {

    private static final String ISBN = "9788535914849";

    private final ServidorFalso servidor = new ServidorFalso();

    @AfterEach
    void fechar() {
        servidor.close();
    }

    private CatalogoLivros catalogo() {
        return new CatalogoLivros(new PropriedadesCatalogo(servidor.url(), "chave-google", servidor.url(),
                Duration.ofSeconds(5)), new JsonMapper());
    }

    @Test
    @DisplayName("Google Books: monta a consulta, usa a chave no cabeçalho e ignora itens sem ISBN")
    void buscar() {
        servidor.responde(200, """
                {"items": [
                  {"saleInfo": {"isEbook": true}, "volumeInfo": {"title": "1984", "authors": ["George Orwell"], "publisher": "Companhia das Letras",
                    "pageCount": 416, "description": "Distopia.",
                    "industryIdentifiers": [{"type": "ISBN_10", "identifier": "8535914846"},
                                            {"type": "ISBN_13", "identifier": "9788535914849"}]}},
                  {"volumeInfo": {"title": "1984", "pageCount": 0,
                    "industryIdentifiers": [{"type": "OTHER", "identifier": "UOM:123"}]}}]}
                """);

        List<Edicao> edicoes = catalogo().buscar("O \"1984\"", "Orwell & Cia", null);

        assertThat(edicoes).containsExactly(new Edicao(ISBN, "1984", null, List.of("George Orwell"),
                "Companhia das Letras", 416, "Distopia.", true, CatalogoLivros.GOOGLE_BOOKS));
        assertThat(servidor.caminhos.getFirst()).startsWith("/volumes?q=intitle:\"O 1984\" inauthor:\"Orwell & Cia\"&")
                .endsWith("&langRestrict=pt");
        assertThat(servidor.chaves).containsExactly("chave-google");
    }

    @Test
    @DisplayName("Google Books: sem resultado em português, busca em qualquer idioma")
    void qualquerIdioma() {
        servidor.responde(200, "{\"totalItems\": 0}").responde(200, "{\"totalItems\": 0}");

        assertThat(catalogo().buscar("Título", null, null)).isEmpty();

        assertThat(servidor.caminhos).hasSize(2);
        assertThat(servidor.caminhos.getLast()).doesNotContain("langRestrict").doesNotContain("inauthor");
    }

    @Test
    @DisplayName("Google Books: com editora, filtra por ela; sem resultado, busca sem a editora")
    void editora() {
        servidor.responde(200, "{\"totalItems\": 0}").responde(200, "{\"items\": [{\"volumeInfo\": {\"title\": \"1984\", "
                + "\"industryIdentifiers\": [{\"identifier\": \"9788535914849\"}]}}]}");

        assertThat(catalogo().buscar("1984", null, "Editora 34")).hasSize(1);

        assertThat(servidor.caminhos.getFirst()).contains("inpublisher:\"Editora 34\"");
        assertThat(servidor.caminhos.getLast()).doesNotContain("inpublisher");
    }

    @Test
    @DisplayName("BrasilAPI: lê a edição; páginas 0 e campos null viram ausentes")
    void porIsbn() {
        servidor.responde(200, """
                {"isbn": "9788535914849", "title": "1984", "subtitle": null, "authors": ["George Orwell"],
                 "publisher": "Companhia das Letras", "synopsis": null, "page_count": 0}
                """);

        assertThat(catalogo().porIsbn("85-359-1484-6")).contains(new Edicao(ISBN, "1984", null,
                List.of("George Orwell"), "Companhia das Letras", null, null, null, CatalogoLivros.BRASIL_API));
        assertThat(servidor.caminhos.getFirst()).startsWith("/isbn/v1/" + ISBN);
    }

    @Test
    @DisplayName("BrasilAPI: ISBN inexistente (404) ou inválido devolve vazio")
    void naoEncontrado() {
        servidor.responde(404, "{}");

        assertThat(catalogo().porIsbn(ISBN)).isEmpty();
        assertThat(catalogo().porIsbn("123")).isEmpty();
        assertThat(servidor.caminhos).hasSize(1);
    }

    @Test
    @DisplayName("Catálogo com erro ou resposta ilegível vira CatalogoIndisponivelException")
    void indisponivel() {
        servidor.responde(500, "{}").responde(429, "{}").responde(200, "não é json");

        assertThatThrownBy(() -> catalogo().porIsbn(ISBN)).isInstanceOf(CatalogoIndisponivelException.class);
        assertThatThrownBy(() -> catalogo().buscar("x", null, null)).isInstanceOf(CatalogoIndisponivelException.class);
        assertThatThrownBy(() -> catalogo().porIsbn(ISBN)).isInstanceOf(CatalogoIndisponivelException.class);
    }
}
