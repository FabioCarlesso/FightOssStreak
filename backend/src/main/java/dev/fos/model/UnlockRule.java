package dev.fos.model;

/**
 * Como os pré-requisitos de um nó são combinados.
 *
 * <p>{@link #ALL} é o padrão descrito em docs/arquitetura.md. {@link #ANY} existe porque
 * docs/produto/curriculo.md descreve o Módulo 4 como "pré-requisito: M3.2 <em>ou</em> M3.3
 * (qualquer passagem)" — semântica de OU que a regra ALL não expressa (decisão D13).
 */
public enum UnlockRule {
    ALL,
    ANY
}
