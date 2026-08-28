package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.model.AppUser;
import dev.fos.model.Role;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.DisclaimerAcceptanceRepository;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.LoginTokenRepository;
import dev.fos.repo.PasswordCredentialRepository;
import dev.fos.repo.QuizAttemptRepository;
import dev.fos.repo.SrsReviewRepository;
import dev.fos.repo.UsageEventRepository;
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
 * Ciclo de vida da conta: login, vínculo entre identidades, papel e exclusão.
 *
 * <p>Todo o resto do app continua falando em {@code userId} — o que muda com a #24 é de onde esse
 * id vem. A segunda pergunta que esta classe respondia, "essa conta pode usar o app?", deixou de
 * existir com a fila de aprovação (D48): quem tem sessão está dentro. No lugar dela entrou uma
 * outra, de escopo bem menor — {@link #roleOf}, que diz quem administra.
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
    private final PasswordCredentialRepository credentials;
    private final QuizAttemptRepository quizAttempts;
    private final DisclaimerAcceptanceRepository disclaimers;
    private final UsageEventRepository usageEvents;
    private final FosProperties.Auth auth;
    private final Clock clock;

    public AccountService(
            AppUserRepository users,
            UserIdentityRepository identities,
            UserProgressRepository progress,
            SrsReviewRepository reviews,
            DrillLogRepository drills,
            LoginTokenRepository loginTokens,
            PasswordCredentialRepository credentials,
            QuizAttemptRepository quizAttempts,
            DisclaimerAcceptanceRepository disclaimers,
            UsageEventRepository usageEvents,
            FosProperties properties,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.progress = progress;
        this.reviews = reviews;
        this.drills = drills;
        this.loginTokens = loginTokens;
        this.credentials = credentials;
        this.quizAttempts = quizAttempts;
        this.disclaimers = disclaimers;
        this.usageEvents = usageEvents;
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
            AppUser user = promoteIfOwner(identity, label, now);
            // Depois de promover, e não antes: a promoção pode trocar a conta da identidade, e
            // quem fica dona do endereço é a conta que sobrou.
            claimVerifiedEmail(user, identity.getEmail(), identity.isEmailVerified());
            return user;
        }

        // Identidade nova cujo e-mail VERIFICADO já pertence a uma conta se anexa àquela conta,
        // em vez de criar a segunda (D47). Sem isto, quem usa o app pelo Google e depois se
        // cadastra com senha no mesmo endereço — ou o contrário — cai numa conta vazia e acha que
        // perdeu o progresso.
        Optional<AppUser> daMesmaPessoa = accountOwning(email, emailVerified);
        if (daMesmaPessoa.isPresent()) {
            AppUser user = daMesmaPessoa.get();
            identities.save(
                    new UserIdentity(
                            user.getId(),
                            provider,
                            subject,
                            email,
                            emailVerified,
                            displayName,
                            now));
            log.info(
                    "Primeiro login de {}:{} anexado à conta {}, dona do e-mail verificado",
                    provider,
                    subject,
                    user.getId());
            return user;
        }

        boolean owner = emailVerified && auth.isOwnerEmail(email);
        AppUser user = owner ? ownerAccount(label, now) : users.save(AppUser.approved(label, now));
        claimVerifiedEmail(user, email, emailVerified);
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

    /**
     * A conta que já é dona deste e-mail verificado, se houver (D47).
     *
     * <p>Endereço não verificado não responde nada: se respondesse, digitar o e-mail de outra
     * pessoa no cadastro anexaria a identidade nova à conta dela.
     */
    @Transactional(readOnly = true)
    public Optional<AppUser> accountOwning(String email, boolean emailVerified) {
        if (!emailVerified || email == null || email.isBlank()) {
            return Optional.empty();
        }
        return users.findByPrimaryEmail(Emails.normalize(email));
    }

    /**
     * Declara a conta dona do endereço, quando ele é verificado e ninguém mais o tem.
     *
     * <p>Silencioso quando outra conta já o reivindicou: são as contas que nasceram antes da D47,
     * quando o mesmo endereço podia estar verificado em duas contas de provedores diferentes. A
     * mais antiga fica com ele (é o que a V11 fez no backfill), e a outra segue funcionando sem
     * vínculo — nenhuma some, e nada é fundido por trás de ninguém.
     */
    @Transactional
    public void claimVerifiedEmail(AppUser user, String email, boolean emailVerified) {
        if (!emailVerified || email == null || email.isBlank()) {
            return;
        }
        // Antes da guarda de "já reivindicado", e de propósito: é aqui que passa TODO caminho em
        // que um endereço vira verificado — provedor externo e confirmação do próprio app —, e a
        // semente precisa valer em todo login, não só no primeiro. Sem isso, preencher
        // `owner-emails` depois de a conta já existir só teria efeito na próxima subida.
        applyOwnerSeed(user, email);
        if (user.getPrimaryEmail() != null) {
            return;
        }
        String normalizado = Emails.normalize(email);
        if (users.findByPrimaryEmail(normalizado).isEmpty()) {
            user.claimPrimaryEmail(normalizado);
        }
    }

    /**
     * A semente de administração aplicada a uma conta, num login (D49).
     *
     * <p>Chamada só com endereço já <b>verificado</b> — quem chama é {@link #claimVerifiedEmail},
     * que é o funil por onde passam os dois caminhos de verificação. Promove e nunca rebaixa, pelo
     * mesmo motivo de {@link #seedAdmins()}: tirar um endereço da variável não é ordem de tirar o
     * papel de ninguém, e um deploy com a lista mal preenchida não pode virar perda de acesso à
     * administração.
     */
    private void applyOwnerSeed(AppUser user, String email) {
        if (user.isDemo() || user.getRole() == Role.ADMIN || !auth.isOwnerEmail(email)) {
            return;
        }
        user.changeRole(Role.ADMIN, null, Instant.now(clock));
        log.info("Conta {} promovida a ADMIN por estar em fos.auth.owner-emails", user.getId());
    }

    /**
     * Move a identidade para a conta que já é dona do endereço, e apaga a conta que ela deixou.
     *
     * <p>Só é chamado na confirmação do e-mail do cadastro por senha (#81), e só quando a conta de
     * origem está vazia — ela nasceu no cadastro, nunca teve sessão (o cadastro não abre nenhuma) e
     * portanto não tem o que perder. A guarda existe assim mesmo: apagar conta com dado dentro é o
     * tipo de erro que ninguém descobre a tempo.
     */
    @Transactional
    public AppUser mergeIdentityInto(UserIdentity identity, AppUser target) {
        Long origem = identity.getUserId();
        if (origem.equals(target.getId())) {
            return target;
        }
        identity.moveTo(target.getId());
        // Flush antes de apagar, pelo mesmo motivo da adoção do progresso semeado: sem ele a
        // ordem das operações no commit pode tentar remover a conta antiga com a identidade ainda
        // apontando para ela.
        identities.saveAndFlush(identity);
        if (identities.findByUserId(origem).isEmpty() && isEmpty(origem)) {
            delete(origem);
        }
        log.info(
                "Identidade {} anexada à conta {}, dona do e-mail",
                identity.getId(),
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

    /** Identidades da conta, na ordem em que foram criadas. */
    @Transactional(readOnly = true)
    public List<UserIdentity> identitiesOf(Long userId) {
        return identities.findByUserId(userId);
    }

    /**
     * O papel da conta — e o <b>único</b> ponto do app que decide isso (D48, D49).
     *
     * <p>O lugar não mudou; a fonte, sim. Até a D48 a resposta era calculada a cada requisição a
     * partir de {@code fos.auth.owner-emails}, e a consequência era que administrador novo exigia
     * deploy. Agora ela sai de {@code app_user.role}, que a semente da subida ({@link
     * #seedAdmins()}) e o próprio app escrevem — e a exigência de e-mail <b>verificado</b> continua
     * valendo, aplicada onde a promoção acontece em vez de a cada leitura. Sem ela, digitar o
     * endereço do administrador num provedor que não verifica e-mail daria acesso de administração;
     * a regra é a mesma da D48, só mudou de momento.
     */
    @Transactional(readOnly = true)
    public Role roleOf(AppUser user) {
        // Guarda explícita, mesmo com a identidade de demonstração nascendo sem e-mail e com a
        // conta nascendo USUARIO: a cópia vem de uma conta-modelo que pode estar em `owner-emails`,
        // e um dia alguém pode achar boa ideia copiar mais campos junto. Se isso acontecer, o
        // defeito é a administração do app aberta para um link público (#62).
        if (user.isDemo()) {
            return Role.USUARIO;
        }
        return user.getRole();
    }

    /**
     * A semente de administração, na subida da aplicação (D49).
     *
     * <p>{@code fos.auth.owner-emails} deixou de ser a fonte da verdade do papel e virou isto: a
     * lista promove, uma vez por subida, quem tem e-mail verificado e ainda é {@code USUARIO}. Ela
     * <b>não</b> sai — sem ela, ambiente novo sobe sem nenhum administrador e não existe ninguém
     * para promover o primeiro. É o bootstrap e a saída de emergência, nessa ordem.
     *
     * <p>O que ela não faz é rebaixar: tirar um endereço da variável não tira o papel de ninguém.
     * Rebaixar é ação de quem administra, pela API, com as guardas que a #89 define — e uma
     * variável que rebaixasse em silêncio na subida transformaria um deploy com a lista mal
     * preenchida em perda de acesso à administração do app.
     */
    @Transactional
    public void seedAdmins() {
        for (String email : auth.ownerEmails()) {
            identities.findByEmailIgnoreCaseAndEmailVerifiedTrue(email).stream()
                    .map(UserIdentity::getUserId)
                    .distinct()
                    .flatMap(userId -> users.findById(userId).stream())
                    .filter(user -> !user.isDemo())
                    .filter(user -> user.getRole() != Role.ADMIN)
                    .forEach(
                            user -> {
                                user.changeRole(Role.ADMIN, null, Instant.now(clock));
                                log.info(
                                        "Conta {} promovida a ADMIN por estar em"
                                                + " fos.auth.owner-emails",
                                        user.getId());
                            });
        }
    }

    /**
     * Apaga a conta e tudo que é dela, em uma transação.
     *
     * <p>São sete tabelas com {@code user_id}: {@code quiz_attempt} entrou na V3 e {@code
     * login_token} na V7. Esquecer uma delas não dá erro silencioso — dá violação de FK ao apagar
     * {@code app_user} —, mas a ordem importa e por isso está explícita. A oitava, {@code
     * password_credential} (V11), é a única que não tem {@code user_id}: ela pendura na identidade,
     * e por isso é a primeira a sair.
     *
     * <p>Não é só o {@code DELETE /api/me}: o mesmo caminho apaga a conta descartável do cadastro
     * que acabou vinculado a outra (D47) e a demonstração vencida (#62). Nenhum deles tem um humano
     * do outro lado, e é por isso que a exclusão precisa ser completa sozinha.
     *
     * <p>A nona é {@code usage_event} (V14, D50), e ela é a única que não daria erro se fosse
     * esquecida: não tem FK, de propósito. Sai daqui porque foi prometido por escrito que sairia —
     * o agregado <b>fica</b>, porque não tem chave de visita nem id de conta, e apagá-lo faria a
     * exclusão de uma conta reescrever o histórico de uso de todo mundo.
     */
    @Transactional
    public void delete(Long userId) {
        // A senha mora em `password_credential`, pendurada na IDENTIDADE e não na conta (#81):
        // sai antes das identidades, senão a remoção delas bate na FK.
        credentials.deleteByIdentityIdIn(
                identities.findByUserId(userId).stream().map(UserIdentity::getId).toList());
        usageEvents.deleteByUserId(userId);
        loginTokens.deleteByUserId(userId);
        quizAttempts.deleteByUserId(userId);
        drills.deleteByUserId(userId);
        reviews.deleteByIdUserId(userId);
        progress.deleteByIdUserId(userId);
        disclaimers.deleteByUserId(userId);
        identities.deleteByUserId(userId);
        users.deleteById(userId);
        // Sem "a pedido do próprio usuário": desde a #62 este mesmo caminho apaga demonstração
        // vencida na varredura, e o log não pode afirmar o que não sabe.
        log.info("Conta {} excluída, com tudo que era dela", userId);
    }

    private static String label(String provider, String subject, String email, String displayName) {
        String raw =
                displayName != null && !displayName.isBlank()
                        ? displayName
                        : email != null && !email.isBlank() ? email : provider + ":" + subject;
        return raw.length() > MAX_LABEL ? raw.substring(0, MAX_LABEL) : raw;
    }
}
