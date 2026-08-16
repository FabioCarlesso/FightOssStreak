package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.fos.model.DrillLog;
import dev.fos.model.ProgressStatus;
import dev.fos.model.QuizAttempt;
import dev.fos.model.Recall;
import dev.fos.model.SrsReview;
import dev.fos.model.UserNodeKey;
import dev.fos.model.UserProgress;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.QuizAttemptRepository;
import dev.fos.repo.SrsReviewRepository;
import dev.fos.repo.UserProgressRepository;
import dev.fos.web.dto.MetricsDtos;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O que estes testes travam é a definição de cada métrica — não o SQL.
 *
 * <p>Métrica mal definida é pior que métrica ausente: dá a sensação de estar medindo e leva a uma
 * conclusão errada sobre 30 dias de uso.
 */
class MvpMetricsServiceTest {

    private static final long USER = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 16);
    private static final LocalDate WINDOW_START = TODAY.minusDays(29);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);

    private DrillLogRepository drillLogRepository;
    private QuizAttemptRepository quizAttemptRepository;
    private SrsReviewRepository srsRepository;
    private UserProgressRepository progressRepository;
    private MvpMetricsService service;

    @BeforeEach
    void setUp() {
        drillLogRepository = mock(DrillLogRepository.class);
        quizAttemptRepository = mock(QuizAttemptRepository.class);
        srsRepository = mock(SrsReviewRepository.class);
        progressRepository = mock(UserProgressRepository.class);
        service = new MvpMetricsService(
                drillLogRepository, quizAttemptRepository, srsRepository, progressRepository, CLOCK);

        givenDrills();
        givenQuizAttempts();
        givenOverdueReviews();
        givenProgress();
    }

    @Test
    @DisplayName("sem uso nenhum, todas as metas aparecem como não atingidas")
    void emptyHistory() {
        MetricsDtos.MvpMetrics metrics = metrics();

        assertThat(metrics.daysWithDrill().value()).isZero();
        assertThat(metrics.daysWithDrill().met()).isFalse();
        assertThat(metrics.nodesCompleted().value()).isZero();
        assertThat(metrics.quizRetakes().value()).isZero();
        assertThat(metrics.srsAdherence().met()).isFalse();
    }

    @Test
    @DisplayName("a janela é fechada nas duas pontas: 30 dias contando com hoje")
    void windowBounds() {
        MetricsDtos.MvpMetrics metrics = metrics();

        assertThat(metrics.windowStart()).isEqualTo(WINDOW_START);
        assertThat(metrics.windowEnd()).isEqualTo(TODAY);
        assertThat(metrics.windowDays()).isEqualTo(30);
        assertThat(metrics.targetsFor30Days()).isTrue();
    }

    @Test
    @DisplayName("dias com drill contam datas distintas, não registros")
    void daysWithDrillCountsDistinctDates() {
        givenDrills(
                drill(10L, TODAY, false, null),
                drill(11L, TODAY, false, null),
                drill(12L, TODAY.minusDays(1), false, null));

        assertThat(metrics().daysWithDrill().value()).isEqualTo(2);
    }

    @Test
    @DisplayName("a meta de dias com drill é 12 em 30, como em docs/05")
    void daysWithDrillTarget() {
        List<DrillLog> drills = new ArrayList<>();
        for (int day = 0; day < 12; day++) {
            drills.add(drill(10L, TODAY.minusDays(day), false, null));
        }
        givenDrills(drills.toArray(new DrillLog[0]));

        MetricsDtos.Counted counted = metrics().daysWithDrill();
        assertThat(counted.target()).isEqualTo(12);
        assertThat(counted.met()).isTrue();
    }

    @Test
    @DisplayName("só o drill marcado como vencido conta como revisão atendida")
    void adherenceCountsOnlyDueDrills() {
        givenDrills(
                drill(10L, TODAY, true, TODAY.minusDays(1)),
                drill(11L, TODAY, false, null));

        MetricsDtos.SrsAdherence adherence = metrics().srsAdherence();
        assertThat(adherence.attended()).isEqualTo(1);
        assertThat(adherence.scheduled()).isEqualTo(1);
        assertThat(adherence.percent()).isEqualTo(100);
        assertThat(adherence.met()).isTrue();
    }

    @Test
    @DisplayName("o mesmo nó drilado duas vezes no mesmo dia conta uma revisão só")
    void adherenceDeduplicatesSameNodeSameDay() {
        givenDrills(
                drill(10L, TODAY, true, TODAY.minusDays(1)),
                drill(10L, TODAY, true, TODAY.minusDays(1)));

        assertThat(metrics().srsAdherence().attended()).isEqualTo(1);
    }

    @Test
    @DisplayName("revisão vencida e não atendida entra no denominador e derruba o percentual")
    void outstandingReviewsLowerAdherence() {
        givenDrills(drill(10L, TODAY, true, TODAY.minusDays(1)));
        givenOverdueReviews(review(20L, TODAY.minusDays(3)), review(21L, TODAY.minusDays(2)));

        MetricsDtos.SrsAdherence adherence = metrics().srsAdherence();
        assertThat(adherence.attended()).isEqualTo(1);
        assertThat(adherence.scheduled()).isEqualTo(3);
        assertThat(adherence.percent()).isEqualTo(33);
        assertThat(adherence.met()).isFalse();
        assertThat(adherence.targetPercent()).isEqualTo(60);
    }

    @Test
    @DisplayName("revisão vencida antes da janela não é cobrada de novo nesta janela")
    void outstandingBeforeWindowIsIgnored() {
        givenOverdueReviews(review(20L, WINDOW_START.minusDays(1)));

        assertThat(metrics().srsAdherence().scheduled()).isZero();
    }

    @Test
    @DisplayName("sem nada agendado na janela, a aderência é ausente em vez de zero")
    void noScheduledReviewsMeansNoPercent() {
        MetricsDtos.SrsAdherence adherence = metrics().srsAdherence();

        assertThat(adherence.scheduled()).isZero();
        assertThat(adherence.percent()).isNull();
    }

    @Test
    @DisplayName("nó concluído fora da janela não conta")
    void completedNodesRespectWindow() {
        givenProgress(
                completed(TODAY.minusDays(2)),
                completed(TODAY.minusDays(40)),
                inProgress());

        assertThat(metrics().nodesCompleted().value()).isEqualTo(1);
    }

    @Test
    @DisplayName("nó com mais de uma tentativa de quiz conta como refeito")
    void quizRetakeNeedsMoreThanOneAttempt() {
        givenQuizAttempts(
                attempt(10L, TODAY.minusDays(5)),
                attempt(10L, TODAY),
                attempt(11L, TODAY));

        MetricsDtos.Counted retakes = metrics().quizRetakes();
        assertThat(retakes.value()).isEqualTo(1);
        assertThat(retakes.met()).isTrue();
    }

    @Test
    @DisplayName("janela fora de 1..365 é recusada, e fora de 30 dias as metas deixam de valer")
    void windowValidation() {
        assertThatThrownBy(() -> service.metrics(USER, TODAY, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.metrics(USER, TODAY, 366))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(service.metrics(USER, TODAY, 7).targetsFor30Days()).isFalse();
    }

    // --- fixtures ---

    private MetricsDtos.MvpMetrics metrics() {
        return service.metrics(USER, TODAY, MvpMetricsService.DEFAULT_WINDOW_DAYS);
    }

    private void givenDrills(DrillLog... drills) {
        when(drillLogRepository.findByUserIdAndDrilledOnGreaterThanEqual(eq(USER), any()))
                .thenReturn(List.of(drills));
    }

    private void givenQuizAttempts(QuizAttempt... attempts) {
        when(quizAttemptRepository.findByUserIdAndAttemptedOnGreaterThanEqual(eq(USER), any()))
                .thenReturn(List.of(attempts));
    }

    private void givenOverdueReviews(SrsReview... reviews) {
        when(srsRepository.findByIdUserIdAndNextReviewOnLessThanEqual(eq(USER), any()))
                .thenReturn(List.of(reviews));
    }

    private void givenProgress(UserProgress... progress) {
        when(progressRepository.findByIdUserId(USER)).thenReturn(List.of(progress));
    }

    private DrillLog drill(long nodeId, LocalDate drilledOn, boolean wasDue, LocalDate dueOn) {
        return new DrillLog(USER, nodeId, drilledOn, Recall.OK, null, wasDue, dueOn, Instant.now());
    }

    private QuizAttempt attempt(long nodeId, LocalDate attemptedOn) {
        return new QuizAttempt(USER, nodeId, 100, true, attemptedOn, Instant.now());
    }

    private SrsReview review(long nodeId, LocalDate nextReviewOn) {
        return new SrsReview(new UserNodeKey(USER, nodeId), nextReviewOn, 3, 2.5, 1);
    }

    private UserProgress completed(LocalDate completedOn) {
        UserProgress progress = new UserProgress(
                new UserNodeKey(USER, completedOn.toEpochDay()), ProgressStatus.COMPLETED, Instant.now());
        progress.setCompletedAt(completedOn.atStartOfDay(ZoneOffset.UTC).toInstant());
        return progress;
    }

    private UserProgress inProgress() {
        return new UserProgress(new UserNodeKey(USER, 99L), ProgressStatus.IN_PROGRESS, Instant.now());
    }
}
