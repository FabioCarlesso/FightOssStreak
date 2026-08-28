package dev.fos.model;

/**
 * Por qual recorte a contagem diária é quebrada (#84, D50).
 *
 * <p>Formato longo — uma linha por (dia, dimensão, valor) — em vez de uma coluna por dimensão em
 * {@code usage_daily}: dimensão nova passa a ser dado, e não migration.
 */
public enum UsageDimension {

    /** Caminho normalizado da rota. */
    CAMINHO,

    /** País ISO de duas letras, ou {@code ZZ}. */
    PAIS,

    /** Celular, tablet, desktop ou desconhecido. */
    DISPOSITIVO,

    /** De onde a pessoa veio: {@code utm_source}, o host do referrer, ou {@code direto}. */
    ORIGEM,

    /** O tipo do evento — é por esta dimensão que o funil é lido. */
    EVENTO
}
