package dev.fos.web;

import dev.fos.service.CurrentUserProvider;
import dev.fos.service.DisclaimerService;
import dev.fos.service.ReviewAgendaService;
import dev.fos.service.StreakQueryService;
import dev.fos.web.dto.ActivityDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Atividade", description = "Streak, agenda de revisão e aceite do aviso")
public class ActivityController {

    private final StreakQueryService streakQueryService;
    private final ReviewAgendaService reviewAgendaService;
    private final DisclaimerService disclaimerService;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public ActivityController(
            StreakQueryService streakQueryService,
            ReviewAgendaService reviewAgendaService,
            DisclaimerService disclaimerService,
            CurrentUserProvider currentUser,
            Clock clock) {
        this.streakQueryService = streakQueryService;
        this.reviewAgendaService = reviewAgendaService;
        this.disclaimerService = disclaimerService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping("/streak")
    @Operation(summary = "Streak atual, recorde e dias ativos nos últimos 30")
    public ActivityDtos.StreakView streak() {
        return streakQueryService.streak(currentUser.currentUserId(), today());
    }

    @GetMapping("/reviews/today")
    @Operation(summary = "O que drillar hoje, do mais atrasado para o menos")
    public ActivityDtos.ReviewAgenda reviewsToday() {
        return reviewAgendaService.dueToday(currentUser.currentUserId(), today());
    }

    @GetMapping("/disclaimer")
    @Operation(summary = "Estado do aceite do aviso de responsabilidade")
    public ActivityDtos.DisclaimerStatus disclaimer() {
        return disclaimerService.status(currentUser.currentUserId());
    }

    @PostMapping("/disclaimer/accept")
    @Operation(summary = "Registra o aceite com data e versão do texto")
    public ActivityDtos.DisclaimerStatus acceptDisclaimer(
            @Valid @RequestBody ActivityDtos.AcceptDisclaimerRequest request) {
        return disclaimerService.accept(currentUser.currentUserId(), request.version());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
