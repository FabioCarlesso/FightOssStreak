package dev.fos.service;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Sessão de quem entrou com e-mail e senha próprios (#81).
 *
 * <p>Terceiro tipo no mesmo molde do {@link EmailAuthenticationToken} e do {@link
 * DemoAuthenticationToken}, e pela mesma razão: o {@link CurrentUserProvider} resolve o usuário
 * pelo par {@code (provider, subject)} da própria autenticação, então cada forma de entrar precisa
 * de um tipo que ele reconheça. Ficar de fora dali é o defeito da #51 — a sessão existe, o login
 * "funciona", e toda chamada responde 401 sem erro nenhum no log.
 */
public class PasswordAuthenticationToken extends AbstractAuthenticationToken {

    /** O mesmo valor que vai em {@code user_identity.provider}. */
    public static final String PROVIDER = "password";

    private final String email;

    public PasswordAuthenticationToken(String email) {
        super(AuthorityUtils.createAuthorityList("ROLE_USER"));
        this.email = email;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        // A senha foi conferida para chegar aqui; guardá-la na sessão só criaria uma cópia da
        // credencial para vazar. A partir daqui a credencial é o cookie.
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
