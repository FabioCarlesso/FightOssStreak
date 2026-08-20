package dev.fos.config;

import dev.fos.service.AccountService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Ponto onde um login bem-sucedido no provedor vira conta no app.
 *
 * <p>É aqui, e não numa consulta espalhada pelo código, que {@code app_user} e {@code
 * user_identity} nascem: sem passar pelo provedor não existe conta.
 */
@Component
class FosOAuth2UserService extends DefaultOAuth2UserService {

    private final AccountService accounts;

    FosOAuth2UserService(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(request);
        String provider = request.getClientRegistration().getRegistrationId();
        // getName() já respeita o atributo de identificação de cada provedor (`sub` no Google,
        // `id` no Facebook) — é o subject, a metade estável da chave da identidade.
        accounts.registerLogin(
                provider,
                user.getName(),
                attribute(user, "email"),
                isEmailVerified(user),
                attribute(user, "name"));
        return user;
    }

    private static String attribute(OAuth2User user, String name) {
        Object value = user.getAttribute(name);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    /**
     * O Google manda {@code email_verified}; o Facebook não manda nada equivalente porque só
     * devolve e-mail já confirmado por ele. Sem e-mail, não há o que verificar.
     */
    private static boolean isEmailVerified(OAuth2User user) {
        if (attribute(user, "email") == null) {
            return false;
        }
        Object flag = user.getAttribute("email_verified");
        return flag == null || Boolean.parseBoolean(flag.toString());
    }
}
