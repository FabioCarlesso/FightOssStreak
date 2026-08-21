package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.model.AppUser;
import dev.fos.model.DrillLog;
import dev.fos.model.QuizAttempt;
import dev.fos.model.SrsReview;
import dev.fos.model.UserIdentity;
import dev.fos.model.UserNodeKey;
import dev.fos.model.UserProgress;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.QuizAttemptRepository;
import dev.fos.repo.SrsReviewRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.repo.UserProgressRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acesso demonstrativo: cada visita ganha uma cópia descartável da conta-modelo (#62).
 *
 * <p><b>Por que cópia e não uma conta de demonstração compartilhada.</b> O desenho óbvio — um
 * usuário fixo, um link permanente — quebra assim que a demonstração grava de verdade: o primeiro
 * visitante conclui nós e o segundo vê a sobra do anterior em vez da árvore curada; a anotação
 * fixada é texto livre visível para quem vier depois; o streak passa a ser o de quem visitou por
 * último; e {@code DELETE /api/me} apaga a demonstração inteira. Consertar isso pede reset
 * agendado, e reset pede uma fonte de verdade para restaurar — ou seja, já é o desenho da cópia, só
 * que pior. Daí a regra: a conta-modelo existe, mas nunca recebe visitante.
 *
 * <p><b>A conta-modelo é curada pelo app, não por script.</b> O autor entra nela pelos meios
 * normais, conclui nós, registra drills e escreve as anotações que o visitante vai ler. É por isso
 * que a configuração é um e-mail (ver {@link FosProperties.Demo}).
 *
 * <p><b>Isto abre exceção à D37</b>, que manda quem não tem provedor passar pela fila: aqui a conta
 * nasce aprovada sem aprovação nenhuma. O que a sustenta está todo neste arquivo — a conta é
 * descartável, não tem identidade de ninguém, não tem poder além do próprio progresso (ver {@code
 * AccountService.isOwner}) e tem prazo.
 */
@Service
public class DemoAccessService {

    private static final Logger log = LoggerFactory.getLogger(DemoAccessService.class);

    /**
     * Prazo de vida da demonstração.
     *
     * <p>Duas horas: longo o bastante para percorrer o loop inteiro — responder o quiz, registrar o
     * drill, ver a revisão ser reagendada — e curto o bastante para que a conta não vire depósito
     * de dado de ninguém.
     */
    public static final Duration VALIDADE = Duration.ofHours(2);

    /** Teto de demonstrações vivas ao mesmo tempo. Cada uma é uma cópia da árvore inteira. */
    public static final int MAX_VIVAS = 20;

    /** Freio por IP: abrir demonstração é ação rara: o excesso só serve para encher o banco. */
    public static final int MAX_POR_IP = 5;

    public static final Duration JANELA_IP = Duration.ofHours(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Nome que aparece no cabeçalho do app. Diz o que a conta é sem fingir ser alguém. */
    private static final String ROTULO = "Visitante";

    private final AppUserRepository users;
    private final UserIdentityRepository identities;
    private final UserProgressRepository progress;
    private final SrsReviewRepository reviews;
    private final DrillLogRepository drills;
    private final QuizAttemptRepository quizAttempts;
    private final AccountService accounts;
    private final AccessRateLimiter freio;
    private final FosProperties.Demo demo;
    private final Clock clock;

    public DemoAccessService(
            AppUserRepository users,
            UserIdentityRepository identities,
            UserProgressRepository progress,
            SrsReviewRepository reviews,
            DrillLogRepository drills,
            QuizAttemptRepository quizAttempts,
            AccountService accounts,
            AccessRateLimiter freio,
            FosProperties properties,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.progress = progress;
        this.reviews = reviews;
        this.drills = drills;
        this.quizAttempts = quizAttempts;
        this.accounts = accounts;
        this.freio = freio;
        this.demo = properties.demo();
        this.clock = clock;
    }

    /**
     * Sessão recém-aberta.
     *
     * @param subject o {@code provider_subject} sorteado; é o que o {@link DemoAuthenticationToken}
     *     carrega
     * @param expiresAt quando a conta deixa de autenticar e passa a ser varrida
     */
    public record DemoSession(String subject, Instant expiresAt) {}

    /**
     * Existe conta-modelo neste ambiente?
     *
     * <p>Sem ela o botão não aparece na landing, em vez de aparecer e falhar no clique — mesma
     * regra do provedor sem credencial e do envio de e-mail sem chave.
     */
    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return template().isPresent();
    }

    /**
     * Abre uma demonstração: varre as vencidas, cria a conta descartável e copia a conta-modelo.
     *
     * <p>Tudo em uma transação, e no banco — e não pela API, como faz a semeadura do script de
     * prints: por visitante seriam dezenas de chamadas, e o resultado dependeria da hora.
     *
     * @param ip endereço de quem pediu, para o freio
     */
    @Transactional
    public DemoSession create(String ip) {
        AppUser modelo = template().orElseThrow(DemoUnavailableException::notConfigured);
        Instant agora = Instant.now(clock);

        freio.evictOlderThan(JANELA_IP, agora);
        if (!freio.tryAcquire("demo-ip:" + ip, MAX_POR_IP, JANELA_IP, agora)) {
            throw DemoUnavailableException.busy(
                    "Muitas demonstrações abertas deste endereço na última hora. Tente de novo"
                            + " mais tarde.");
        }

        // Varredura preguiçosa, no momento de criar. Não é @Scheduled de propósito: o caso ruim é
        // um punhado de contas vencidas paradas no banco enquanto ninguém abre demonstração nova,
        // e isso não é vazamento. O agendador que existe (#54) é do resumo da fila e só é
        // registrado quando há credencial de envio — pendurar a limpeza nele amarraria a
        // demonstração a uma configuração que nada tem a ver com ela.
        sweepExpired(agora);

        if (users.countByDemoExpiresAtIsNotNull() >= MAX_VIVAS) {
            throw DemoUnavailableException.busy(
                    "Há demonstrações demais abertas agora. Tente de novo em alguns minutos.");
        }

        AppUser visitante = users.save(AppUser.demo(ROTULO, agora, agora.plus(VALIDADE)));
        String subject = newSubject();
        // Sem e-mail e sem nome de gente: a conta não é de ninguém. `user_identity` já comporta
        // isto — a chave é (provider, subject), e os dois campos são nulos por natureza.
        identities.save(
                new UserIdentity(
                        visitante.getId(),
                        DemoAuthenticationToken.PROVIDER,
                        subject,
                        null,
                        false,
                        ROTULO,
                        agora));
        copiar(modelo.getId(), visitante.getId(), agora);

        log.info(
                "Demonstração {} criada a partir da conta-modelo {}, válida até {}",
                visitante.getId(),
                modelo.getId(),
                visitante.getDemoExpiresAt());
        return new DemoSession(subject, visitante.getDemoExpiresAt());
    }

    /**
     * A conta-modelo, quando ela existe.
     *
     * <p>Três filtros, e cada um fecha um buraco: <b>e-mail verificado</b> (na consulta) impede que
     * alguém aponte a cópia para outra conta digitando este endereço na tela de pedido de acesso,
     * que grava identidade não verificada; <b>aprovada</b> porque conta na fila não tem estado
     * nenhum para copiar; e <b>não-demonstração</b> para que uma cópia jamais vire molde de outra.
     */
    private Optional<AppUser> template() {
        if (!demo.isConfigured()) {
            return Optional.empty();
        }
        return identities.findByEmailIgnoreCaseAndEmailVerifiedTrue(demo.templateEmail()).stream()
                .map(UserIdentity::getUserId)
                .distinct()
                .flatMap(id -> users.findById(id).stream())
                .filter(AppUser::isApproved)
                .filter(user -> !user.isDemo())
                .findFirst();
    }

    /** Apaga toda demonstração vencida, com o mesmo caminho de {@code DELETE /api/me}. */
    private void sweepExpired(Instant agora) {
        List<AppUser> vencidas = users.findByDemoExpiresAtLessThan(agora);
        vencidas.forEach(vencida -> accounts.delete(vencida.getId()));
        if (!vencidas.isEmpty()) {
            log.info("{} demonstração(ões) vencida(s) apagada(s)", vencidas.size());
        }
    }

    /**
     * Copia o estado da conta-modelo, deslocando todas as datas pelo mesmo delta.
     *
     * <p><b>O rebase é o que faz a demonstração parecer viva.</b> As datas do progresso são
     * absolutas: copiadas cruas, uma conta-modelo curada há dois meses entrega uma agenda com tudo
     * vencido — o oposto de "já carregado". Deslocar tudo junto preserva o que interessa, que é a
     * distância entre os eventos: o que estava vencido há dois dias continua vencido há dois dias,
     * e o que estava agendado para depois de amanhã continua para depois de amanhã.
     *
     * <p><b>O aceite do disclaimer não entra</b>: é declaração de quem está lendo, não estado de
     * progresso. O visitante passa pelo {@code DisclaimerGate} como qualquer um.
     */
    private void copiar(Long modelo, Long visitante, Instant agora) {
        long delta = delta(modelo);

        for (UserProgress origem : progress.findByIdUserId(modelo)) {
            UserProgress copia =
                    new UserProgress(
                            new UserNodeKey(visitante, origem.getId().getNodeId()),
                            origem.getStatus(),
                            desloca(origem.getUpdatedAt(), delta));
            copia.setCompletedAt(desloca(origem.getCompletedAt(), delta));
            copia.setLastQuizScore(origem.getLastQuizScore());
            copia.setPinnedNote(origem.getPinnedNote());
            progress.save(copia);
        }

        for (SrsReview origem : reviews.findByIdUserId(modelo)) {
            SrsReview copia =
                    new SrsReview(
                            new UserNodeKey(visitante, origem.getId().getNodeId()),
                            desloca(origem.getNextReviewOn(), delta),
                            origem.getIntervalDays(),
                            origem.getEaseFactor(),
                            origem.getRepetitions());
            copia.setLastReviewedOn(desloca(origem.getLastReviewedOn(), delta));
            reviews.save(copia);
        }

        for (DrillLog origem : drills.findByUserId(modelo)) {
            drills.save(
                    new DrillLog(
                            visitante,
                            origem.getNodeId(),
                            desloca(origem.getDrilledOn(), delta),
                            origem.getRecall(),
                            origem.getNote(),
                            origem.isWasDue(),
                            desloca(origem.getDueOn(), delta),
                            desloca(origem.getCreatedAt(), delta)));
        }

        for (QuizAttempt origem : quizAttempts.findByUserIdOrderByAttemptedOnAscIdAsc(modelo)) {
            quizAttempts.save(
                    new QuizAttempt(
                            visitante,
                            origem.getNodeId(),
                            origem.getScore(),
                            origem.isPassed(),
                            desloca(origem.getAttemptedOn(), delta),
                            desloca(origem.getCreatedAt(), delta)));
        }
        log.debug(
                "Cópia da conta-modelo {} para a demonstração {} com delta de {} dia(s) em {}",
                modelo,
                visitante,
                delta,
                agora);
    }

    /**
     * Quantos dias somar a cada data, ancorado na <em>última atividade</em> da conta-modelo.
     *
     * <p>A âncora é a última coisa que aconteceu (drill, quiz, revisão, conclusão) e não a data
     * agendada mais recente: o que se quer é que "o último treino do modelo" caia hoje, e a agenda
     * futura venha junto na mesma distância. Conta-modelo sem atividade nenhuma não desloca nada —
     * não há o que ancorar, e a demonstração já vai sair vazia de qualquer jeito.
     */
    private long delta(Long modelo) {
        LocalDate hoje = LocalDate.now(clock);
        return ultimaAtividade(modelo)
                .map(ancora -> ChronoUnit.DAYS.between(ancora, hoje))
                .orElse(0L);
    }

    private Optional<LocalDate> ultimaAtividade(Long modelo) {
        return java.util.stream.Stream.of(
                        drills.findByUserId(modelo).stream().map(DrillLog::getDrilledOn),
                        quizAttempts.findByUserIdOrderByAttemptedOnAscIdAsc(modelo).stream()
                                .map(QuizAttempt::getAttemptedOn),
                        reviews.findByIdUserId(modelo).stream()
                                .map(SrsReview::getLastReviewedOn)
                                .filter(java.util.Objects::nonNull),
                        progress.findByIdUserId(modelo).stream()
                                .map(UserProgress::getCompletedAt)
                                .filter(java.util.Objects::nonNull)
                                .map(instante -> LocalDate.ofInstant(instante, clock.getZone())))
                .flatMap(stream -> stream)
                .max(LocalDate::compareTo);
    }

    private static LocalDate desloca(LocalDate data, long delta) {
        return data == null ? null : data.plusDays(delta);
    }

    private static Instant desloca(Instant instante, long delta) {
        return instante == null ? null : instante.plus(delta, ChronoUnit.DAYS);
    }

    /**
     * Aleatório e sem significado: não é derivado de nada do visitante, porque não há visitante.
     */
    private static String newSubject() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
