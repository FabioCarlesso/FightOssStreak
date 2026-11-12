package dev.fos.repo;

import dev.fos.model.Attire;

/**
 * Quantas aulas com um tipo de kimono — a linha da contagem do próximo grau (#193).
 *
 * <p>{@code attire} nulo é a linha das aulas sem marcação, e ela precisa sair da consulta: são
 * aulas que contam no total mesmo sem dizer se foram com ou sem kimono.
 */
public record AttireCount(Attire attire, Long total) {}
