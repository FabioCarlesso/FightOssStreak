package dev.fos.config;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Onde um login OIDC vira conta no app — hoje, o Google.
 *
 * <p>Existe porque o Spring escolhe o serviço de usuário pelo escopo pedido: com {@code openid} o
 * fluxo é OIDC e quem responde é o {@code oidcUserService}; sem ele, o OAuth2 puro e o {@code
 * userService}. O Google pede {@code openid profile email}, então <b>só o {@link
 * FosOAuth2UserService} registrado não cria conta nenhuma</b> — e como autenticar continua dando
 * certo, o sintoma é o pior possível: o login "funciona", volta para o app, e toda chamada responde
 * 401 porque a identidade nunca foi gravada. Foi assim em produção.
 */
@Component
class FosOidcUserService extends OidcUserService {

    private final ProviderLogin login;

    FosOidcUserService(ProviderLogin login) {
        this.login = login;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser user = super.loadUser(request);
        login.register(request.getClientRegistration().getRegistrationId(), user);
        return user;
    }
}
