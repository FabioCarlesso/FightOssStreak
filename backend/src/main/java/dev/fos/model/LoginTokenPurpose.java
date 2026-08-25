package dev.fos.model;

/**
 * Para que serve um {@link LoginToken}.
 *
 * <p>Existe porque os três links têm prazos e poderes diferentes, e um link não pode valer pelo
 * outro: o de verificação vale 24 horas e o de redefinição vale 1 hora — sem o propósito conferido
 * no consumo, apresentar o mais longo na rota do mais curto daria ao primeiro o poder do segundo.
 */
public enum LoginTokenPurpose {

    /**
     * Link de entrada da #52, para quem não tem provedor externo.
     *
     * <p>Continua existindo enquanto a fila de aprovação existir. A fatia 3 da #81 desmonta a fila
     * e este propósito sai junto — o link deixa de ser meio de login e fica só com os dois de
     * baixo.
     */
    ENTRADA,

    /** Confirma que o endereço é mesmo de quem se cadastrou. É o que abre a primeira sessão. */
    VERIFICACAO,

    /** Redefine a senha. Não abre sessão: quem redefine entra depois, com a senha nova. */
    REDEFINICAO
}
