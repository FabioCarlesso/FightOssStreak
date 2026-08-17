package dev.fos.model;

/**
 * Auto-avaliação ao registrar um drill, convertida em nota de qualidade do SM-2.
 *
 * <p>O SRS aqui não agenda revisão de teoria, e sim de <em>drill</em>: o app diz o que treinar hoje
 * (docs/04-arvore-curriculo-bjj.md). A escala é curta de propósito — quanto mais opções, menos
 * consistente a auto-avaliação.
 */
public enum Recall {
    /** Não lembrava como fazer. */
    FORGOT(2),
    /** Lembrava, mas travou / saiu errado. */
    HARD(3),
    /** Saiu, com esforço. */
    OK(4),
    /** Saiu limpo, sem pensar. */
    EASY(5);

    private final int quality;

    Recall(int quality) {
        this.quality = quality;
    }

    /** Nota 0–5 do SM-2. */
    public int quality() {
        return quality;
    }
}
