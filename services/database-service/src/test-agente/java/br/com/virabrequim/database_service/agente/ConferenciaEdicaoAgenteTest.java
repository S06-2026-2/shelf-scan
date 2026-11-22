package br.com.virabrequim.database_service.agente;

import br.com.virabrequim.database_service.agente.catalogo.Edicao;
import br.com.virabrequim.database_service.domain.node.AutorNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConferenciaEdicaoAgenteTest {

    private static final String ISBN = "9788573261585";

    private static LivroNode livro(String titulo, String volume, String editora, String... autores) {
        LivroNode livro = new LivroNode(titulo);
        livro.setVolume(volume);
        livro.setEditora(editora);
        List<AutorNode> lista = new ArrayList<>();
        for (String autor : autores) {
            lista.add(new AutorNode(autor));
        }
        livro.setAutores(lista);
        return livro;
    }

    private static LivroNode soTitulo(String titulo) {
        return livro(titulo, null, null);
    }

    private static Edicao edicao(String titulo, String subtitulo, List<String> autores, String editora) {
        return new Edicao(ISBN, titulo, subtitulo, autores, editora, null, null, null, "teste");
    }

    private static Edicao titulo(String titulo) {
        return edicao(titulo, null, List.of(), null);
    }

    private static boolean confere(LivroNode livro, Edicao... edicoes) {
        return ConferenciaEdicao.mesmoLivro(livro, List.of(edicoes));
    }

    @Nested
    @DisplayName("Título")
    class Titulo {

        @Test
        @DisplayName("E1 (F2): título do livro tem que ser o começo do título da edição")
        void comecoDoTitulo() {
            assertAll(
                    () -> assertTrue(confere(soTitulo("O Hobbit"), titulo("Hobbit"))),
                    () -> assertTrue(confere(soTitulo("Duna"), edicao("Duna", "edição especial", List.of(), null))),
                    () -> assertFalse(confere(soTitulo("Duna"), titulo("Messias de Duna"))),
                    () -> assertFalse(confere(soTitulo("Dom Casmurro"), titulo("100 receitas de macarrão"))),
                    () -> assertFalse(confere(soTitulo("Dom Casmurro"), titulo("Dom"))));
        }

        @Test
        @DisplayName("E2 (F2): título com subtítulo confere com título + subtítulo da edição")
        void tituloComSubtitulo() {
            LivroNode livro = soTitulo("O Senhor dos Anéis: A Sociedade do Anel");
            assertAll(
                    () -> assertTrue(confere(livro, edicao("O Senhor dos Anéis", "A Sociedade do Anel", List.of(), null))),
                    () -> assertFalse(confere(livro, edicao("O Senhor dos Anéis", "As Duas Torres", List.of(), null))));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F2 (derivado): maiúsculas e acentos não contam")
        void semAcentosEMaiusculas() {
            assertTrue(confere(soTitulo("MEMÓRIAS DO SUBSOLO"), titulo("memorias do subsolo")));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F2 (derivado): artigo em inglês (the) é ignorado")
        void artigoEmIngles() {
            assertTrue(confere(soTitulo("The Hobbit"), titulo("Hobbit")));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F2 (derivado): pontuação, inclusive no começo do título, não conta")
        void pontuacao() {
            assertTrue(confere(soTitulo("Duna!"), titulo("\"Duna\"")));
        }
    }

    @Nested
    @DisplayName("Autor")
    class Autor {

        @Test
        @DisplayName("E3 (F3): mesma pessoa com grafias diferentes; outro autor ou edição sem autores não conferem")
        void autores() {
            assertAll(
                    () -> assertTrue(ConferenciaEdicao.mesmaPessoa("Fiódor Dostoiévski", "Dostoyevsky, Fyodor")),
                    () -> assertTrue(confere(livro("Memórias do Subsolo", null, null, "Fiódor Dostoiévski"),
                            edicao("Memórias do Subsolo", null, List.of("Dostoyevsky, Fyodor"), null))),
                    () -> assertFalse(ConferenciaEdicao.mesmaPessoa("Machado de Assis", "José de Alencar")),
                    () -> assertFalse(confere(livro("Dom Casmurro", null, null, "Machado de Assis"),
                            edicao("Dom Casmurro", null, List.of("José de Alencar"), null))),
                    () -> assertFalse(confere(livro("Dom Casmurro", null, null, "Machado de Assis"),
                            edicao("Dom Casmurro", null, List.of(), null))));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F3 (derivado): sobrenome de 7 letras com 2 letras diferentes é a mesma pessoa")
        void duasDiferencas() {
            assertTrue(ConferenciaEdicao.mesmaPessoa("Ana Machado", "Ana Mechedo"));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F3 (derivado): sobrenome de 8 letras com 3 letras diferentes não é a mesma pessoa")
        void tresDiferencas() {
            assertFalse(ConferenciaEdicao.mesmaPessoa("José Saramago", "José Saremogu"));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F3 (derivado): sobrenome de 6 letras com 1 letra diferente não é a mesma pessoa")
        void palavraCurta() {
            assertFalse(ConferenciaEdicao.mesmaPessoa("Isaac Asimov", "Isaac Asimav"));
        }
    }

    @Nested
    @DisplayName("Volume")
    class Volume {

        @Test
        @DisplayName("E4 (F4): volume 3 confere com Vol. 03, não com Vol. 13 nem sem volume; sem volume não confere com Vol. 3")
        void volume() {
            LivroNode volume3 = livro("Frieren", "3", null);
            assertAll(
                    () -> assertTrue(confere(volume3, titulo("Frieren Vol. 03"))),
                    () -> assertFalse(confere(volume3, titulo("Frieren Vol. 13"))),
                    () -> assertFalse(confere(volume3, titulo("Frieren"))),
                    () -> assertFalse(confere(soTitulo("Frieren"), titulo("Frieren Vol. 3"))));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F4 (derivado): zeros à esquerda no volume do livro não contam")
        void zeroNoLivro() {
            assertTrue(confere(livro("Frieren", "03", null), titulo("Frieren Vol. 3")));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F4 (derivado): livro sem volume não confere com edição \"Tomo 2\"")
        void tomo() {
            assertFalse(confere(soTitulo("Frieren"), titulo("Frieren Tomo 2")));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F4 (derivado): livro sem volume confere com número no título que não é volume")
        void numeroQueNaoEVolume() {
            assertTrue(confere(soTitulo("Fahrenheit 451"), titulo("Fahrenheit 451")));
        }
    }

    @Nested
    @DisplayName("Editora")
    class Editora {

        private LivroNode comEditora(String editora) {
            return livro("Memórias do Subsolo", null, editora);
        }

        private Edicao daEditora(String editora) {
            return edicao("Memórias do Subsolo", null, List.of(), editora);
        }

        @Test
        @DisplayName("E5 (F5): editoras com palavra em comum fora as genéricas")
        void editora() {
            assertAll(
                    () -> assertTrue(confere(comEditora("Editora 34"), daEditora("34"))),
                    () -> assertFalse(confere(comEditora("Editora 34"), daEditora(null))),
                    () -> assertFalse(confere(comEditora("Editora 34"), daEditora("Companhia das Letras"))),
                    () -> assertTrue(confere(comEditora("Cia. das Letras"), daEditora("Companhia das Letras"))));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F5 (derivado): livro sem editora não é conferido")
        void livroSemEditora() {
            assertTrue(confere(comEditora(null), daEditora("Companhia das Letras")));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F5 (derivado): \"Grupo Record\" confere com \"Editora Record Ltda\"")
        void genericasDosDoisLados() {
            assertTrue(confere(comEditora("Grupo Record"), daEditora("Editora Record Ltda")));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F5 (derivado): só a palavra genérica em comum não confere")
        void soGenericaEmComum() {
            assertFalse(confere(comEditora("Editora Globo"), daEditora("Editora Rocco")));
        }
    }

    @Nested
    @DisplayName("Conjunto de edições")
    class Conjunto {

        @Test
        @DisplayName("E6 (F1): autor e editora confirmados por edições diferentes conferem; cada uma sozinha não")
        void confirmacaoPorEdicoesDiferentes() {
            LivroNode livro = livro("Memórias do Subsolo", null, "Editora 34", "Fiódor Dostoiévski");
            Edicao semEditora = edicao("Memórias do Subsolo", null, List.of("Fiódor Dostoiévski"), null);
            Edicao semAutores = edicao("Memórias do Subsolo", null, List.of(), "Editora 34");
            assertAll(
                    () -> assertTrue(confere(livro, semEditora, semAutores)),
                    () -> assertFalse(confere(livro, semEditora)),
                    () -> assertFalse(confere(livro, semAutores)));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F1 (derivado): lista vazia não confere")
        void listaVazia() {
            assertFalse(ConferenciaEdicao.mesmoLivro(soTitulo("Duna"), List.of()));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F1 (derivado): uma edição com título que não confere derruba o conjunto")
        void tituloDeUmaNaoConfere() {
            assertFalse(confere(soTitulo("Duna"), titulo("Duna"), titulo("Messias de Duna")));
        }

        @Test
        @Tag("derivado")
        @DisplayName("F1 (derivado): autor confirmado por uma edição e contradito por outra não confere")
        void autorContradito() {
            LivroNode livro = livro("Dom Casmurro", null, null, "Machado de Assis");
            assertFalse(confere(livro,
                    edicao("Dom Casmurro", null, List.of("Machado de Assis"), null),
                    edicao("Dom Casmurro", null, List.of("José de Alencar"), null)));
        }
    }
}
