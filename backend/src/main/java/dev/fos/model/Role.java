package dev.fos.model;

/**
 * O que uma conta pode fazer no app (D48).
 *
 * <p>São dois papéis, e **dois é o recorte, não um estágio**: com a fila de aprovação desmontada,
 * {@code fos.auth.owner-emails} deixou de significar "vê a fila e decide" e passou a significar
 * simplesmente "é a conta de administração" — métricas, fila de feedback e o que vier. Nomear o
 * conceito custa este enum; construir a máquina (tabela de papéis, tela de gestão, permissão
 * granular) custaria muito mais e resolveria um problema que ainda não existe.
 *
 * <p>A origem continua sendo configuração, e quem decide continua sendo um ponto só ({@code
 * AccountService.roleOf}). O critério para isto virar tabela está na D48: um segundo administrador,
 * ou uma permissão que não seja "tudo".
 */
public enum Role {
    /** Vê e decide o que é do app: métricas e fila de feedback. */
    ADMIN,
    /** Todo mundo. É o papel de quem cria conta — que, desde a D47, é qualquer um. */
    USUARIO
}
