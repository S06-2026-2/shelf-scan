package br.com.virabrequim.database_service.agente;

import br.com.virabrequim.database_service.agente.catalogo.Edicao;
import br.com.virabrequim.database_service.domain.node.AutorNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConferenciaEdicaoTest {

    private static boolean confere(String titulo, String autor, String volume, String editora,
            String tituloEdicao, String subtitulo, String autorEdicao, String editoraEdicao) {
        LivroNode livro = new LivroNode(titulo);
        livro.setVolume(volume);
        livro.setEditora(editora);
        if (autor != null) {
            livro.setAutores(List.of(new AutorNode(autor)));
        }
        return ConferenciaEdicao.mesmoLivro(livro, List.of(new Edicao("9788573261585", tituloEdicao, subtitulo,
                autorEdicao == null ? List.of() : List.of(autorEdicao), editoraEdicao, 100, null, null, "teste")));
    }

    @ParameterizedTest
    @DisplayName("Título: a edição tem que começar pelo título do livro")
    @CsvSource(delimiter = '|', value = {
            "Memórias do Subsolo | MEMORIAS DO SUBSOLO      |                         | true",
            "O Hobbit            | Hobbit                   |                         | true",
            "Duna                | Duna                     | edição especial         | true",
            "O Senhor dos Anéis: A Sociedade do Anel | O Senhor dos Anéis | A Sociedade do Anel | true",
            "O Senhor dos Anéis: A Sociedade do Anel | O Senhor dos Anéis | As Duas Torres      | false",
            "Duna                | Messias de Duna          |                         | false",
            "Dom Casmurro        | 100 receitas de macarrão |                         | false",
            "Dom Casmurro        | Dom                      |                         | false"})
    void titulo(String titulo, String tituloEdicao, String subtitulo, boolean esperado) {
        assertThat(confere(titulo, null, null, null, tituloEdicao, subtitulo, null, null)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @DisplayName("Autor: confere pelo sobrenome, tolerando grafias próximas; sem autor em nenhum catálogo, recusa")
    @CsvSource(delimiter = '|', value = {
            "Fiódor Dostoiévski | Dostoyevsky, Fyodor | true",
            "Dostoiévski        | Fiódor Dostoiévski  | true",
            "Machado de Assis   | MACHADO DE ASSIS    | true",
            "Machado de Assis   | José de Alencar     | false",
            "Machado de Assis   |                     | false",
            "                   | José de Alencar     | true"})
    void autor(String autor, String autorEdicao, boolean esperado) {
        assertThat(confere("Livro", autor, null, null, "Livro", null, autorEdicao, null)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @DisplayName("Volume: a edição tem que ser do mesmo volume")
    @CsvSource(delimiter = '|', value = {
            "3     | Frieren Vol. 03 | true",
            "Vol 3 | Frieren 3       | true",
            "3     | Frieren Vol. 13 | false",
            "3     | Frieren         | false",
            "      | Frieren Vol. 3  | false",
            "      | Frieren         | true"})
    void volume(String volume, String tituloEdicao, boolean esperado) {
        assertThat(confere("Frieren", null, volume, null, tituloEdicao, null, null, null)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @DisplayName("Editora: diferente é outra edição; com editora no livro, um catálogo tem que confirmar")
    @CsvSource(delimiter = '|', value = {
            "Editora 34             | 34                     | true",
            "Cia. das Letras        | Companhia das Letras   | true",
            "Editora 34             | Companhia das Letras   | false",
            "                       | Companhia das Letras   | true",
            "Editora 34             |                        | false"})
    void editora(String editora, String editoraEdicao, boolean esperado) {
        assertThat(confere("Livro", null, null, editora, "Livro", null, null, editoraEdicao)).isEqualTo(esperado);
    }
}
