package br.com.virabrequim.notification_service.cliente;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", unique = true)
    private String usuarioId;

    @Column(name = "telegram_chat_id", nullable = false, unique = true)
    private Long telegramChatId;

    private String nome;

    @Column(name = "username_telegram")
    private String usernameTelegram;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(nullable = false)
    private boolean ativo = true;

    protected Cliente() {
        // construtor exigido pelo JPA
    }

    public Cliente(String usuarioId, Long telegramChatId, String nome, String usernameTelegram) {
        this.usuarioId = usuarioId;
        this.telegramChatId = telegramChatId;
        this.nome = nome;
        this.usernameTelegram = usernameTelegram;
    }

    @PrePersist
    protected void aoPersistir() {
        this.criadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public Long getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(Long telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getUsernameTelegram() {
        return usernameTelegram;
    }

    public void setUsernameTelegram(String usernameTelegram) {
        this.usernameTelegram = usernameTelegram;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
