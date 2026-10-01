package dev.fos.config;

import dev.fos.service.AccountService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Um login bem-sucedido no provedor virando conta no app.
 *
 * <p>Mora fora dos dois serviços de usuário de propósito. O Spring escolhe entre eles pelo escopo
 * pedido — {@code openid} leva ao fluxo OIDC, o resto ao OAuth2 puro —, e o app precisa reagir
 * igual nos dois. Com a regra em um lugar só, não existe a versão do fluxo que "esquece" de criar a
 * conta.
 *
 * <p>Ler por atributo funciona nos dois porque {@code OidcUser} <em>é</em> um {@code OAuth2User}:
 * as claims do id token aparecem como atributos.
 */
@Component
class ProviderLogin {

    private final AccountService accounts;

    ProviderLogin(AccountService accounts) {
        this.accounts = accounts;
    }

    void register(String provider, OAuth2User user) {
        // getName() já respeita o atributo de identificação de cada provedor (`sub` no Google,
        // `id` no Facebook) — é o subject, a metade estável da chave da identidade.
        accounts.registerLogin(
                provider,
                user.getName(),
                attribute(user, "email"),
                isEmailVerified(user),
                attribute(user, "name"));
    }

    private static String attribute(OAuth2User user, String name) {
        Object value = user.getAttribute(name);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    /**
     * Verificado só quando o provedor <b>afirma</b> a verificação (D63, FOS-03).
     *
     * <p>O Google manda {@code email_verified} no id token. O Facebook não manda nada equivalente,
     * e até aqui a ausência valia como "sim" — a garantia passava a ser do provedor e não estava
     * escrita em lugar nenhum, e é desse booleano que saem o vínculo por e-mail (D47) e a semente
     * de administração (D49). Atributo ausente é não verificado: o login funciona, só não vincula
     * conta nem promove ninguém. Sem e-mail, não há o que verificar.
     */
    static boolean isEmailVerified(OAuth2User user) {
        if (attribute(user, "email") == null) {
            return false;
        }
        Object flag = user.getAttribute("email_verified");
        return flag != null && Boolean.parseBoolean(flag.toString());
    }
}
