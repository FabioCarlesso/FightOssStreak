package dev.fos.model;

/**
 * Estado de um feedback na fila do dono (docs/13-feedback-usuarios.md).
 *
 * <p>Sem reabertura automática: um feedback {@link #RECUSADO} ou {@link #RESOLVIDO} continua assim
 * — quem quiser revisitar manda outro, mesmo espírito de {@code AccessStatus#RECUSADO}.
 */
public enum FeedbackStatus {
    /** Recém-enviado, ainda não olhado. */
    ABERTO,
    /** O dono está avaliando. */
    EM_ANALISE,
    RESOLVIDO,
    RECUSADO
}
