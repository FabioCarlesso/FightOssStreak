package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * O refresh token que a Apple entrega no primeiro login, guardado só para ser revogado (#139, D68).
 *
 * <p>Fora de {@link UserIdentity} pela razão do {@link PasswordCredential}: a identidade diz só
 * "quem é". Ao contrário da senha, este valor não vira hash, porque a revogação precisa reenviá-lo
 * à Apple — e é por isso que nada no app aceita este valor como credencial: ele serve para a
 * exclusão da conta avisar a Apple, e para mais nada.
 */
@Entity
@Table(name = "apple_credential")
public class AppleCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "identity_id", nullable = false)
    private Long identityId;

    @Column(name = "refresh_token", nullable = false)
    private String refreshToken;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppleCredential() {
        // JPA
    }

    public AppleCredential(Long identityId, String refreshToken, Instant now) {
        this.identityId = identityId;
        this.refreshToken = refreshToken;
        this.updatedAt = now;
    }

    /** Troca pelo mais recente: a Apple pode emitir outro numa nova autorização. */
    public void replaceWith(String refreshToken, Instant now) {
        this.refreshToken = refreshToken;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getIdentityId() {
        return identityId;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
