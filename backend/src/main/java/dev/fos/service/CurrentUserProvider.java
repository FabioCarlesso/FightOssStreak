package dev.fos.service;

import dev.fos.model.AppUser;
import dev.fos.repo.AppUserRepository;
import java.time.Instant;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolve o usuário atual.
 *
 * <p>No MVP existe exatamente um (D9: sem login). Esta indireção existe para que introduzir
 * autenticação depois seja trocar esta classe, e não caçar {@code userId} espalhado por serviços.
 *
 * <p>Nota para quando houver login: a Apple exige rota de exclusão de conta em apps com conta
 * (docs/01-stack-tecnica.md) — isso precisa existir antes de qualquer publicação iOS.
 */
@Component
public class CurrentUserProvider {

    private final AppUserRepository userRepository;

    public CurrentUserProvider(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public Long currentUserId() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .findFirst()
                .map(AppUser::getId)
                .orElseGet(
                        () ->
                                userRepository
                                        .save(new AppUser("usuario-local", Instant.now()))
                                        .getId());
    }
}
