package dev.fos.model;

/**
 * Estado de acesso da conta.
 *
 * <p>A D36 criou três estados porque o app era aberto sob aprovação do autor: entre "não
 * autenticado" e "usuário do app" faltava o de quem tinha entrado pelo provedor e ainda esperava
 * liberação. A D47 abriu o cadastro e a D48 desmontou a fila — {@code PENDENTE} não tem mais quem o
 * produza, e saiu.
 *
 * <p><b>{@code RECUSADO} fica, e hoje ninguém o produz.</b> É o risco que a D48 assume com todas as
 * letras: um app de cadastro aberto sem nenhuma forma de barrar uma conta abusiva. O estado
 * permanece porque o portão que o lê ({@code AccessGateInterceptor}) é o lugar certo para o
 * bloqueio reativo entrar quando isso incomodar — e porque devolver a fila seria a resposta errada
 * para esse problema.
 */
public enum AccessStatus {
    /** Usa o app normalmente. É como toda conta nasce desde a D47. */
    APROVADO,
    /** Bloqueada. Só resta excluir a própria conta. Nada no app produz este estado hoje. */
    RECUSADO
}
