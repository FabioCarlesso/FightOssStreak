package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fos.model.QuizQuestion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Matemática da rotação de banco de perguntas, isolada do resto de {@code QuizService} e {@code
 * CurriculumQueryService} (issue #59, D44).
 */
class QuizRotationTest {

    @Test
    @DisplayName(
            "banco de 8: a segunda tentativa não repete a primeira, e a nona volta a repeti-la")
    void firstAndSecondAttemptDisjointAndNinthRepeatsFirst() {
        List<QuizQuestion> bank = questions(8);

        List<String> first = prompts(QuizRotation.served(bank, 0));
        List<String> second = prompts(QuizRotation.served(bank, 1));
        List<String> ninth = prompts(QuizRotation.served(bank, 8));

        assertThat(first).hasSize(4);
        assertThat(second).hasSize(4);
        assertThat(second).doesNotContainAnyElementsOf(first);
        assertThat(ninth).isEqualTo(first);
    }

    @Test
    @DisplayName(
            "banco não múltiplo de 4: a fatia que cruza o fim mistura fim e começo sem repetir")
    void nonMultipleBankWrapsAroundWithoutRepeatingWithinTheAttempt() {
        List<QuizQuestion> bank = questions(10);

        // Terceira tentativa (n=2): início = (2*4) % 10 = 8, cruzando o fim do banco.
        List<String> third = prompts(QuizRotation.served(bank, 2));

        assertThat(third).hasSize(4);
        assertThat(third).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("banco menor ou igual ao tamanho da prova serve o banco inteiro, sem rotação")
    void bankSmallerThanAttemptSizeServesWholeBank() {
        List<QuizQuestion> bank = questions(3);

        assertThat(QuizRotation.served(bank, 0)).hasSize(3);
        assertThat(QuizRotation.served(bank, 5)).hasSize(3);
    }

    @Test
    @DisplayName(
            "a rotação depende do enunciado, não da ordem de chegada nem do id — estável entre "
                    + "reingestões que recriam os ids (D11)")
    void rotationIsStableAcrossReingestionBecauseKeyIsThePromptNotTheOrder() {
        List<QuizQuestion> firstIngestion = questions(8);
        List<QuizQuestion> secondIngestion = new ArrayList<>(questions(8));
        Collections.shuffle(secondIngestion);

        assertThat(prompts(QuizRotation.served(firstIngestion, 3)))
                .isEqualTo(prompts(QuizRotation.served(secondIngestion, 3)));
    }

    private List<QuizQuestion> questions(int count) {
        List<QuizQuestion> bank = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            bank.add(new QuizQuestion(null, "pergunta " + i, "explicação " + i, i));
        }
        return bank;
    }

    private List<String> prompts(List<QuizQuestion> questions) {
        return questions.stream().map(QuizQuestion::getPrompt).toList();
    }
}
