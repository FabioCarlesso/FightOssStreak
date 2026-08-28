package dev.fos.model;

/**
 * Perfil de dispositivo derivado do {@code User-Agent} (#84, D50).
 *
 * <p>Três classes e um desconhecido, e não resolução de tela ou modelo de aparelho: a pergunta que
 * isto existe para responder é "isso está sendo aberto no celular ou no desktop", e qualquer sinal
 * mais fino que isso começa a individualizar quem acessa — que é o que a D50 se comprometeu a não
 * fazer.
 */
public enum DeviceClass {
    CELULAR,
    TABLET,
    DESKTOP,
    DESCONHECIDO
}
