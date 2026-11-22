package br.com.virabrequim.database_service.agente;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// Servidor HTTP local que devolve as respostas enfileiradas e guarda o que recebeu
public class ServidorFalso implements AutoCloseable {

    private record Resposta(int status, String corpo) {
    }

    public final List<String> caminhos = new ArrayList<>();
    public final List<String> corpos = new ArrayList<>();
    public final List<String> chaves = new ArrayList<>();
    private final Deque<Resposta> respostas = new ArrayDeque<>();
    private final HttpServer servidor;

    public ServidorFalso() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        servidor.createContext("/", troca -> {
            caminhos.add(URLDecoder.decode(troca.getRequestURI().getRawPath() + "?"
                    + troca.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            corpos.add(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            chaves.add(troca.getRequestHeaders().getFirst("x-goog-api-key"));
            Resposta resposta = respostas.isEmpty() ? new Resposta(500, "{}") : respostas.poll();
            byte[] bytes = resposta.corpo().getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            troca.sendResponseHeaders(resposta.status(), bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
    }

    public ServidorFalso responde(int status, String corpo) {
        respostas.add(new Resposta(status, corpo));
        return this;
    }

    public String url() {
        return "http://localhost:" + servidor.getAddress().getPort();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
