package dev.fos.service;

import dev.fos.model.QuizQuestion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Escolhe qual fatia do banco de perguntas de um nó é servida em cada tentativa (issue #59, D44).
 *
 * <p>As perguntas do nó são ordenadas por uma chave estável derivada do <em>enunciado</em>, não do
 * id: {@code CurriculumIngestionService.replaceQuizzes()} apaga e recria todas as perguntas a cada
 * sincronização do currículo (D11), então uma chave por id embaralharia a rotação a cada deploy. O
 * enunciado é a definição certa de "a mesma pergunta" enquanto ele não mudar.
 *
 * <p>A tentativa {@code attemptNumber} (o número de tentativas anteriores do usuário naquele nó, já
 * gravadas em {@code quiz_attempt}) serve a fatia de {@link #QUESTIONS_PER_ATTEMPT} perguntas que
 * começa em {@code (attemptNumber * QUESTIONS_PER_ATTEMPT) % tamanhoDoBanco}, dando a volta quando
 * a fatia cruza o fim do banco — é isso que mistura fim e começo quando o banco não é múltiplo de
 * 4.
 */
final class QuizRotation {

    /**
     * Perguntas servidas por tentativa. Mantém a nota de corte de 70 (D27, revisitada pela D44): 3
     * acertos em 4 dão 75 e passam; 2 em 4 dão 50 e reprovam. O banco pode — e deve — ter mais
     * perguntas que isso; é o que a rotação existe para cobrir.
     */
    static final int QUESTIONS_PER_ATTEMPT = 4;

    private QuizRotation() {}

    /** Fatia do banco servida na tentativa de número {@code attemptNumber} (0 = primeira vez). */
    static List<QuizQuestion> served(List<QuizQuestion> bank, long attemptNumber) {
        if (bank.size() <= QUESTIONS_PER_ATTEMPT) {
            return List.copyOf(bank);
        }

        List<QuizQuestion> ordered =
                bank.stream()
                        .sorted(
                                Comparator.comparingLong(
                                                (QuizQuestion question) ->
                                                        SplitMix.finalize(
                                                                question.getPrompt().hashCode()))
                                        .thenComparing(QuizQuestion::getPrompt))
                        .toList();

        int size = ordered.size();
        long start = (attemptNumber % size) * QUESTIONS_PER_ATTEMPT % size;
        List<QuizQuestion> served = new ArrayList<>(QUESTIONS_PER_ATTEMPT);
        for (int i = 0; i < QUESTIONS_PER_ATTEMPT; i++) {
            served.add(ordered.get((int) ((start + i) % size)));
        }
        return served;
    }
}
