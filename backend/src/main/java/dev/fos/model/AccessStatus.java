package dev.fos.model;

/**
 * Estado de acesso da conta (#24).
 *
 * <p>Existe porque autenticar não é liberar: o app é aberto sob aprovação do autor, e entre "não
 * autenticado" e "usuário do app" falta justamente o estado de quem entrou pelo provedor e ainda
 * não foi liberado.
 */
public enum AccessStatus {
    /** Entrou pelo provedor e aguarda liberação. Não lê nem escreve estado de usuário. */
    PENDENTE,
    /** Liberado: usa o app normalmente. */
    APROVADO,
    /** Recusado. Continua recusado nos logins seguintes; só resta excluir a própria conta. */
    RECUSADO
}
