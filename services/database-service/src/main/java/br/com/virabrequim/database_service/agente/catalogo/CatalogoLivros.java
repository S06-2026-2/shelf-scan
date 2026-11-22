package br.com.virabrequim.database_service.agente.catalogo;

import br.com.virabrequim.database_service.agente.Isbn;
import br.com.virabrequim.database_service.domain.Textos;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Catálogos de livros consultados pelo agente:
// - Google Books: busca edições por título e autor (precisa de chave)
// - BrasilAPI: dados de um ISBN, reunindo CBL e outras bases brasileiras (sem chave)
@Component
public class CatalogoLivros {

    public static final String GOOGLE_BOOKS = "Google Books";
    public static final String BRASIL_API = "BrasilAPI";

    private final PropriedadesCatalogo propriedades;
    private final JsonMapper jsonMapper;
    private final RestClient googleBooks;
    private final RestClient brasilApi;

    public CatalogoLivros(PropriedadesCatalogo propriedades, JsonMapper jsonMapper) {
        this.propriedades = propriedades;
        this.jsonMapper = jsonMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(httpClient);
        fabrica.setReadTimeout(propriedades.tempoLimite());
        this.googleBooks = RestClient.builder().baseUrl(propriedades.googleBooksUrl()).requestFactory(fabrica).build();
        this.brasilApi = RestClient.builder().baseUrl(propriedades.brasilApiUrl()).requestFactory(fabrica).build();
    }

    public boolean buscaPorTituloLigada() {
        return propriedades.googleBooksLigado();
    }

    // Da busca mais específica para a mais ampla, até achar algo: com a editora, sem ela, e em qualquer idioma
    public List<Edicao> buscar(String titulo, String autor, String editora) {
        String consulta = "intitle:\"" + semAspas(titulo) + "\"";
        if (Textos.opcional(autor) != null) {
            consulta += " inauthor:\"" + semAspas(autor) + "\"";
        }
        if (Textos.opcional(editora) != null) {
            List<Edicao> daEditora = buscarNoGoogle(consulta + " inpublisher:\"" + semAspas(editora) + "\"", "");
            if (!daEditora.isEmpty()) {
                return daEditora;
            }
        }
        List<Edicao> emPortugues = buscarNoGoogle(consulta, "&langRestrict=pt");
        return emPortugues.isEmpty() ? buscarNoGoogle(consulta, "") : emPortugues;
    }

    public Optional<Edicao> porIsbn(String isbn) {
        String isbn13 = Isbn.como13(isbn);
        if (isbn13 == null) {
            return Optional.empty();
        }
        try {
            String corpo = brasilApi.get().uri("/isbn/v1/{isbn}", isbn13).retrieve().body(String.class);
            JsonNode livro = jsonMapper.readTree(corpo);
            return Optional.of(new Edicao(isbn13, texto(livro.path("title")), texto(livro.path("subtitle")),
                    textos(livro.path("authors")), texto(livro.path("publisher")), inteiro(livro.path("page_count")),
                    // O format da BrasilAPI marca como digital edições impressas: não é usado
                    texto(livro.path("synopsis")), null, BRASIL_API));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest ex) {
            return Optional.empty();
        } catch (RestClientException | JacksonException ex) {
            throw new CatalogoIndisponivelException("A BrasilAPI não respondeu.", ex);
        }
    }

    private List<Edicao> buscarNoGoogle(String consulta, String filtroIdioma) {
        try {
            String corpo = googleBooks.get()
                    .uri("/volumes?q={consulta}&printType=books&maxResults=20&country=BR" + filtroIdioma, consulta)
                    // Chave no cabeçalho, não na URL: não aparece em logs de acesso
                    .header("x-goog-api-key", propriedades.googleBooksApiKey())
                    .retrieve()
                    .body(String.class);
            List<Edicao> edicoes = new ArrayList<>();
            for (JsonNode item : jsonMapper.readTree(corpo).path("items")) {
                JsonNode volume = item.path("volumeInfo");
                String isbn = isbn13(volume.path("industryIdentifiers"));
                if (isbn != null) {
                    edicoes.add(new Edicao(isbn, texto(volume.path("title")), texto(volume.path("subtitle")),
                            textos(volume.path("authors")), texto(volume.path("publisher")),
                            inteiro(volume.path("pageCount")), texto(volume.path("description")),
                            digital(item.path("saleInfo").path("isEbook")), GOOGLE_BOOKS));
                }
            }
            return edicoes;
        } catch (RestClientException | JacksonException ex) {
            throw new CatalogoIndisponivelException("O Google Books não respondeu.", ex);
        }
    }

    private static Boolean digital(JsonNode isEbook) {
        return isEbook.isBoolean() ? isEbook.asBoolean() : null;
    }

    private static String isbn13(JsonNode identificadores) {
        for (JsonNode identificador : identificadores) {
            String isbn = Isbn.como13(texto(identificador.path("identifier")));
            if (isbn != null) {
                return isbn;
            }
        }
        return null;
    }

    private static String semAspas(String valor) {
        return Textos.espacosNormalizados(valor.replace('"', ' '));
    }

    private static String texto(JsonNode no) {
        return no.isString() ? Textos.opcional(no.asString()) : null;
    }

    private static List<String> textos(JsonNode no) {
        List<String> valores = new ArrayList<>();
        for (JsonNode item : no) {
            if (texto(item) != null) {
                valores.add(texto(item));
            }
        }
        return List.copyOf(valores);
    }

    // Os catálogos usam 0 para "não sei"
    private static Integer inteiro(JsonNode no) {
        return no.isIntegralNumber() && no.asInt() > 0 ? no.asInt() : null;
    }
}
