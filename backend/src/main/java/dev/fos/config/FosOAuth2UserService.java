package dev.fos.config;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Onde um login OAuth2 <em>sem</em> {@code openid} vira conta no app — hoje, o Facebook.
 *
 * <p>Este serviço sozinho não cobre o login: provedor que pede {@code openid} passa pelo fluxo OIDC
 * e é atendido pelo {@link FosOidcUserService}. Ver o porquê em {@link ProviderLogin}.
 */
@Component
class FosOAuth2UserService extends DefaultOAuth2UserService {

    private final ProviderLogin login;

    FosOAuth2UserService(ProviderLogin login) {
        this.login = login;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(request);
        login.register(request.getClientRegistration().getRegistrationId(), user);
        return user;
    }
}
