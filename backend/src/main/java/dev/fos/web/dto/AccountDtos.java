package dev.fos.web.dto;

import dev.fos.model.AccessStatus;
import java.time.Instant;
import java.util.List;

/** Identidade da conta, provedores de login habilitados e fila de solicitações (#24). */
public final class AccountDtos {

    private AccountDtos() {}

    /**
     * Quem está logado e em que estado.
     *
     * @param accessStatus é ele que diz para a web mostrar o app, a tela de solicitação registrada
     *     ou a de recusa
     * @param owner conta do dono: só ela enxerga a fila de solicitações
     * @param demoExpiresAt prazo da conta de demonstração (#62), nulo em conta de gente de verdade.
     *     É por ele que a faixa do topo sabe quanto tempo resta — e é o único lugar onde a web
     *     descobre que está numa demonstração, porque em tudo o mais a conta é comum
     */
    public record AccountView(
            String displayName,
            String email,
            String provider,
            AccessStatus accessStatus,
            boolean owner,
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
     * @param emailEnabled se a entrada por e-mail está disponível neste ambiente. Sem credencial de
     *     envio ela não aparece na tela, em vez de aparecer e falhar no envio
     * @param demoEnabled se há conta-modelo configurada (#62). Mesma regra: sem ela o botão da
     *     landing não aparece, em vez de aparecer e falhar no clique
     * @param passwordEnabled se dá para criar conta com e-mail e senha (#81). O cadastro <i>é</i> o
     *     e-mail de confirmação, então sem credencial de envio a porta não existe — e a tela mostra
     *     o que existe, em vez de um formulário que falha no envio
     */
    public record AuthProviders(
            List<AuthProviderView> providers,
            boolean emailEnabled,
            boolean demoEnabled,
            boolean passwordEnabled) {}

    /**
     * Uma solicitação de acesso na fila do dono.
     *
     * @param id id da conta; é o que vai em {@code /api/admin/solicitacoes/{id}/aprovar}
     */
    public record AccessRequestView(
            Long id, String displayName, String email, String provider, Instant requestedAt) {}

    public record AccessRequests(List<AccessRequestView> requests) {}
}
