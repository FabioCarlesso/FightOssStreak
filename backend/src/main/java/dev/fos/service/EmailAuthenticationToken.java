package dev.fos.service;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Sessão de quem entrou por link de e-mail.
 *
 * <p>Existe para o {@link CurrentUserProvider} poder resolver o usuário sem saber de OAuth. É a
 * costura que quebrou produção na #51 quando ficou de fora: autenticação de tipo não previsto ali
 * faz o login "funcionar" e toda chamada responder 401.
 */
public class EmailAuthenticationToken extends AbstractAuthenticationToken {

    /** O mesmo valor que vai em {@code user_identity.provider}. */
    public static final String PROVIDER = "email";

    private final String email;

    public EmailAuthenticationToken(String email) {
        super(AuthorityUtils.createAuthorityList("ROLE_USER"));
        this.email = email;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        // O token já foi consumido para chegar aqui; guardá-lo na sessão só criaria uma cópia da
        // credencial para vazar.
        return "";
    }

    @Override
    public Object getPrincipal() {
        return email;
    }

    @Override
    public String getName() {
        return email;
    }
}
