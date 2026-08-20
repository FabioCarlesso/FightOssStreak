package dev.fos.web;

import dev.fos.model.AppUser;
import dev.fos.model.UserIdentity;
import dev.fos.service.AccountService;
import dev.fos.web.dto.AccountDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A fila de solicitações de acesso, do lado de quem decide.
 *
 * <p>Quem pode chamar isto é decidido no portão ({@code AccessGateInterceptor}) contra {@code
 * fos.auth.owner-emails} — não há papel, tabela de roles nem tela de administração genérica. É
 * deliberado: a #24 habilita múltiplos usuários sob liberação do autor, não promove o app a
 * multiusuário público.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Solicitações", description = "Fila de acesso; restrita ao dono do app")
public class AdminController {

    private final AccountService accounts;

    public AdminController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/solicitacoes")
    @Operation(summary = "Contas aguardando liberação, da mais antiga para a mais nova")
    public AccountDtos.AccessRequests pending() {
        return queue();
    }

    @PostMapping("/solicitacoes/{id}/aprovar")
    @Operation(summary = "Libera a conta; devolve a fila já sem ela")
    public AccountDtos.AccessRequests approve(@PathVariable Long id) {
        accounts.decide(id, true);
        return queue();
    }

    @PostMapping("/solicitacoes/{id}/recusar")
    @Operation(
            summary = "Recusa a conta; devolve a fila já sem ela",
            description =
                    "A conta recusada continua recusada nos logins seguintes e só pode se excluir.")
    public AccountDtos.AccessRequests deny(@PathVariable Long id) {
        accounts.decide(id, false);
        return queue();
    }

    /** Uma consulta de identidades para a fila inteira, e não uma por linha. */
    private AccountDtos.AccessRequests queue() {
        List<AppUser> pending = accounts.pendingRequests();
        Map<Long, UserIdentity> byUser =
                accounts.identitiesOfAll(pending.stream().map(AppUser::getId).toList()).stream()
                        .collect(
                                Collectors.toMap(
                                        UserIdentity::getUserId,
                                        Function.identity(),
                                        (first, second) -> first));
        return new AccountDtos.AccessRequests(
                pending.stream().map(user -> toView(user, byUser.get(user.getId()))).toList());
    }

    private static AccountDtos.AccessRequestView toView(AppUser user, UserIdentity identity) {
        Optional<UserIdentity> found = Optional.ofNullable(identity);
        return new AccountDtos.AccessRequestView(
                user.getId(),
                found.map(UserIdentity::getDisplayName).orElseGet(user::getLabel),
                found.map(UserIdentity::getEmail).orElse(null),
                found.map(UserIdentity::getProvider).orElse(null),
                user.getRequestedAt());
    }
}
