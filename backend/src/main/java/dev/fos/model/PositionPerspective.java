package dev.fos.model;

/**
 * De que lado o nome da posição é dito (#194, D76): "guarda" é o nome de quem está por baixo,
 * "montada" é o de quem está por cima, e "em pé" não tem lado.
 */
public enum PositionPerspective {
    BAIXO,
    CIMA,
    NEUTRA
}
