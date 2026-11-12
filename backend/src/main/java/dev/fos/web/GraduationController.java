package dev.fos.web;

import dev.fos.service.CurrentUserProvider;
import dev.fos.service.GraduationService;
import dev.fos.web.dto.GraduationDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Próximo grau (#193, D75): a meta de aulas que a academia exige e o que o diário já contou.
 *
 * <p>{@code PUT} e não {@code POST}: é uma linha por conta, e gravar duas vezes o mesmo corpo deixa
 * o mesmo estado.
 */
@RestController
@RequestMapping("/api/graduacao")
@Tag(name = "Graduação", description = "Meta do próximo grau e aulas registradas desde o último")
public class GraduationController {

    private final GraduationService graduacao;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public GraduationController(
            GraduationService graduacao, CurrentUserProvider currentUser, Clock clock) {
        this.graduacao = graduacao;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping
    @Operation(
            summary = "Meta do próximo grau e aulas desde o último",
            description = "Sem meta configurada, `goal` e `classes` vêm nulos.")
    public GraduationDtos.GraduationView graduation() {
        return graduacao.get(currentUser.currentUserId());
    }

    @PutMapping
    @Operation(summary = "Grava a meta do próximo grau — só a data do último grau é obrigatória")
    public GraduationDtos.GraduationView saveGoal(
            @Valid @RequestBody GraduationDtos.GoalRequest request) {
        return graduacao.save(currentUser.currentUserId(), request, LocalDate.now(clock));
    }

    @DeleteMapping
    @Operation(summary = "Remove a meta; as sessões do diário não são tocadas")
    public ResponseEntity<Void> deleteGoal() {
        graduacao.delete(currentUser.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
