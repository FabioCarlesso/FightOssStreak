package dev.fos.model;

/**
 * Para que serve um {@link LoginToken}.
 *
 * <p>Existe porque os links têm prazos e poderes diferentes, e um não pode valer pelo outro: o de
 * verificação vale 24 horas e o de redefinição vale 1 hora — sem o propósito conferido no consumo,
 * apresentar o mais longo na rota do mais curto daria ao primeiro o poder do segundo.
 *
 * <p>Houve um terceiro, {@code ENTRADA}: o link que era meio de login para quem não tinha provedor
 * (#52). Ele saiu com a fila de aprovação (D48) — o link deixou de autenticar ninguém e ficou só
 * com os dois papéis abaixo. As linhas antigas continuam no banco com o valor antigo; são links
 * vencidos de um fluxo que não existe, e nenhum código os lê.
 */
public enum LoginTokenPurpose {

    /** Confirma que o endereço é mesmo de quem se cadastrou. É o que abre a primeira sessão. */
    VERIFICACAO,

    /** Redefine a senha. Não abre sessão: quem redefine entra depois, com a senha nova. */
    REDEFINICAO
}
