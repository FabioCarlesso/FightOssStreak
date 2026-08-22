package dev.fos.service;

/**
 * A submissão não cobre exatamente o conjunto de perguntas que o servidor serviria para a tentativa
 * corrente (issue #59, D44).
 *
 * <p>Acontece com duas abas abertas no mesmo nó, ou uma submissão antiga reenviada depois de a
 * pessoa já ter respondido de novo: a rotação avançou entre a tela ser carregada e a resposta
 * chegar. Vira 409 em vez de corrigir contra o conjunto errado — o que daria uma nota que não
 * corresponde a nada.
 */
public class QuizStaleException extends RuntimeException {

    public QuizStaleException(String code) {
        super("O quiz de " + code + " foi renovado — recarregue o nó e responda de novo.");
    }
}
