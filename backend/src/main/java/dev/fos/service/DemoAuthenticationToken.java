package dev.fos.service;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Sessão de quem entrou pela demonstração pública (#62).
 *
 * <p>Mesmo molde do {@link PasswordAuthenticationToken}, e pelo mesmo motivo: o {@link
 * CurrentUserProvider} resolve o usuário pelo par {@code (provider, subject)} da própria
 * autenticação, então cada forma de entrar precisa de um tipo que ele reconheça. Ficar de fora dali
 * é o defeito da #51 — o login "funciona", a sessão existe, e toda chamada responde 401 sem erro no
 * log.
 *
 * <p>O principal é o {@code providerSubject} sorteado na criação da conta, não um e-mail: a
 * demonstração é uma conta que não é de ninguém, e não tem endereço nenhum para carregar.
 */
public class DemoAuthenticationToken extends AbstractAuthenticationToken {

    /** O mesmo valor que vai em {@code user_identity.provider}. */
    public static final String PROVIDER = "demo";

    private final String subject;

    public DemoAuthenticationToken(String subject) {
        super(AuthorityUtils.createAuthorityList("ROLE_USER"));
        this.subject = subject;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        // A credencial é o próprio cookie de sessão; não há segredo a guardar aqui.
        return "";
    }

    @Override
    public Object getPrincipal() {
        return subject;
    }

    @Override
    public String getName() {
        return subject;
    }
}
