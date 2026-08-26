package dev.fos.model;

/**
 * O que uma conta pode fazer no app (D48, D49).
 *
 * <p>São dois papéis, e <b>dois continua sendo o recorte</b>: o que mudou na D49 foi a origem, não
 * a granularidade. Até então o papel era calculado a cada requisição a partir de {@code
 * fos.auth.owner-emails}, e a consequência era concreta — administrador novo exigia deploy. Agora
 * ele é dado ({@code app_user.role}), administrável por {@code POST /api/admin/usuarios/{id}/role},
 * e a variável virou <b>semente</b>: promove na subida e é a saída de emergência de um ambiente que
 * ficou sem ninguém que administre.
 *
 * <p>Quem decide continua sendo um ponto só ({@code AccountService.roleOf}), e a exigência de
 * e-mail verificado continua valendo — aplicada agora onde a promoção acontece, e não a cada
 * leitura. <b>Permissão granular segue fora de escopo</b>: perfil por recurso é infra para um
 * problema que ainda não existe. O critério para reabrir isso é uma permissão que não seja "tudo".
 */
public enum Role {
    /** Vê e decide o que é do app: contas, métricas e fila de feedback. */
    ADMIN,
    /** Todo mundo. É o papel de quem cria conta — que, desde a D47, é qualquer um. */
    USUARIO
}
