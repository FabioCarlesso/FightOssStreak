package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Aceite do aviso de responsabilidade, registrado com data <em>e versão do texto</em>.
 *
 * <p>Guardar a versão é o que permite reexibir o aviso quando o texto mudar materialmente, em vez
 * de assumir que um aceite antigo cobre um texto novo (docs/06-disclaimer-responsabilidade.md).
 */
@Entity
@Table(name = "disclaimer_acceptance")
public class DisclaimerAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String version;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    protected DisclaimerAcceptance() {
        // JPA
    }

    public DisclaimerAcceptance(Long userId, String version, Instant acceptedAt) {
        this.userId = userId;
        this.version = version;
        this.acceptedAt = acceptedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getVersion() {
        return version;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
