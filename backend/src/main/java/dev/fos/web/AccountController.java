package dev.fos.web;

import dev.fos.model.AppUser;
import dev.fos.model.UserIdentity;
import dev.fos.service.AccountService;
import dev.fos.service.AppleSignIn;
import dev.fos.service.CurrentUserProvider;
import dev.fos.service.DemoAccessService;
import dev.fos.service.GoogleIdTokens;
import dev.fos.service.PasswordAccessService;
import dev.fos.web.dto.AccountDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
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
 * a tela de entrada tira o que oferecer, e quem quiser sumir precisa conseguir se excluir sem
 * passar por portão nenhum.
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
    private final DemoAccessService demoAccess;
    private final PasswordAccessService passwordAccess;
    private final GoogleIdTokens googleIdTokens;
    private final AppleSignIn appleSignIn;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    public AccountController(
            CurrentUserProvider currentUser,
            AccountService accounts,
            DemoAccessService demoAccess,
            PasswordAccessService passwordAccess,
            GoogleIdTokens googleIdTokens,
            AppleSignIn appleSignIn,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.demoAccess = demoAccess;
        this.passwordAccess = passwordAccess;
        this.googleIdTokens = googleIdTokens;
        this.appleSignIn = appleSignIn;
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
        List<String> mobile = new ArrayList<>();
        if (googleIdTokens.isEnabled()) {
            mobile.add("google");
        }
        if (appleSignIn.isEnabled()) {
            mobile.add("apple");
        }
        return new AccountDtos.AuthProviders(
                enabled, demoAccess.isEnabled(), passwordAccess.isEnabled(), List.copyOf(mobile));
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
                accounts.roleOf(user),
                user.getDemoExpiresAt());
    }

    @DeleteMapping("/me")
    @Operation(
            summary = "Exclui a conta e todo o dado dela",
            description =
                    "Irreversível: apaga a conta, a identidade, o hash da senha, os links"
                            + " pendentes, os tokens do app e todo o progresso, e revoga na Apple o"
                            + " acesso de quem entrou por ela. A sessão é invalidada junto.")
    public ResponseEntity<Void> deleteMe(HttpServletRequest request) {
        Long userId = currentUser.currentUserId();
        // Antes de apagar, e fora da transação da exclusão: a revogação é chamada de rede, e é
        // melhor esforço — a Apple fora do ar não pode prender a conta aqui (#139, D68).
        appleSignIn.revokeAllOf(userId);
        accounts.delete(userId);
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
