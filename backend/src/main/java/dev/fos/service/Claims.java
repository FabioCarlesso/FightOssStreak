package dev.fos.service;

import org.springframework.security.oauth2.jwt.Jwt;

/** Leitura das claims dos tokens de provedor, que nem sempre chegam com o mesmo tipo. */
final class Claims {

    private Claims() {}

    static String text(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    /**
     * Verdadeiro só quando a claim <b>afirma</b> verdadeiro (D63). O Google manda booleano, a Apple
     * manda às vezes a string {@code "true"}; ausente é falso.
     */
    static boolean isTrue(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        return value != null && Boolean.parseBoolean(value.toString());
    }
}
