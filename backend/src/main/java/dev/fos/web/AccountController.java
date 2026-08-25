package dev.fos.web;

import dev.fos.model.AppUser;
import dev.fos.model.UserIdentity;
import dev.fos.service.AccountService;
import dev.fos.service.CurrentUserProvider;
import dev.fos.service.DemoAccessService;
import dev.fos.service.EmailAccessService;
import dev.fos.service.PasswordAccessService;
import dev.fos.web.dto.AccountDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A conta vista por ela mesma: com que provedores dá para entrar, quem está logado e como sumir.
 *
 * <p>Estas rotas ficam <em>fora</em> do portão de aprovação (ver {@code WebMvcConfig}): é delas que
 * a tela de "solicitação registrada" tira o que mostrar, e quem pediu acesso e não entrou precisa
 * conseguir se excluir.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Conta", description = "Login social, identidade da conta e exclusão")
public class AccountController {

    /** Rótulo do botão de login. O nome do provedor é marca — não é para traduzir nem abreviar. */
    private static final Map<String, String> LABELS =
            Map.of("google", "Google", "facebook", "Facebook", "apple", "Apple");

    private final CurrentUserProvider currentUser;
    private final AccountService accounts;
    private final EmailAccessService emailAccess;
    private final DemoAccessService demoAccess;
    private final PasswordAccessService passwordAccess;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    public AccountController(
            CurrentUserProvider currentUser,
            AccountService accounts,
            EmailAccessService emailAccess,
            DemoAccessService demoAccess,
            PasswordAccessService passwordAccess,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.emailAccess = emailAccess;
        this.demoAccess = demoAccess;
        this.passwordAccess = passwordAccess;
        this.clientRegistrations = clientRegistrations;
    }

    @GetMapping("/auth/providers")
    @Operation(
            summary = "Provedores de login habilitados",
            description =
                    "Só os que têm credencial configurada. Sem nenhum, a lista vem vazia e a tela"
                            + " de login não mostra botão — a aplicação sobe sem segredo nenhum.")
    public AccountDtos.AuthProviders providers() {
        ClientRegistrationRepository repository = clientRegistrations.getIfAvailable();
        List<AccountDtos.AuthProviderView> enabled =
                repository instanceof InMemoryClientRegistrationRepository registrations
                        ? java.util.stream.StreamSupport.stream(registrations.spliterator(), false)
                                .map(AccountController::toView)
                                .toList()
                        : List.of();
        return new AccountDtos.AuthProviders(
                enabled,
                emailAccess.isEnabled(),
                demoAccess.isEnabled(),
                passwordAccess.isEnabled());
    }

    @GetMapping("/me")
    @Operation(
            summary = "Conta autenticada, com o estado do acesso",
            description =
                    "Responde também para conta pendente ou recusada: é o estado que diz para a"
                            + " web qual tela mostrar.")
    public AccountDtos.AccountView me() {
        AppUser user = currentUser.currentUser();
        Optional<UserIdentity> identity = currentUser.currentIdentity();
        return new AccountDtos.AccountView(
                identity.map(UserIdentity::getDisplayName).orElseGet(user::getLabel),
                identity.map(UserIdentity::getEmail).orElse(null),
                identity.map(UserIdentity::getProvider).orElse(null),
                user.getAccessStatus(),
                accounts.isOwner(user),
                user.getDemoExpiresAt());
    }

    @DeleteMapping("/me")
    @Operation(
            summary = "Exclui a conta e todo o dado dela",
            description =
                    "Irreversível. Vale para conta aprovada, pendente ou recusada. A sessão é"
                            + " invalidada junto.")
    public ResponseEntity<Void> deleteMe(HttpServletRequest request) {
        accounts.delete(currentUser.currentUserId());
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        // A conta acabou de sumir do banco; deixar o contexto de segurança apontando para ela
        // faria a próxima requisição desta thread trabalhar com um usuário inexistente.
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private static AccountDtos.AuthProviderView toView(ClientRegistration registration) {
        String id = registration.getRegistrationId();
        return new AccountDtos.AuthProviderView(
                id,
                LABELS.getOrDefault(id, registration.getClientName()),
                "/api/oauth2/authorization/" + id);
    }
}
