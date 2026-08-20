package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.model.AccessStatus;
import dev.fos.model.AppUser;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.DisclaimerAcceptanceRepository;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.LoginTokenRepository;
import dev.fos.repo.QuizAttemptRepository;
import dev.fos.repo.SrsReviewRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.repo.UserProgressRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida da conta: login, fila de aprovação e exclusão.
 *
 * <p>Todo o resto do app continua falando em {@code userId} — o que muda com a #24 é de onde esse
 * id vem e se ele pode ser usado. Esta classe é a dona dessas duas respostas.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    /**
     * Linha semeada pela {@code V2__seed_single_user.sql}: o progresso real do autor, criado antes
     * de existir login. A primeira identidade de dono a entrar a adota — sem isso o progresso fica
     * órfão no dia do deploy.
     */
    private static final long SEEDED_USER_ID = 1L;

    private static final int MAX_LABEL = 120;

    private final AppUserRepository users;
    private final UserIdentityRepository identities;
    private final UserProgressRepository progress;
    private final SrsReviewRepository reviews;
    private final DrillLogRepository drills;
    private final LoginTokenRepository loginTokens;
    private final QuizAttemptRepository quizAttempts;
    private final DisclaimerAcceptanceRepository disclaimers;
    private final FosProperties.Auth auth;
    private final Clock clock;

    public AccountService(
            AppUserRepository users,
            UserIdentityRepository identities,
            UserProgressRepository progress,
            SrsReviewRepository reviews,
            DrillLogRepository drills,
            LoginTokenRepository loginTokens,
            QuizAttemptRepository quizAttempts,
            DisclaimerAcceptanceRepository disclaimers,
            FosProperties properties,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.progress = progress;
        this.reviews = reviews;
        this.drills = drills;
        this.loginTokens = loginTokens;
        this.quizAttempts = quizAttempts;
        this.disclaimers = disclaimers;
        this.auth = properties.auth();
        this.clock = clock;
    }

    /**
     * Registra um login bem-sucedido no provedor.
     *
     * <p>Primeira vez daquela identidade: cria a conta já {@code APROVADO}. Entrar por provedor
     * externo é acesso direto desde a #52 — quem chega por ali já teve a identidade verificada por
     * um terceiro, e a fila passou a existir para quem <em>não</em> tem provedor. Vezes seguintes:
     * só atualiza {@code last_login_at}. Nada de progresso, SRS, drill ou aceite é criado aqui:
     * quem ainda não foi liberado não tem estado no app.
     */
    @Transactional
    public AppUser registerLogin(
            String provider,
            String subject,
            String email,
            boolean emailVerified,
            String displayName) {
        Instant now = Instant.now(clock);
        String label = label(provider, subject, email, displayName);
        Optional<UserIdentity> known =
                identities.findByProviderAndProviderSubject(provider, subject);
        if (known.isPresent()) {
            UserIdentity identity = known.get();
            identity.registerLogin(now);
            identity.refresh(email, emailVerified, displayName);
            return promoteIfOwner(identity, label, now);
        }

        boolean owner = emailVerified && auth.isOwnerEmail(email);
        AppUser user = owner ? ownerAccount(label, now) : users.save(AppUser.approved(label, now));
        identities.save(
                new UserIdentity(
                        user.getId(), provider, subject, email, emailVerified, displayName, now));
        log.info(
                "Primeiro login de {}:{} — conta {} com acesso {}",
                provider,
                subject,
                user.getId(),
                user.getAccessStatus());
        return user;
    }

    /**
     * Conta do dono: adota o progresso pré-existente se ele ainda não tiver identidade.
     *
     * <p>Adotar só a primeira vez é o que impede que um segundo dono na lista sequestre o progresso
     * do primeiro — daí a checagem de que a linha semeada ainda está sem identidade.
     */
    private AppUser ownerAccount(String label, Instant now) {
        Optional<AppUser> seeded = unclaimedSeededAccount();
        if (seeded.isEmpty()) {
            return users.save(AppUser.approved(label, now));
        }
        AppUser user = seeded.get();
        user.rename(label);
        user.approve(now);
        log.info("Progresso pré-existente (app_user {}) adotado pela conta do dono", user.getId());
        return user;
    }

    /**
     * A regra do dono vale em todo login, não só no primeiro.
     *
     * <p>Sem isto, preencher {@code fos.auth.owner-emails} <em>depois</em> de já ter entrado uma
     * vez — que é a ordem natural de quem sobe o app para testar e configura em seguida — deixaria
     * a conta do autor pendente para sempre, com ninguém para aprová-la, e o progresso semeado
     * órfão. A lista vazia é o default documentado, então esse caminho é o provável, não o exótico.
     *
     * <p>Migrar a identidade para a linha semeada é seguro justamente porque a conta pendente não
     * tem dado nenhum: o portão de acesso a impede de escrever qualquer coisa.
     */
    private AppUser promoteIfOwner(UserIdentity identity, String label, Instant now) {
        AppUser user =
                users.findById(identity.getUserId()).orElseThrow(UnauthenticatedException::new);
        if (!(identity.isEmailVerified() && auth.isOwnerEmail(identity.getEmail()))) {
            return user;
        }
        if (!user.isApproved()) {
            user.approve(now);
            log.info("Conta {} aprovada por estar em fos.auth.owner-emails", user.getId());
        }

        Optional<AppUser> seeded =
                unclaimedSeededAccount()
                        .filter(seed -> !seed.getId().equals(user.getId()))
                        // A adoção apaga a conta que a identidade tinha antes. Isso era seguro
                        // quando ela nascia pendente e não podia escrever nada; desde a #52 ela
                        // nasce aprovada e pode ter usado o app antes de `owner-emails` ser
                        // preenchida. Sem esta guarda, configurar a lista depois apagaria o
                        // progresso do próprio autor.
                        .filter(seed -> isEmpty(user.getId()));
        if (seeded.isEmpty()) {
            return user;
        }

        AppUser target = seeded.get();
        identity.moveTo(target.getId());
        // Flush antes de apagar: sem ele a ordem das operações no commit pode tentar remover a
        // conta antiga com a identidade ainda apontando para ela.
        identities.saveAndFlush(identity);
        users.delete(user);
        target.rename(label);
        target.approve(now);
        log.info(
                "Conta {} do dono migrada para o progresso pré-existente (app_user {})",
                user.getId(),
                target.getId());
        return target;
    }

    /** Conta sem nada escrito: só ela pode ser trocada pela linha semeada sem perder dado. */
    private boolean isEmpty(Long userId) {
        return progress.findByIdUserId(userId).isEmpty()
                && reviews.findByIdUserId(userId).isEmpty()
                && drills.countByUserId(userId) == 0;
    }

    /** A linha semeada pela V2, enquanto nenhuma identidade a tiver reivindicado. */
    private Optional<AppUser> unclaimedSeededAccount() {
        return users.findById(SEEDED_USER_ID)
                .filter(user -> identities.findByUserId(user.getId()).isEmpty());
    }

    /** Fila de solicitações, da mais antiga para a mais nova. */
    @Transactional(readOnly = true)
    public List<AppUser> pendingRequests() {
        return users.findByAccessStatusOrderByRequestedAtAsc(AccessStatus.PENDENTE);
    }

    /**
     * Decide uma solicitação da fila.
     *
     * <p>Duas guardas, e nenhuma é preciosismo. **A própria conta fica de fora**: recusar a si
     * mesmo tranca o dono para fora do app em definitivo, e a recuperação seria pelo banco. **Só
     * decide quem está pendente**: sem isso, uma segunda aba aberta na fila antiga desfaz calada
     * uma decisão já tomada — o clássico de reabrir acesso a quem foi recusado.
     *
     * @param decidedBy conta que está decidindo
     */
    @Transactional
    public AppUser decide(Long userId, boolean approve, Long decidedBy) {
        if (userId.equals(decidedBy)) {
            throw new IllegalArgumentException(
                    "Não dá para decidir a própria conta. Peça a outro dono, se houver.");
        }
        AppUser user =
                users.findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Solicitação inexistente: " + userId));
        if (user.getAccessStatus() != AccessStatus.PENDENTE) {
            throw new IllegalArgumentException(
                    "Esta solicitação já foi decidida (" + user.getAccessStatus() + ").");
        }
        Instant now = Instant.now(clock);
        if (approve) {
            user.approve(now);
        } else {
            user.deny(now);
        }
        return user;
    }

    /** Identidades da conta, na ordem em que foram criadas. */
    @Transactional(readOnly = true)
    public List<UserIdentity> identitiesOf(Long userId) {
        return identities.findByUserId(userId);
    }

    public List<UserIdentity> identitiesOfAll(List<Long> userIds) {
        return userIds.isEmpty() ? List.of() : identities.findByUserIdIn(userIds);
    }

    /**
     * Dono é quem tem e-mail <em>verificado</em> na lista de configuração.
     *
     * <p>Sem a exigência de verificação, qualquer provedor que aceitasse um e-mail digitado pelo
     * usuário viraria caminho para a fila de aprovação.
     */
    public boolean isOwner(AppUser user) {
        return identities.findByUserId(user.getId()).stream()
                .anyMatch(
                        identity ->
                                identity.isEmailVerified()
                                        && auth.isOwnerEmail(identity.getEmail()));
    }

    /**
     * Apaga a conta e tudo que é dela, em uma transação.
     *
     * <p>São sete tabelas com {@code user_id}: {@code quiz_attempt} entrou na V3 e {@code
     * login_token} na V7. Esquecer uma delas não dá erro silencioso — dá violação de FK ao apagar
     * {@code app_user} —, mas a ordem importa e por isso está explícita.
     *
     * <p>Vale para conta pendente e recusada: quem pediu acesso e não entrou tem o mesmo direito de
     * sumir do banco.
     */
    @Transactional
    public void delete(Long userId) {
        loginTokens.deleteByUserId(userId);
        quizAttempts.deleteByUserId(userId);
        drills.deleteByUserId(userId);
        reviews.deleteByIdUserId(userId);
        progress.deleteByIdUserId(userId);
        disclaimers.deleteByUserId(userId);
        identities.deleteByUserId(userId);
        users.deleteById(userId);
        log.info("Conta {} excluída a pedido do próprio usuário", userId);
    }

    private static String label(String provider, String subject, String email, String displayName) {
        String raw =
                displayName != null && !displayName.isBlank()
                        ? displayName
                        : email != null && !email.isBlank() ? email : provider + ":" + subject;
        return raw.length() > MAX_LABEL ? raw.substring(0, MAX_LABEL) : raw;
    }
}
