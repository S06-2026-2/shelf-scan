package br.com.virabrequin.shelf_scan.objetos_usuario;

import br.com.virabrequin.shelf_scan.objetos_bibliteca.Livro;

import java.util.Date;
import java.util.List;

public class Usuario {
    private String nome;
    private List<Livro> livros;
    private int id;
    private String telegram_username;
    private int telegram_chat_id;
    private Date criado_em;
    private boolean ativo;
}
