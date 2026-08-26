package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A senha de uma identidade, guardada só como hash (#81).
 *
 * <p>Tabela própria, e não uma coluna em {@link UserIdentity}, por duas razões que valem separadas:
 * a linha de identidade continua significando só "quem é", e apagar a credencial não apaga a
 * identidade — quem migrar para login social não perde o histórico da conta.
 *
 * <p>O hash chega com o prefixo do algoritmo ({@code {bcrypt}...}), do {@code
 * DelegatingPasswordEncoder}. É isso que faz trocar de algoritmo depois ser rehash no login, e não
 * migration: um hash sem prefixo não diz por qual função passou, e a troca viraria "todo mundo
 * redefine a senha".
 */
@Entity
@Table(name = "password_credential")
public class PasswordCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "identity_id", nullable = false)
    private Long identityId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PasswordCredential() {
        // JPA
    }

    public PasswordCredential(Long identityId, String passwordHash, Instant now) {
        this.identityId = identityId;
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    /** Troca o hash — na redefinição, e no rehash silencioso quando o algoritmo muda. */
    public void changeTo(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getIdentityId() {
        return identityId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
