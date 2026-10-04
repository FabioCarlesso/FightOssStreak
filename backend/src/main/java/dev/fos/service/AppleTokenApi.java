package dev.fos.service;

import java.util.Optional;

/**
 * As duas chamadas que o backend faz à Apple (#139, D68).
 *
 * <p>Interface para os testes trocarem a rede por uma implementação em memória — o mesmo papel do
 * {@code EmailSender}.
 */
public interface AppleTokenApi {

    /** Troca o código de autorização do primeiro login por um refresh token. */
    Optional<String> exchange(String authorizationCode);

    /** Revoga o refresh token — a Apple deixa de considerar o app autorizado por aquela pessoa. */
    void revoke(String refreshToken);
}
