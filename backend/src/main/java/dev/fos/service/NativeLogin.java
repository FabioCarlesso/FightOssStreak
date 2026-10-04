package dev.fos.service;

/**
 * O que o login nativo do app tirou do token do provedor, já conferido (#139, D68).
 *
 * @param subject o {@code sub} — a metade estável da chave da identidade
 * @param email o endereço, se o provedor mandou
 * @param emailVerified se o endereço pode vincular conta e semear administração. Só é verdadeiro
 *     quando o provedor <b>afirma</b> a verificação (D63) — e, na Apple, nunca para o e-mail relay
 * @param displayName o nome, se veio
 */
public record NativeLogin(
        String subject, String email, boolean emailVerified, String displayName) {}
