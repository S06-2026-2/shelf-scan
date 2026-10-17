package br.com.virabrequim.notification_service.vinculo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface VinculoTelegramRepository extends JpaRepository<VinculoTelegram, String> {

    /**
     * Marca o código como usado somente se ele ainda não foi usado e não expirou.
     * É uma única instrução no banco, então dois usos simultâneos do mesmo código
     * nunca passam os dois: quem chega depois recebe 0.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update VinculoTelegram v
               set v.usadoEm = :agora
             where v.codigo = :codigo
               and v.usadoEm is null
               and v.expiraEm > :agora
            """)
    int marcarComoUsado(@Param("codigo") String codigo, @Param("agora") LocalDateTime agora);
}
