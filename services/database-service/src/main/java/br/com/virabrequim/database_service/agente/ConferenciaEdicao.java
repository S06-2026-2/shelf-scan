package br.com.virabrequim.database_service.agente;

import br.com.virabrequim.database_service.agente.catalogo.Edicao;
import br.com.virabrequim.database_service.domain.Textos;
import br.com.virabrequim.database_service.domain.node.AutorNode;
import br.com.virabrequim.database_service.domain.node.LivroNode;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Confere se a edição do catálogo é mesmo do livro cadastrado. Qual é o livro já foi decidido antes (OCR e usuário):
// aqui a edição é que tem que bater com ele. Na dúvida, recusa: melhor deixar vazio do que gravar dado de outro livro.
final class ConferenciaEdicao {

    private static final Set<String> PALAVRAS_VAZIAS = Set.of("o", "a", "os", "as", "um", "uma", "de", "do", "da",
            "dos", "das", "e", "em", "the", "of");
    private static final Set<String> GENERICAS_DE_EDITORA = Set.of("editora", "editorial", "edicoes", "edicao",
            "livros", "livraria", "grupo", "companhia", "cia", "ltda", "sa", "publicacoes");
    private static final Pattern VOLUME_NO_TITULO = Pattern.compile("\\b(?:volume|vol|tomo|v)\\.?\\s*\\d+");
    private static final Pattern NUMERO = Pattern.compile("\\d+");

    // Grafias do mesmo nome ("Dostoiévski" e "Dostoyevsky") diferem em poucas letras
    private static final int MAX_DIFERENCAS_NO_NOME = 2;
    private static final int MIN_LETRAS_PARA_TOLERAR = 7;

    private ConferenciaEdicao() {
    }

    // As edições são do mesmo ISBN, uma por catálogo. Nenhuma pode contradizer o livro, e cada dado que o livro tem
    // precisa ser confirmado por pelo menos uma: um catálogo incompleto (ex.: sem autores) não impede, se outro confirma
    static boolean mesmoLivro(LivroNode livro, List<Edicao> edicoes) {
        return !edicoes.isEmpty()
                && edicoes.stream().allMatch(edicao -> mesmoTitulo(livro, edicao))
                && confirmado(edicoes, edicao -> autor(nomes(livro), edicao.autores()))
                && confirmado(edicoes, edicao -> volume(livro, edicao))
                && confirmado(edicoes, edicao -> editora(livro.getEditora(), edicao.editora()));
    }

    private enum Conferencia { CONFIRMA, CONTRADIZ, NAO_INFORMA }

    private static boolean confirmado(List<Edicao> edicoes, Function<Edicao, Conferencia> conferencia) {
        List<Conferencia> resultados = edicoes.stream().map(conferencia).toList();
        return resultados.contains(Conferencia.CONFIRMA) && !resultados.contains(Conferencia.CONTRADIZ);
    }

    private static Conferencia confereSe(boolean condicao) {
        return condicao ? Conferencia.CONFIRMA : Conferencia.CONTRADIZ;
    }

    // O título da edição tem que COMEÇAR pelo do livro: "Duna: edição especial" é "Duna", "Messias de Duna" não é
    private static boolean mesmoTitulo(LivroNode livro, Edicao edicao) {
        List<String> doLivro = palavras(livro.getTitulo(), PALAVRAS_VAZIAS);
        List<String> daEdicao = palavras(tituloCompleto(edicao), PALAVRAS_VAZIAS);
        return !doLivro.isEmpty() && daEdicao.size() >= doLivro.size()
                && daEdicao.subList(0, doLivro.size()).equals(doLivro);
    }

    // Pelo menos um autor em comum (pelo sobrenome). Livro sem autor cadastrado não tem o que conferir
    private static Conferencia autor(List<String> doLivro, List<String> daEdicao) {
        if (doLivro.isEmpty()) {
            return Conferencia.CONFIRMA;
        }
        if (daEdicao.isEmpty()) {
            return Conferencia.NAO_INFORMA;
        }
        return confereSe(doLivro.stream()
                .anyMatch(nome -> daEdicao.stream().anyMatch(outro -> mesmaPessoa(nome, outro))));
    }

