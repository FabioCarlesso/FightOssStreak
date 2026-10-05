package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.model.MobileToken;
import dev.fos.model.UserIdentity;
import dev.fos.repo.MobileTokenRepository;
import dev.fos.repo.UserIdentityRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os tokens do app mobile: emitir, conferir e revogar (#139, D68).
 *
 * <p>É o equivalente do {@link SessionLogin} para o app, e pelo mesmo motivo: o único lugar que
 * cria credencial. Dos três deveres do {@code SessionLogin}, rotacionar o id não se aplica — todo
 * token nasce sorteado, não há um anterior que alguém tenha plantado —, gravar o contexto vira
 * gravar a linha, e registrar é a própria linha, que é o que permite revogar depois.
 */
@Service
public class MobileTokens {

    private static final Logger log = LoggerFactory.getLogger(MobileTokens.class);

    /** 256 bits: o mesmo tamanho do link de e-mail, que também é credencial enquanto vale. */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom SORTEIO = new SecureRandom();

    private final MobileTokenRepository tokens;
    private final UserIdentityRepository identities;
    private final Duration idle;
    private final Clock clock;

    public MobileTokens(
            MobileTokenRepository tokens,
            UserIdentityRepository identities,
            FosProperties properties,
            Clock clock) {
        this.tokens = tokens;
        this.identities = identities;
        this.idle = Duration.ofDays(properties.mobile().tokenIdleDays());
        this.clock = clock;
    }

    /** Emite um token para a identidade e devolve o valor — a única vez que ele existe em claro. */
    @Transactional
    public String issue(UserIdentity identity) {
        byte[] bytes = new byte[TOKEN_BYTES];
        SORTEIO.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new MobileToken(identity.getId(), hash(raw), Instant.now(clock)));
        log.info("Token do app emitido — identidade {}", identity.getId());
        return raw;
    }

    /**
     * A identidade dona do token, se ele vale.
     *
     * <p>Token vencido por desuso é apagado aqui, na primeira vez que alguém o apresenta: a
     * varredura é preguiçosa como a da demonstração, e o efeito para quem chama é o mesmo 401 de um
     * token que nunca existiu.
     *
     * <p>As duas escritas daqui são em massa, porque o mesmo token chega em várias requisições
     * simultâneas: ver {@link MobileTokenRepository}.
     */
    @Transactional
    public Optional<Long> authenticate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Optional<MobileToken> found = tokens.findByTokenHash(hash(raw.trim()));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MobileToken token = found.get();
        Instant now = Instant.now(clock);
        if (token.isIdleExpired(idle, now)) {
            tokens.deleteRow(token.getId());
            return Optional.empty();
        }
        if (token.isTouchDue(now)) {
            tokens.touch(token.getId(), now);
        }
        return Optional.of(token.getIdentityId());
    }

    /** Revoga o token apresentado — é o "sair" do app. Token que não existe não é erro. */
    @Transactional
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        tokens.deleteRowByTokenHash(hash(raw.trim()));
    }

    /**
     * Revoga todo token da conta, de qualquer identidade.
     *
     * <p>É o que a redefinição de senha chama: trocar a senha depois de perder o celular tem que
     * tirar o celular de dentro, inclusive o que entrou pelo Google.
     */
    @Transactional
    public void revokeAllOf(Long userId) {
        List<Long> ids = identities.findByUserId(userId).stream().map(UserIdentity::getId).toList();
        if (!ids.isEmpty()) {
            tokens.deleteByIdentityIdIn(ids);
        }
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", e);
        }
    }
}
