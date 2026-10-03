package dev.fos.service;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Requisição do app mobile, autenticada pelo token do cabeçalho {@code Authorization} (#139, D68).
 *
 * <p>Mesmo molde do {@link PasswordAuthenticationToken}, e pela mesma razão: o {@link
 * CurrentUserProvider} só resolve usuário para o tipo de autenticação que reconhece, e ficar de
 * fora dali é o defeito da #51.
 *
 * <p>Ao contrário das outras, esta nunca vai para sessão: vale para a requisição que trouxe o token
 * e morre com ela.
 */
public class MobileTokenAuthentication extends AbstractAuthenticationToken {

    private final Long identityId;

    public MobileTokenAuthentication(Long identityId) {
        super(AuthorityUtils.createAuthorityList("ROLE_USER"));
        this.identityId = identityId;
        setAuthenticated(true);
    }

    /** A identidade por onde a pessoa entrou no app. */
    public Long identityId() {
        return identityId;
    }

    @Override
    public Object getCredentials() {
        // O token foi conferido para chegar aqui; guardá-lo no contexto só criaria uma cópia dele.
        return "";
    }

    @Override
    public Object getPrincipal() {
        return identityId;
    }

    @Override
    public String getName() {
        return String.valueOf(identityId);
    }
}