    // O sobrenome (última palavra) de um dos nomes aparece no outro: "Dostoiévski" e "Fiódor Dostoiévski",
    // "Dostoiévski, Fiódor" e "Fiódor Dostoiévski"
    static boolean mesmaPessoa(String nome, String outro) {
        List<String> a = palavras(nome, PALAVRAS_VAZIAS);
        List<String> b = palavras(outro, PALAVRAS_VAZIAS);
        return !a.isEmpty() && !b.isEmpty()
                && (contem(b, a.getLast()) || contem(a, b.getLast()));
    }

    // Com volume no livro, a edição tem que trazer o número dele; sem volume, não pode ser a de um volume da série
    private static Conferencia volume(LivroNode livro, Edicao edicao) {
        String titulo = Textos.chave(tituloCompleto(edicao));
        boolean edicaoTemVolume = VOLUME_NO_TITULO.matcher(titulo).find();
        Set<Integer> doLivro = numeros(livro.getVolume());
        if (doLivro.isEmpty()) {
            return confereSe(!edicaoTemVolume || Textos.opcional(livro.getVolume()) != null);
        }
        if (numeros(titulo).containsAll(doLivro)) {
            return Conferencia.CONFIRMA;
        }
        return edicaoTemVolume ? Conferencia.CONTRADIZ : Conferencia.NAO_INFORMA;
    }

    // Editora diferente é outra edição, com outro ISBN e outras páginas
    private static Conferencia editora(String doLivro, String daEdicao) {
        List<String> a = palavras(doLivro, GENERICAS_DE_EDITORA, PALAVRAS_VAZIAS);
        List<String> b = palavras(daEdicao, GENERICAS_DE_EDITORA, PALAVRAS_VAZIAS);
        if (a.isEmpty()) {
            return Conferencia.CONFIRMA;
        }
        return b.isEmpty() ? Conferencia.NAO_INFORMA : confereSe(a.stream().anyMatch(b::contains));
    }

    private static List<String> nomes(LivroNode livro) {
        return livro.getAutores() == null ? List.of()
                : livro.getAutores().stream().map(AutorNode::getNome).toList();
    }

    private static String tituloCompleto(Edicao edicao) {
        String titulo = edicao.titulo() == null ? "" : edicao.titulo();
        return edicao.subtitulo() == null ? titulo : titulo + " " + edicao.subtitulo();
    }

    @SafeVarargs
    private static List<String> palavras(String texto, Set<String>... ignoradas) {
        if (texto == null) {
            return List.of();
        }
        return Arrays.stream(Textos.chave(texto).split("[^\\p{L}\\p{N}]+"))
                .filter(palavra -> !palavra.isEmpty())
                .filter(palavra -> Arrays.stream(ignoradas).noneMatch(conjunto -> conjunto.contains(palavra)))
                .toList();
    }

    private static Set<Integer> numeros(String texto) {
        Set<Integer> numeros = new HashSet<>();
        Matcher matcher = NUMERO.matcher(texto == null ? "" : texto);
        while (matcher.find()) {
            if (matcher.group().length() <= 4) {
                numeros.add(Integer.valueOf(matcher.group()));
            }
        }
        return numeros;
    }

    private static boolean contem(Collection<String> palavras, String procurada) {
        return palavras.stream().anyMatch(palavra -> palavra.equals(procurada)
                || Math.min(palavra.length(), procurada.length()) >= MIN_LETRAS_PARA_TOLERAR
                && diferencas(palavra, procurada) <= MAX_DIFERENCAS_NO_NOME);
    }

    // Distância de Levenshtein: quantas letras trocar, pôr ou tirar para ir de uma palavra à outra
    private static int diferencas(String a, String b) {
        int[] anterior = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            anterior[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int[] atual = new int[b.length() + 1];
            atual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int troca = anterior[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                atual[j] = Math.min(troca, Math.min(anterior[j], atual[j - 1]) + 1);
            }
            anterior = atual;
        }
        return anterior[b.length()];
    }
}
