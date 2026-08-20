package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Token de entrada por e-mail, de uso único.
 *
 * <p>O valor sorteado viaja no link e <b>não</b> é guardado: a coluna tem o hash. Quem tiver
 * leitura do banco não consegue entrar como ninguém — mesma razão de não se guardar senha em claro,
 * e vale igual aqui porque este token <em>é</em> a credencial enquanto vale.
 */
@Entity
@Table(name = "login_token")
public class LoginToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected LoginToken() {
        // JPA
    }

    public LoginToken(Long userId, String tokenHash, Instant now, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    /**
     * Consome o token. Falso quando ele já foi usado ou venceu — o link não autentica duas vezes.
     */
    public boolean consume(Instant now) {
        if (usedAt != null || !now.isBefore(expiresAt)) {
            return false;
        }
        this.usedAt = now;
        return true;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
