package dev.fos.service;

import dev.fos.model.Node;
import dev.fos.model.ProgressStatus;
import dev.fos.model.QuizOption;
import dev.fos.model.QuizQuestion;
import dev.fos.model.SrsReview;
import dev.fos.model.UserNodeKey;
import dev.fos.model.UserProgress;
import dev.fos.repo.QuizQuestionRepository;
import dev.fos.repo.SrsReviewRepository;
import dev.fos.repo.UserProgressRepository;
import dev.fos.web.dto.QuizDtos;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Correção do quiz conceitual.
 *
 * <p>A correção é sempre do lado do servidor: o cliente nunca recebe o gabarito antes de responder.
 */
@Service
public class QuizService {

    /** Nota mínima para concluir um nó. */
    public static final int PASSING_SCORE = 70;

    private final QuizQuestionRepository quizQuestionRepository;
    private final UserProgressRepository progressRepository;
    private final SrsReviewRepository srsRepository;
    private final CurriculumQueryService curriculumQueryService;
    private final SrsScheduler srsScheduler;

    public QuizService(
            QuizQuestionRepository quizQuestionRepository,
            UserProgressRepository progressRepository,
            SrsReviewRepository srsRepository,
            CurriculumQueryService curriculumQueryService,
            SrsScheduler srsScheduler) {
        this.quizQuestionRepository = quizQuestionRepository;
        this.progressRepository = progressRepository;
        this.srsRepository = srsRepository;
        this.curriculumQueryService = curriculumQueryService;
        this.srsScheduler = srsScheduler;
    }

    @Transactional
    public QuizDtos.QuizResult submit(
            Long userId, String nodeCode, QuizDtos.QuizSubmission submission, LocalDate today) {

        Node node = curriculumQueryService.requireNode(nodeCode);
        List<QuizQuestion> questions = quizQuestionRepository.findByNodeIdWithOptions(node.getId());
        if (questions.isEmpty()) {
            throw new QuizUnavailableException(nodeCode);
        }

        Map<Long, Long> answers = submission.answers().stream()
                .collect(Collectors.toMap(
                        QuizDtos.Answer::questionId, QuizDtos.Answer::optionId, (first, second) -> second));

        List<QuizDtos.QuestionFeedback> feedback = new ArrayList<>(questions.size());
        int correctCount = 0;

        for (QuizQuestion question : questions) {
            QuizOption correctOption = question.correctOption();
            Long chosen = answers.get(question.getId());
            boolean correct = chosen != null && chosen.equals(correctOption.getId());
            if (correct) {
                correctCount++;
            }
            feedback.add(new QuizDtos.QuestionFeedback(
                    question.getId(),
                    question.getPrompt(),
                    chosen,
                    correctOption.getId(),
                    correct,
                    question.getExplanation()));
        }

        int score = Math.round((correctCount * 100f) / questions.size());
        boolean passed = score >= PASSING_SCORE;
        ProgressStatus status = applyResult(userId, node, score, passed, today);

        return new QuizDtos.QuizResult(
                score, correctCount, questions.size(), passed, PASSING_SCORE, status, feedback);
    }

    private ProgressStatus applyResult(
            Long userId, Node node, int score, boolean passed, LocalDate today) {

        UserNodeKey key = new UserNodeKey(userId, node.getId());
        UserProgress progress = progressRepository
                .findById(key)
                .orElseGet(() -> new UserProgress(key, ProgressStatus.IN_PROGRESS, Instant.now()));

        boolean firstCompletion = progress.getStatus() != ProgressStatus.COMPLETED && passed;

        progress.setLastQuizScore(score);
        progress.setUpdatedAt(Instant.now());
        if (passed) {
            progress.setStatus(ProgressStatus.COMPLETED);
            if (progress.getCompletedAt() == null) {
                progress.setCompletedAt(Instant.now());
            }
        } else if (progress.getStatus() != ProgressStatus.COMPLETED) {
            // Refazer um quiz já aprovado e errar não "desconclui" o nó — desbloqueios que já
            // aconteceram não devem sumir. O score menor fica registrado e a agenda de SRS é
            // quem passa a priorizar o nó.
            progress.setStatus(ProgressStatus.IN_PROGRESS);
        }
        progressRepository.save(progress);

        if (firstCompletion) {
            scheduleFirstReview(key, today);
        }
        return progress.getStatus();
    }

    /** Concluir um nó agenda o primeiro drill de revisão — é o gancho entre quiz e SRS. */
    private void scheduleFirstReview(UserNodeKey key, LocalDate today) {
        if (srsRepository.existsById(key)) {
            return;
        }
        SrsScheduler.State state = srsScheduler.initial(today);
        srsRepository.save(new SrsReview(
                key, state.nextReviewOn(), state.intervalDays(), state.easeFactor(), state.repetitions()));
    }

    /** Mapa auxiliar usado em testes e leitura. */
    static Map<Long, QuizQuestion> byId(List<QuizQuestion> questions) {
        return questions.stream().collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));
    }
}
