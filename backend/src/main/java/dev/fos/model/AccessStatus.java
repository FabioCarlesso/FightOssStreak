package dev.fos.model;

/**
 * Estado de acesso da conta.
 *
 * <p>A D36 criou três estados porque o app era aberto sob aprovação do autor: entre "não
 * autenticado" e "usuário do app" faltava o de quem tinha entrado pelo provedor e ainda esperava
 * liberação. A D47 abriu o cadastro e a D48 desmontou a fila — {@code PENDENTE} não tem mais quem o
 * produza, e saiu.
 *
 * <p><b>{@code RECUSADO} tem produtor desde a #90</b>, e é bloqueio <em>reativo</em>: quem
 * administra bloqueia uma conta que já entrou, e a decisão vale na hora — as sessões abertas dela
 * caem junto. Não é a fila de volta, e a diferença não é de forma: a fila barrava todo mundo antes
 * de saber quem era, o bloqueio barra alguém depois de haver motivo. O portão que lê este campo
 * ({@code AccessGateInterceptor}) é o mesmo de sempre — a D48 o deixou de pé exatamente para isto.
 */
public enum AccessStatus {
    /** Usa o app normalmente. É como toda conta nasce desde a D47. */
    APROVADO,
    /**
     * Bloqueada por decisão de quem administra (#90). Só resta excluir a própria conta — e isso
     * continua podendo, de propósito: bloquear não pode virar sequestro de dado pessoal.
     */
    RECUSADO
}
