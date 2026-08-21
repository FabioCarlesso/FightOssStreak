package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.email.EmailSender;
import dev.fos.model.AppUser;
import dev.fos.model.LoginToken;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.LoginTokenRepository;
import dev.fos.repo.UserIdentityRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entrada por e-mail, para quem não tem provedor externo (#52).
 *
 * <p>Três passos, e a ordem deles é a decisão: <b>pedir</b> acesso não dispara e-mail nenhum,
 * <b>aprovar</b> dispara o primeiro, e <b>entrar</b> só emite link para conta já liberada. Enviar
 * no pedido transformaria um endpoint público em canal de spam com o domínio do app no remetente.
 *
 * <p>Consequência boa da ordem: pedir acesso com o endereço de outra pessoa não dá acesso a
 * ninguém, porque o link vai para a caixa do dono do endereço, não para quem preencheu o
 * formulário.
 *
 * <p>O pedido avisa <b>o dono</b> (#54), e só ele: o destinatário vem de {@code
 * fos.auth.owner-emails}, nunca do formulário. É por isso que o aviso não reabre o buraco que a
 * ordem acima fecha — {@code /solicitar} continua sem servir para escrever a endereço arbitrário.
 */
@Service
public class EmailAccessService {

    private static final Logger log = LoggerFactory.getLogger(EmailAccessService.class);

    /** Curto de propósito: o link é credencial enquanto vale. */
    static final Duration VALIDADE = Duration.ofMinutes(15);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** UTC e explícito: o aviso pode ser lido de qualquer fuso, e a hora crua confundiria. */
    private static final DateTimeFormatter QUANDO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final AppUserRepository users;
    private final UserIdentityRepository identities;
    private final LoginTokenRepository tokens;
    private final ObjectProvider<EmailSender> emailSender;
    private final FosProperties.Auth auth;
    private final Clock clock;

    public EmailAccessService(
            AppUserRepository users,
            UserIdentityRepository identities,
            LoginTokenRepository tokens,
            ObjectProvider<EmailSender> emailSender,
            FosProperties properties,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.tokens = tokens;
        this.emailSender = emailSender;
        this.auth = properties.auth();
        this.clock = clock;
    }

    /** Sem credencial de envio não há bean, e a entrada por e-mail não é oferecida na tela. */
    public boolean isEnabled() {
        return emailSender.getIfAvailable() != null;
    }

    /**
     * Registra um pedido de acesso. Idempotente: pedir de novo não cria outra conta nem reabre uma
     * decisão já tomada.
     *
     * @return verdadeiro só quando a fila realmente cresceu. É o que faz o aviso ao dono herdar a
     *     idempotência daqui em vez de repeti-la: quem insiste no formulário não vira uma caixa de
     *     entrada cheia do mesmo pedido.
     */
    @Transactional
    public boolean requestAccess(String rawEmail) {
        String email = normalize(rawEmail);
        if (identities
                .findByProviderAndProviderSubject(EmailAuthenticationToken.PROVIDER, email)
                .isPresent()) {
            return false;
        }
        Instant now = Instant.now(clock);
        AppUser user = users.save(AppUser.pending(email, now));
        // `emailVerified` falso: até aqui ninguém provou ser dono do endereço — só digitou ele.
        // Vira verdadeiro no primeiro link consumido. É isso que impede alguém de virar dono do
        // app digitando o e-mail que está em `fos.auth.owner-emails`.
        identities.save(
                new UserIdentity(
                        user.getId(),
                        EmailAuthenticationToken.PROVIDER,
                        email,
                        email,
                        false,
                        null,
                        now));
        log.info("Pedido de acesso por e-mail registrado — conta {}", user.getId());
        return true;
    }

    /**
     * Avisa o dono de que alguém entrou na fila (#54).
     *
     * <p>Fora da transação de propósito, e por isso um método separado em vez de duas linhas no fim
     * do {@link #requestAccess}: {@code EmailSender.send} propaga a falha do provedor de envio, e
     * lá dentro isso reverteria a conta pendente recém-criada e devolveria 500 num endpoint público
     * — o pedido sumiria por causa do aviso sobre ele. Aqui a falha é só log.
     *
     * <p>Um envio por endereço, e um {@code catch} por envio: endereço inválido na configuração não
     * pode calar os outros donos. Lista vazia — o default — não tenta nada.
     */
    public void notifyOwnersOfRequest(String rawEmail, String baseUrl) {
        EmailSender sender = emailSender.getIfAvailable();
        if (sender == null || auth.ownerEmails().isEmpty()) {
            return;
        }
        String corpo =
                """
                Alguém pediu acesso ao FightOssStreak.

                Endereço: %s
                Quando: %s

                Para liberar ou recusar, abra a fila de solicitações:
                %s

                Quem pediu não recebe nada até você decidir.
                """
                        .formatted(
                                normalize(rawEmail),
                                QUANDO.format(Instant.now(clock)),
                                baseUrl + "/solicitacoes");
        for (String owner : auth.ownerEmails()) {
            try {
                sender.send(owner, "Novo pedido de acesso ao FightOssStreak", corpo);
            } catch (RuntimeException e) {
                // Sem o endereço na mensagem: quem pediu é dado pessoal (docs/11-privacidade.md),
                // e para depurar basta saber que o aviso não saiu.
                log.warn("Aviso de pedido de acesso não pôde ser enviado ao dono", e);
            }
        }
    }

    /**
     * Emite o link de entrada, se a conta existir e estiver liberada.
     *
     * <p>Não devolve nada e não reclama: quem chama responde igual para endereço inexistente,
     * pendente, recusado e aprovado. Diferenciar transformaria isto em consulta de "quem tem conta
     * neste app".
     */
    @Transactional
    public void requestLogin(String rawEmail, String baseUrl) {
        String email = normalize(rawEmail);
        identities
                .findByProviderAndProviderSubject(EmailAuthenticationToken.PROVIDER, email)
                .flatMap(identity -> users.findById(identity.getUserId()))
                .filter(AppUser::isApproved)
                .ifPresent(user -> sendLink(user, email, baseUrl, "Sua entrada no FightOssStreak"));
    }

    /** Primeiro link, disparado quando o dono aprova o pedido. */
    @Transactional
    public void notifyApproved(Long userId, String baseUrl) {
        identities.findByUserId(userId).stream()
                .filter(
                        identity ->
                                EmailAuthenticationToken.PROVIDER.equals(identity.getProvider()))
                .findFirst()
                .ifPresent(
                        identity ->
                                sendLink(
                                        users.findById(userId).orElseThrow(),
                                        identity.getEmail(),
                                        baseUrl,
                                        "Seu acesso ao FightOssStreak foi liberado"));
    }

    /**
     * Consome o link e devolve o e-mail autenticado.
     *
     * <p>Vazio quando o token não existe, já foi usado ou venceu — os três casos respondem igual
     * para fora, mas a tela sabe dizer que o link não vale mais.
     */
    @Transactional
    public Optional<String> consume(String rawToken) {
        Instant now = Instant.now(clock);
        return tokens.findByTokenHash(hash(rawToken))
                .filter(token -> token.consume(now))
                .flatMap(
                        token ->
                                identities.findByUserId(token.getUserId()).stream()
                                        .filter(
                                                i ->
                                                        EmailAuthenticationToken.PROVIDER.equals(
                                                                i.getProvider()))
                                        .findFirst())
                .map(
                        identity -> {
                            // Chegou pelo link, então o endereço é mesmo dela.
                            identity.refresh(identity.getEmail(), true, identity.getDisplayName());
                            identity.registerLogin(now);
                            return identity.getEmail();
                        });
    }

    private void sendLink(AppUser user, String email, String baseUrl, String assunto) {
        EmailSender sender = emailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Entrada por e-mail pedida sem provedor de envio configurado");
            return;
        }
        Instant now = Instant.now(clock);
        String raw = newToken();
        tokens.save(new LoginToken(user.getId(), hash(raw), now, now.plus(VALIDADE)));
        String link = baseUrl + "/api/login/email/" + raw;
        sender.send(
                email,
                assunto,
                """
                Para entrar no FightOssStreak, abra o link abaixo:

                %s

                O link vale por %d minutos e só funciona uma vez.
                Se não foi você que pediu, ignore este e-mail — nada acontece.
                """
                        .formatted(link, VALIDADE.toMinutes()));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** O banco guarda só o hash — ver {@link LoginToken}. */
    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", e);
        }
    }

    /** O endereço é a chave da identidade: precisa entrar sempre na mesma forma. */
    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
