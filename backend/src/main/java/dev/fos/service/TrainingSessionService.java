package dev.fos.service;

import dev.fos.model.DrillLog;
import dev.fos.model.Node;
import dev.fos.model.SessionKind;
import dev.fos.model.TrainingSession;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.NodeRepository;
import dev.fos.repo.TrainingSessionRepository;
import dev.fos.web.dto.ActivityDtos;
import dev.fos.web.dto.DiaryDtos;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O diário de treino (#114, D56): registrar o treino inteiro em um lugar só.
 *
 * <p>A sessão é a entrada e o currículo é a saída. Técnica vinculada <b>é</b> o {@code drill_log}
 * que já existia — esta classe delega ao {@link DrillService} e não reimplementa SM-2, {@code
 * was_due}/{@code due_on} nem a devolução de freeze da D55. Um segundo registro de "treinei isto"
 * daria ao SRS duas verdades sobre o mesmo fato, e só uma delas agendaria revisão.
 *
 * <p>Não há exclusão de sessão inteira, e é escopo e não esquecimento: desfazer envolveria desfazer
 * SRS, progresso e freeze. Correção é por edição.
 */
@Service
public class TrainingSessionService {

    /** Recorte padrão da linha do tempo quando a tela não pede período. */
    private static final int JANELA_PADRAO_DIAS = 90;

    /** Teto de sessões por resposta. Pedir mais devolve o teto, não um erro. */
    private static final int LIMITE_MAXIMO = 500;

    private static final int LIMITE_PADRAO = 200;

    private final TrainingSessionRepository sessions;
    private final DrillLogRepository drills;
    private final NodeRepository nodes;
    private final DrillService drillService;
    private final CurriculumQueryService curriculum;
    private final Clock clock;

    public TrainingSessionService(
            TrainingSessionRepository sessions,
            DrillLogRepository drills,
            NodeRepository nodes,
            DrillService drillService,
            CurriculumQueryService curriculum,
            Clock clock) {
        this.sessions = sessions;
        this.drills = drills;
        this.nodes = nodes;
        this.drillService = drillService;
        this.curriculum = curriculum;
        this.clock = clock;
    }

    /**
     * Cria a sessão e, se houver, registra cada técnica pelo caminho de sempre.
     *
     * <p>A sessão é gravada <b>antes</b> das técnicas porque é o id dela que cada drill carrega. E
     * o registro de cada técnica passa pelo {@link DrillService}, o que traz de graça o
     * reagendamento no SRS, o avanço de progresso e a devolução de freeze de um dia perdoado que
     * acabou tendo treino (regra (e) da D55).
     */
    @Transactional
    public DiaryDtos.SessionView create(
            Long userId, DiaryDtos.SessionRequest request, LocalDate today) {

        LocalDate trainedOn = requireNotFuture(request.trainedOn(), today);
        SessionKind kind = request.kind() != null ? request.kind() : SessionKind.AULA;
        List<DiaryDtos.TechniqueRequest> tecnicas =
                request.tecnicas() != null ? request.tecnicas() : List.of();
        if (!tecnicas.isEmpty()) {
            requireKindAcceptsTechniques(kind);
        }

        TrainingSession session = new TrainingSession(userId, trainedOn, kind, clock.instant());
        session.setDurationMinutes(request.durationMinutes());
        session.setFeeling(request.feeling());
        session.setWeightKg(request.weightKg());
        session.setLearned(trimToNull(request.learned()));
        session.setImprove(trimToNull(request.improve()));
        TrainingSession saved = sessions.save(session);

        for (DiaryDtos.TechniqueRequest tecnica : tecnicas) {
            logTechnique(userId, saved, tecnica, today);
        }

        return view(userId, saved);
    }

    /**
     * Edição dos campos da sessão. As técnicas vinculadas não são tocadas — elas têm rota própria.
     *
     * <p>Com uma exceção, e ela é a data: corrigir o dia do treino <b>move junto</b> o {@code
     * drilledOn} das técnicas daquele treino. Não é conveniência — é a única forma de o registro
     * continuar dizendo uma coisa só. O drill vinculado não entra no streak pela própria data (a
     * sessão responde pelo dia dele), então sem a propagação o {@code drill_log} ficaria afirmando
     * um dia que sumiu do diário e da corrente: treino que aconteceu, ninguém representa. É a mesma
     * invariante que a criação já estabelece, quando manda {@code trainedOn} como data do drill.
     */
    @Transactional
    public DiaryDtos.SessionView update(
            Long userId, Long sessionId, DiaryDtos.SessionPatch patch, LocalDate today) {

        TrainingSession session = require(userId, sessionId);
        SessionKind kind = patch.kind() != null ? patch.kind() : SessionKind.AULA;
        if (!kind.contaComoTreino() && drills.existsByUserIdAndSessionId(userId, sessionId)) {
            throw new IllegalArgumentException(
                    "Sessão com técnica vinculada não pode virar DESCANSO:"
                            + " desvincule as técnicas antes.");
        }

        LocalDate trainedOn = requireNotFuture(patch.trainedOn(), today);
        moveTechniques(userId, session, trainedOn);
        session.setTrainedOn(trainedOn);
        session.setKind(kind);
        session.setDurationMinutes(patch.durationMinutes());
        session.setFeeling(patch.feeling());
        session.setWeightKg(patch.weightKg());
        session.setLearned(trimToNull(patch.learned()));
        session.setImprove(trimToNull(patch.improve()));
        session.touch(clock.instant());
        return view(userId, sessions.save(session));
    }

    /** Vincula uma técnica a uma sessão já existente — o "completa depois" da D56b. */
    @Transactional
    public DiaryDtos.SessionView addTechnique(
            Long userId, Long sessionId, DiaryDtos.TechniqueRequest tecnica, LocalDate today) {

        TrainingSession session = require(userId, sessionId);
        requireKindAcceptsTechniques(session.getKind());
        logTechnique(userId, session, tecnica, today);
        return view(userId, session);
    }

    /**
     * Desvincula a técnica: {@code session_id} volta a {@code NULL} e o drill <b>permanece</b>.
     *
     * <p>Apagar o drill desfaria SRS e progresso a partir de uma tela de diário, e a nota escrita
     * nele é histórico (D34). O que a pessoa está corrigindo aqui é a qual treino aquilo pertence,
     * não que treinou.
     */
    @Transactional
    public DiaryDtos.SessionView removeTechnique(Long userId, Long sessionId, String nodeCode) {
        TrainingSession session = require(userId, sessionId);
        Node node = curriculum.requireNode(nodeCode);
        List<DrillLog> vinculados =
                drills.findByUserIdAndSessionIdAndNodeId(userId, sessionId, node.getId());
        vinculados.forEach(drill -> drill.setSessionId(null));
        drills.saveAll(vinculados);
        session.touch(clock.instant());
        sessions.save(session);
        return view(userId, session);
    }

    @Transactional(readOnly = true)
    public DiaryDtos.SessionView get(Long userId, Long sessionId) {
        return view(userId, require(userId, sessionId));
    }

    /**
     * A linha do tempo: por dia, do mais recente para o mais antigo.
     *
     * <p>Os drills avulsos entram no mesmo dia das sessões, e é o que faz os registros anteriores a
     * esta feature aparecerem no diário sem que nenhuma linha tenha sido migrada (D56a).
     */
    @Transactional(readOnly = true)
    public DiaryDtos.DiaryTimeline timeline(
            Long userId, LocalDate de, LocalDate ate, Integer limite, LocalDate today) {

        LocalDate to = ate != null ? ate : today;
        LocalDate from = de != null ? de : to.minusDays(JANELA_PADRAO_DIAS - 1L);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Período inválido: 'de' é depois de 'ate'.");
        }
        int teto = limite == null ? LIMITE_PADRAO : Math.min(LIMITE_MAXIMO, Math.max(1, limite));

        List<TrainingSession> doPeriodo =
                sessions
                        .findByUserIdAndTrainedOnBetweenOrderByTrainedOnDescIdDesc(userId, from, to)
                        .stream()
                        .limit(teto)
                        .toList();

        Map<Long, List<DrillLog>> porSessao =
                doPeriodo.isEmpty()
                        ? Map.of()
                        : drills
                                .findByUserIdAndSessionIdInOrderByIdAsc(
                                        userId,
                                        doPeriodo.stream().map(TrainingSession::getId).toList())
                                .stream()
                                .collect(Collectors.groupingBy(DrillLog::getSessionId));

        List<DrillLog> avulsos =
                drills
                        .findByUserIdAndDrilledOnBetweenOrderByDrilledOnDescIdDesc(userId, from, to)
                        .stream()
                        .filter(drill -> drill.getSessionId() == null)
                        .toList();

        Map<Long, Node> nodesById = nodesOf(concat(porSessao.values(), avulsos));

        // Ordem decrescente pela data: a linha do tempo abre no que aconteceu por último.
        Map<LocalDate, List<DiaryDtos.SessionView>> sessoesPorDia =
                new TreeMap<>(Comparator.reverseOrder());
        for (TrainingSession session : doPeriodo) {
            sessoesPorDia
                    .computeIfAbsent(session.getTrainedOn(), dia -> new ArrayList<>())
                    .add(
                            view(
                                    session,
                                    porSessao.getOrDefault(session.getId(), List.of()),
                                    nodesById));
        }

        Map<LocalDate, List<DiaryDtos.TechniqueView>> avulsosPorDia =
                new TreeMap<>(Comparator.reverseOrder());
        for (DrillLog drill : avulsos) {
            avulsosPorDia
                    .computeIfAbsent(drill.getDrilledOn(), dia -> new ArrayList<>())
                    .add(technique(drill, nodesById));
        }

        Map<LocalDate, DiaryDtos.DiaryDay> dias = new TreeMap<>(Comparator.reverseOrder());
        for (LocalDate dia : sessoesPorDia.keySet()) {
            dias.put(
                    dia,
                    day(dia, sessoesPorDia.get(dia), avulsosPorDia.getOrDefault(dia, List.of())));
        }
        for (LocalDate dia : avulsosPorDia.keySet()) {
            dias.computeIfAbsent(
                    dia, d -> day(d, List.of(), avulsosPorDia.getOrDefault(d, List.of())));
        }

        YearMonth mes = YearMonth.from(to);
        int noMes = (int) sessions.countTrainingSessions(userId, mes.atDay(1), mes.atEndOfMonth());

        return new DiaryDtos.DiaryTimeline(from, to, noMes, List.copyOf(dias.values()));
    }

    private DiaryDtos.DiaryDay day(
            LocalDate dia,
            List<DiaryDtos.SessionView> sessoes,
            List<DiaryDtos.TechniqueView> avulsos) {
        // O dia conta como treino se qualquer coisa nele conta: uma sessão que não é DESCANSO, ou
        // um drill avulso. É a mesma união que alimenta o streak (D58), aqui só para a tela poder
        // mostrar o dia de descanso sem prometer que ele mantém a corrente.
        boolean conta =
                !avulsos.isEmpty()
                        || sessoes.stream().anyMatch(DiaryDtos.SessionView::countsAsTrainingDay);
        return new DiaryDtos.DiaryDay(dia, conta, sessoes, avulsos);
    }

    private void logTechnique(
            Long userId,
            TrainingSession session,
            DiaryDtos.TechniqueRequest tecnica,
            LocalDate today) {
        drillService.log(
                userId,
                tecnica.nodeCode(),
                new ActivityDtos.DrillRequest(
                        tecnica.recall(), tecnica.note(), session.getTrainedOn()),
                today,
                session.getId());
    }

    /**
     * Leva as técnicas vinculadas para a data nova da sessão (#114).
     *
     * <p>Só escreve quando a data muda de fato: edição de peso ou de sensação — o caso comum do
     * "completa depois" — não tem por que tocar em {@code drill_log}.
     */
    private void moveTechniques(Long userId, TrainingSession session, LocalDate trainedOn) {
        if (trainedOn.equals(session.getTrainedOn())) {
            return;
        }
        List<DrillLog> vinculados =
                drills.findByUserIdAndSessionIdOrderByIdAsc(userId, session.getId());
        vinculados.forEach(drill -> drill.setDrilledOn(trainedOn));
        drills.saveAll(vinculados);
    }

    /**
     * {@code DESCANSO} é dia <b>sem</b> treino (D58): aceitar técnica nele seria dizer as duas
     * coisas ao mesmo tempo, e abriria um dia que não entra no streak mas tem drill registrado.
     */
    private void requireKindAcceptsTechniques(SessionKind kind) {
        if (!kind.contaComoTreino()) {
            throw new IllegalArgumentException(
                    "Sessão de DESCANSO não recebe técnica: descanso é dia sem treino.");
        }
    }

    private LocalDate requireNotFuture(LocalDate day, LocalDate today) {
        if (day.isAfter(today)) {
            throw new IllegalArgumentException(
                    "Não dá para registrar treino em data futura: " + day);
        }
        return day;
    }

    private TrainingSession require(Long userId, Long sessionId) {
        return sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new TrainingSessionNotFoundException(sessionId));
    }

    private DiaryDtos.SessionView view(Long userId, TrainingSession session) {
        List<DrillLog> vinculados =
                drills.findByUserIdAndSessionIdOrderByIdAsc(userId, session.getId());
        return view(session, vinculados, nodesOf(List.of(vinculados)));
    }

    private DiaryDtos.SessionView view(
            TrainingSession session, List<DrillLog> vinculados, Map<Long, Node> nodesById) {
        return new DiaryDtos.SessionView(
                session.getId(),
                session.getTrainedOn(),
                session.getKind(),
                session.getKind().contaComoTreino(),
                session.getDurationMinutes(),
                session.getFeeling(),
                session.getWeightKg(),
                session.getLearned(),
                session.getImprove(),
                vinculados.stream().map(drill -> technique(drill, nodesById)).toList());
    }

    private DiaryDtos.TechniqueView technique(DrillLog drill, Map<Long, Node> nodesById) {
        Node node = nodesById.get(drill.getNodeId());
        return new DiaryDtos.TechniqueView(
                node != null ? node.getCode() : null,
                node != null ? node.getTitle() : null,
                drill.getRecall(),
                drill.getNote(),
                drill.getDrilledOn());
    }

    /** Um SELECT para os nós de todos os drills da resposta, em vez de um por linha. */
    private Map<Long, Node> nodesOf(Iterable<? extends List<DrillLog>> grupos) {
        List<Long> ids = new ArrayList<>();
        for (List<DrillLog> grupo : grupos) {
            grupo.forEach(drill -> ids.add(drill.getNodeId()));
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return nodes.findAllById(ids).stream()
                .collect(
                        Collectors.toMap(
                                Node::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static List<List<DrillLog>> concat(
            Collection<List<DrillLog>> grupos, List<DrillLog> extra) {
        List<List<DrillLog>> todos = new ArrayList<>(grupos);
        todos.add(extra);
        return todos;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String limpo = value.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
