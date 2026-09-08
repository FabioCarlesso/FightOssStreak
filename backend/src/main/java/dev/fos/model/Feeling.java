package dev.fos.model;

/**
 * Como o corpo respondeu (#114, D57).
 *
 * <p>Três níveis, e nenhum deles vira conselho: o app guarda e mostra, nunca interpreta. Sem meta,
 * sem alerta e sem "descanse amanhã" — sensação puxa recomendação de treino, e o FOS não aconselha
 * (D1, docs/06).
 */
public enum Feeling {
    BEM,
    NEUTRO,
    MAL
}
