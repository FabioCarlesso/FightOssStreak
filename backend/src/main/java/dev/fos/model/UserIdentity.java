package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Identidade externa que autentica uma conta.
 *
 * <p>A chave é o par {@code (provider, providerSubject)} — e-mail não serve: a Apple entrega um
 * endereço de relay quando o usuário esconde o dele, o Facebook pode não devolver e-mail nenhum, e
 * o mesmo endereço pode aparecer em provedores diferentes. Por isso também não há merge automático
 * de contas por e-mail coincidente.
 */
@Entity
@Table(name = "user_identity")
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    /** Nulo quando o provedor não devolve e-mail. */
    @Column private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    /** Nulo quando o provedor não devolve nome. */
    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected UserIdentity() {
        // JPA
    }

    public UserIdentity(
            Long userId,
            String provider,
            String providerSubject,
            String email,
            boolean emailVerified,
            String displayName,
            Instant now) {
        this.userId = userId;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.email = email;
        this.emailVerified = emailVerified;
        this.displayName = displayName;
        this.createdAt = now;
        this.lastLoginAt = now;
    }

    /**
     * Login seguinte da mesma identidade: só a data muda.
     *
     * <p>Nome e e-mail não são reescritos de propósito — a Apple só os envia no primeiro
     * consentimento, então sobrescrever com o que veio depois apagaria o que se sabe da pessoa.
     */
    public void registerLogin(Instant now) {
        this.lastLoginAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
