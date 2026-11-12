package dev.fos.model;

/**
 * O que foi o treino (#114, D56).
 *
 * <p>Lista curta de propósito: cada item a mais é uma decisão a menos que a pessoa toma no
 * vestiário, e o campo existe para ser preenchido em um toque.
 *
 * <p>{@link #DESCANSO} é o único que carrega regra: ele é o que impede o abuso trivial da D58 — com
 * o streak contando dia com registro, um tipo "não treinei" que contasse como treino transformaria
 * a corrente em "abri o app". Dia de descanso fica no diário, e não conta.
 */
public enum SessionKind {
    AULA,
    DRILL,
    ROLA,
    FISICO,
    /** Dia anotado sem treino. Fica no diário e <b>não</b> conta como dia de treino (D58). */
    DESCANSO,
    OUTRO;

    /** Dia de treino para efeito de streak — tudo menos {@link #DESCANSO}. */
    public boolean contaComoTreino() {
        return this != DESCANSO;
    }

    /**
     * Se a sessão registra com ou sem kimono (#193, D75): só o que acontece no tatame. Físico é
     * fora dele, e descanso é dia sem treino — marcar kimono nos dois seria afirmar uma aula que
     * não houve.
     */
    public boolean registraKimono() {
        return this != FISICO && this != DESCANSO;
    }

    /** O que conta como aula para o próximo grau (#193, D75): só {@link #AULA}. */
    public boolean contaComoAula() {
        return this == AULA;
    }
}
