package dev.fos.web.dto;

import dev.fos.model.AccessStatus;
import dev.fos.model.Role;
import java.time.Instant;
import java.util.List;

/** Identidade da conta e formas de entrar habilitadas. */
public final class AccountDtos {

    private AccountDtos() {}

    /**
     * Quem está logado e em que estado.
     *
     * @param accessStatus estado do acesso. Desde a D48 só há {@code APROVADO} em circulação — o
     *     campo fica porque o bloqueio reativo, se um dia entrar, entra por ele
     * @param role {@code ADMIN} ou {@code USUARIO} (D48). É <b>o</b> campo que a web checa para
     *     decidir o que mostrar de administração; antes ela lia um booleano chamado {@code owner},
     *     que descrevia a fila de acesso e não o que a conta pode fazer
     * @param demoExpiresAt prazo da conta de demonstração (#62), nulo em conta de gente de verdade.
     *     É por ele que a faixa do topo sabe quanto tempo resta — e é o único lugar onde a web
     *     descobre que está numa demonstração, porque em tudo o mais a conta é comum
     */
    public record AccountView(
            String displayName,
            String email,
            String provider,
            AccessStatus accessStatus,
            Role role,
            Instant demoExpiresAt) {}

    /**
     * Provedor com credencial configurada.
     *
     * <p>A tela de login se monta a partir desta lista — provedor sem credencial não aparece, em
     * vez de aparecer e falhar no clique.
     *
     * @param authorizationUrl para onde o botão navega (navegação de página inteira, não fetch)
     */
    public record AuthProviderView(String id, String label, String authorizationUrl) {}

    /**
     * @param demoEnabled se há conta-modelo configurada (#62). Sem ela o botão da landing não
     *     aparece, em vez de aparecer e falhar no clique
     * @param passwordEnabled se dá para criar conta com e-mail e senha (D47). O cadastro <i>é</i> o
     *     e-mail de confirmação, então sem credencial de envio a porta não existe — e a tela mostra
     *     o que existe, em vez de um formulário que falha no envio
     */
    public record AuthProviders(
            List<AuthProviderView> providers, boolean demoEnabled, boolean passwordEnabled) {}
}
