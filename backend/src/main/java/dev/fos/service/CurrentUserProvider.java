package dev.fos.service;

import dev.fos.model.AppUser;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.UserIdentityRepository;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolve o usuário atual a partir da autenticação.
 *
 * <p>Esta classe é o ponto de costura que a versão sem login já previa: introduzir autenticação foi
 * trocá-la, e não caçar {@code userId} espalhado por serviços. O que ela deixou de fazer importa
 * tanto quanto o que faz — não existe mais criação implícita de usuário. Sem sessão é 401; criar
 * conta é consequência de um login bem-sucedido (ver {@link AccountService#registerLogin}).
 *
 * <p>A identidade é buscada pelo par {@code (provider, subject)} da própria autenticação, o que
 * mantém a resolução idêntica no fluxo real e em teste.
 */
@Component
public class CurrentUserProvider {

    private final UserIdentityRepository identities;
    private final AppUserRepository users;

    public CurrentUserProvider(UserIdentityRepository identities, AppUserRepository users) {
        this.identities = identities;
        this.users = users;
    }

    /** Id do usuário autenticado. Lança 401 quando não há sessão — nunca cria conta. */
    @Transactional(readOnly = true)
    public Long currentUserId() {
        return currentUser().getId();
    }

    @Transactional(readOnly = true)
    public AppUser currentUser() {
        return findCurrentUser().orElseThrow(UnauthenticatedException::new);
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findCurrentUser() {
        return currentIdentity().flatMap(identity -> users.findById(identity.getUserId()));
    }

    @Transactional(readOnly = true)
    public Optional<UserIdentity> currentIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken token)
                || !token.isAuthenticated()) {
            return Optional.empty();
        }
        return identities.findByProviderAndProviderSubject(
                token.getAuthorizedClientRegistrationId(), token.getPrincipal().getName());
    }
}
