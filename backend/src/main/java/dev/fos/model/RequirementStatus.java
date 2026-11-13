package dev.fos.model;

/**
 * Status de um requisito do próximo grau (#196, D78). Derivado a cada leitura, nunca gravado.
 *
 * <p>A regra pura é {@code requirementStatus}, em {@code shared/domain} (D17), espelhada em {@code
 * GraduationRequirementService.statusOf}.
 */
public enum RequirementStatus {
    PENDENTE,
    EM_ANDAMENTO,
    CONCLUIDO
}
