package br.com.virabrequin.shelf_scan.objetos_bibliteca;

import java.util.Date;
import java.util.List;

public class Livro {    private String isbn;
    private String capa; //vai ser uma foto da capa do livro
    private String lombada; //vai ser uma foto da lombada do livro
    private String nome;
    private float volume;
    private int paginas;
    private Date dataDeAdicao;
    private boolean emprestado = false;
    private Emprestimo emprestimo_atual;
    private List<String> tags;
    private List<String> autores;
    private String sinopse;
    private String contracapa;
    private Estante estante;

}
