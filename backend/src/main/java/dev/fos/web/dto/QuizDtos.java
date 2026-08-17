package dev.fos.web.dto;

import dev.fos.model.ProgressStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class QuizDtos {

    private QuizDtos() {}

    /**
     * Pergunta como o cliente a vê.
     *
     * <p>Não expõe qual alternativa é a correta — a correção acontece no servidor. Mandar o
     * gabarito junto transformaria o quiz em decoração.
     */
    public record QuestionView(Long id, String prompt, List<OptionView> options) {}

    public record OptionView(Long id, String label) {}

    public record QuizSubmission(@NotEmpty @Valid List<Answer> answers) {}

    public record Answer(@NotNull Long questionId, @NotNull Long optionId) {}

    /**
     * @param score 0–100
     * @param passed atingiu o mínimo para concluir o nó
     * @param status estado do nó depois da submissão
     * @param feedback explicação por pergunta — o valor de retenção está aqui, não na nota
     */
    public record QuizResult(
            int score,
            int correctCount,
            int totalQuestions,
            boolean passed,
            int passingScore,
            ProgressStatus status,
            List<QuestionFeedback> feedback) {}

    public record QuestionFeedback(
            Long questionId,
            String prompt,
            Long chosenOptionId,
            Long correctOptionId,
            boolean correct,
            String explanation) {}
}
