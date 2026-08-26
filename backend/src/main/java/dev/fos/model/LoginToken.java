package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 *
 * <p>Desde a #81 ele serve a três propósitos ({@link LoginTokenPurpose}), com prazos diferentes, e
 * o propósito é conferido no consumo: link de verificação apresentado na rota de entrada não
 * autentica ninguém.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoginTokenPurpose purpose;

    protected LoginToken() {
        // JPA
    }

    public LoginToken(
            Long userId,
            String tokenHash,
            LoginTokenPurpose purpose,
            Instant now,
            Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    /**
     * Consome o token. Falso quando ele já foi usado, venceu ou é de outro propósito — o link não
     * autentica duas vezes, e não faz o que não foi emitido para fazer.
     */
    public boolean consume(LoginTokenPurpose expected, Instant now) {
        if (!isUsable(expected, now)) {
            return false;
        }
        this.usedAt = now;
        return true;
    }

    /**
     * O token ainda serve, sem gastá-lo.
     *
     * <p>Existe para o {@code GET} da tela de redefinição poder dizer "este link não vale mais"
     * antes de a pessoa digitar a senha nova. Consumir na abertura da tela queimaria o link em
     * qualquer pré-carregamento do navegador ou do cliente de e-mail.
     */
    public boolean isUsable(LoginTokenPurpose expected, Instant now) {
        return usedAt == null && purpose == expected && now.isBefore(expiresAt);
    }

    /**
     * Queima o token sem que ele tenha sido usado.
     *
     * <p>É o que faz redefinir a senha invalidar o que estiver pendente: um link de redefinição
     * antigo na caixa de entrada continuaria valendo por até uma hora depois de a senha já ter
     * mudado, que é exatamente a janela que um invasor com acesso à caixa usaria.
     */
    public void invalidate(Instant now) {
        if (usedAt == null) {
            this.usedAt = now;
        }
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

    public LoginTokenPurpose getPurpose() {
        return purpose;
    }
}
