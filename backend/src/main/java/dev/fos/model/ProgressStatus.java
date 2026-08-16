package dev.fos.model;

/** Estado de um nó para um usuário. LOCKED é derivado do grafo, não persistido. */
public enum ProgressStatus {
    /** Pré-requisitos ainda não satisfeitos. */
    LOCKED,
    /** Disponível, ainda não iniciado. */
    AVAILABLE,
    /** Quiz iniciado ou drill registrado, mas ainda não concluído. */
    IN_PROGRESS,
    /** Quiz aprovado — libera os sucessores. */
    COMPLETED
}
