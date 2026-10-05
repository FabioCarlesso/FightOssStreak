package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * Token do app mobile (#139, D68).
 *
 * <p>Guarda o hash, nunca o valor: o valor sorteado vive no {@code expo-secure-store} do aparelho,
 * e quem ler o banco não entra como ninguém — a mesma razão do {@link LoginToken}.
 *
 * <p>Pendura na identidade, e não na conta. É por ela que o {@code CurrentUserProvider} resolve o
 * usuário, e é ela que o {@code AccountService.mergeIdentityInto} move: o token segue a pessoa.
 */
@Entity
@Table(name = "mobile_token")
public class MobileToken {

    /**
     * De quanto em quanto tempo o uso é gravado. Um dia basta para vencer por desuso com precisão
     * de dia, e é o que impede toda leitura do app de virar escrita.
     */
    static final Duration TOUCH_EVERY = Duration.ofDays(1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "identity_id", nullable = false)
    private Long identityId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    protected MobileToken() {
        // JPA
    }

    public MobileToken(Long identityId, String tokenHash, Instant now) {
        this.identityId = identityId;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.lastUsedAt = now;
    }

    /** Venceu por desuso: o último uso gravado ficou mais longe que o prazo. */
    public boolean isIdleExpired(Duration idle, Instant now) {
        return !now.isBefore(lastUsedAt.plus(idle));
    }

    /**
     * Já é hora de gravar o uso de novo, no máximo uma vez por dia.
     *
     * <p>Só responde, e não muda o campo: quem grava é o {@code MobileTokenRepository.touch}, em
     * massa. Alterar a entidade carregada faria o Hibernate gravá-la no commit conferindo a linha,
     * e um "sair" no meio do caminho faria essa gravação responder 500 (#148).
     */
    public boolean isTouchDue(Instant now) {
        return !now.isBefore(lastUsedAt.plus(TOUCH_EVERY));
    }

    public Long getId() {
        return id;
    }

    public Long getIdentityId() {
        return identityId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
