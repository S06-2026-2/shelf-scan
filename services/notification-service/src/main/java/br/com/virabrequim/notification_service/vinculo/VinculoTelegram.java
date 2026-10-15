package br.com.virabrequim.notification_service.vinculo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "vinculo_telegram")
public class VinculoTelegram {

    @Id
    private String codigo;

    @Column(name = "usuario_id", nullable = false)
    private String usuarioId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "usado_em")
    private LocalDateTime usadoEm;

    protected VinculoTelegram() {
        // construtor exigido pelo JPA
    }

    public VinculoTelegram(String codigo, String usuarioId, LocalDateTime criadoEm, LocalDateTime expiraEm) {
        this.codigo = codigo;
        this.usuarioId = usuarioId;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getExpiraEm() {
        return expiraEm;
    }

    public LocalDateTime getUsadoEm() {
        return usadoEm;
    }
}
