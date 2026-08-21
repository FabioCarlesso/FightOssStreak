package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.email.EmailSender;
import dev.fos.model.AccessStatus;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * <p>O dono é avisado por um <b>resumo horário</b> da fila (#54), não a cada pedido: ver {@link
 * #notifyOwnersOfQueue()}. O destinatário vem de {@code fos.auth.owner-emails}, nunca do formulário
 * — é por isso que o aviso não reabre o buraco que a ordem acima fecha, e {@code /solicitar}
 * continua sem servir para escrever a endereço arbitrário.
 */
@Service
public class EmailAccessService {

    private static final Logger log = LoggerFactory.getLogger(EmailAccessService.class);

    /** Curto de propósito: o link é credencial enquanto vale. */
    static final Duration VALIDADE = Duration.ofMinutes(15);

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * O fuso do resumo, e a razão de ele existir como constante pública: o agendador dispara em
     * horário de Brasília e o e-mail mostra as horas no mesmo fuso. Separados, um resumo das 10h
     * listaria pedidos com hora que não bate com a janela que o mandou.
     */
    public static final String FUSO_BRASIL = "America/Sao_Paulo";

    private static final DateTimeFormatter QUANDO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of(FUSO_BRASIL));

    private final AppUserRepository users;
    private final UserIdentityRepository identities;
    private final LoginTokenRepository tokens;
    private final ObjectProvider<EmailSender> emailSender;
    private final FosProperties.Auth auth;
    private final String publicUrl;
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
        this.publicUrl = properties.publicUrl();
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
     * <p>Não dispara e-mail nenhum — nem para quem pediu, nem para o dono. O dono fica sabendo pelo
     * resumo da hora seguinte ({@link #notifyOwnersOfQueue()}).
     */
    @Transactional
    public void requestAccess(String rawEmail) {
        String email = normalize(rawEmail);
        if (identities
                .findByProviderAndProviderSubject(EmailAuthenticationToken.PROVIDER, email)
                .isPresent()) {
            return;
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
    }

    /**
     * Resumo da fila para quem decide (#54) — uma vez por janela, das 10h às 22h de Brasília.
     *
     * <p><b>Por que resumo e não aviso por pedido.</b> O que faltava era o dono saber que a fila
     * andou; um e-mail por clique dava isso e trazia junto um canal de ruído no horário que
     * quisesse. A janela horária resolve os dois: o atraso máximo é de uma hora dentro do dia, e
     * nada chega de madrugada.
     *
     * <p><b>Só sai quando há novidade.</b> A guarda é {@code queue_notice_sent_at} nulo em alguma
     * pendente. Sem ela, uma fila parada geraria treze e-mails idênticos por dia até alguém decidir
     * — o oposto do problema que isto veio resolver. E o e-mail lista a fila <em>inteira</em>, não
     * só o que chegou agora: quem abre precisa ver o que ainda deve, não o delta desde a última
     * vez.
     *
     * <p><b>A marca só é gravada se pelo menos um envio passou.</b> Provedor fora do ar deixa tudo
     * como estava, e a janela seguinte tenta de novo — marcar antes de entregar perderia o pedido
     * em silêncio, que é exatamente o esquecimento que este método existe para evitar.
     */
    @Transactional
    public void notifyOwnersOfQueue() {
        EmailSender sender = emailSender.getIfAvailable();
        if (sender == null || auth.ownerEmails().isEmpty()) {
            return;
        }
        List<AppUser> fila = users.findByAccessStatusOrderByRequestedAtAsc(AccessStatus.PENDENTE);
        if (fila.stream().noneMatch(AppUser::isQueueNoticePending)) {
            return;
        }
        String assunto =
                fila.size() == 1
                        ? "1 solicitação de acesso esperando no FightOssStreak"
                        : fila.size() + " solicitações de acesso esperando no FightOssStreak";
        String corpo = resumo(fila);
        boolean entregue = false;
        for (String owner : auth.ownerEmails()) {
            try {
                sender.send(owner, assunto, corpo);
                entregue = true;
            } catch (RuntimeException e) {
                // Sem endereço na mensagem: quem pediu é dado pessoal (docs/11-privacidade.md).
                log.warn("Resumo da fila não pôde ser enviado a um dos donos", e);
            }
        }
        if (!entregue) {
            return;
        }
        Instant now = Instant.now(clock);
        fila.forEach(user -> user.markQueueNoticeSent(now));
        log.info("Resumo da fila enviado — {} solicitação(ões) pendente(s)", fila.size());
    }

    /** Uma consulta de identidades para a fila inteira, e não uma por linha. */
    private String resumo(List<AppUser> fila) {
        Map<Long, UserIdentity> porUsuario =
                identities.findByUserIdIn(fila.stream().map(AppUser::getId).toList()).stream()
                        .collect(
                                Collectors.toMap(
                                        UserIdentity::getUserId,
                                        Function.identity(),
                                        (primeiro, segundo) -> primeiro));
        String linhas =
                fila.stream()
                        .map(
                                user ->
                                        "- %s — %s"
                                                .formatted(
                                                        Optional.ofNullable(
                                                                        porUsuario.get(
                                                                                user.getId()))
                                                                .map(UserIdentity::getEmail)
                                                                .orElseGet(user::getLabel),
                                                        QUANDO.format(user.getRequestedAt())))
                        .collect(Collectors.joining("\n"));
        String link =
                publicUrl.isEmpty()
                        ? "Para liberar ou recusar, abra Solicitações no app."
                        : "Para liberar ou recusar:\n" + publicUrl + "/solicitacoes";
        return """
                %s aguardando sua decisão no FightOssStreak:

                %s

                Horários em horário de Brasília.

                %s

                Quem pediu não recebe nada até você decidir.
                """
                .formatted(
                        fila.size() == 1 ? "1 solicitação" : fila.size() + " solicitações",
                        linhas,
                        link);
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
