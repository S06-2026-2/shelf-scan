package br.com.virabrequim.notification_service.ai;

import java.util.regex.Pattern;

final class LimpezaDeTexto {

    private static final Pattern CITACAO = Pattern.compile("【[^】]*】");
    private static final Pattern TITULO = Pattern.compile("(?m)^[ \\t]{0,3}#{1,6}[ \\t]*");
    private static final Pattern MARCADOR_DE_LISTA = Pattern.compile("(?m)^([ \\t]*)[*+][ \\t]+");
    private static final Pattern LINHAS_EM_EXCESSO = Pattern.compile("\\n{3,}");

    private LimpezaDeTexto() {
    }

    static String limpar(String texto) {
        String limpo = CITACAO.matcher(texto).replaceAll("");
        limpo = TITULO.matcher(limpo).replaceAll("");
        limpo = MARCADOR_DE_LISTA.matcher(limpo).replaceAll("$1- ");
        limpo = limpo.replace("**", "").replace("__", "").replace("*", "").replace("`", "");
        limpo = LINHAS_EM_EXCESSO.matcher(limpo).replaceAll("\n\n");
        return limpo.strip();
    }
}
