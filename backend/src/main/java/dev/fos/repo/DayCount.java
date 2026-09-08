package dev.fos.repo;

import java.time.LocalDate;

/**
 * Quantos registros num dia — a linha que as consultas agregadas do heatmap devolvem (#102).
 *
 * <p>Record e não projeção por interface porque o que sai daqui é somado logo em seguida: as duas
 * consultas (sessões e drills avulsos) devolvem o mesmo formato e o serviço as funde num mapa. Um
 * tipo só evita a conversão intermediária, e {@code Long} em vez de {@code long} porque é o que a
 * expressão de construtor do JPQL entrega para {@code count()}.
 */
public record DayCount(LocalDate dia, Long total) {}
