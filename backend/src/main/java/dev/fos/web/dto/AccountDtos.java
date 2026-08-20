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
     */
    public record AccountView(
            String displayName,
            String email,
            String provider,
            AccessStatus accessStatus,
            boolean owner) {}

    /**
     * Provedor com credencial configurada.
     *
     * <p>A tela de login se monta a partir desta lista — provedor sem credencial não aparece, em
     * vez de aparecer e falhar no clique.
     *
     * @param authorizationUrl para onde o botão navega (navegação de página inteira, não fetch)
     */
    public record AuthProviderView(String id, String label, String authorizationUrl) {}

    public record AuthProviders(List<AuthProviderView> providers) {}

    /**
     * Uma solicitação de acesso na fila do dono.
     *
     * @param id id da conta; é o que vai em {@code /api/admin/solicitacoes/{id}/aprovar}
     */
    public record AccessRequestView(
            Long id, String displayName, String email, String provider, Instant requestedAt) {}

    public record AccessRequests(List<AccessRequestView> requests) {}
}
