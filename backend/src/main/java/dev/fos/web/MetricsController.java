package dev.fos.web;

import dev.fos.service.CurrentUserProvider;
import dev.fos.service.MvpMetricsService;
import dev.fos.web.dto.MetricsDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Métricas", description = "Critérios de sucesso do MVP medidos sobre o uso real")
public class MetricsController {

    private final MvpMetricsService metricsService;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public MetricsController(
            MvpMetricsService metricsService, CurrentUserProvider currentUser, Clock clock) {
        this.metricsService = metricsService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping("/metrics/mvp")
    @Operation(
            summary = "Os quatro critérios de sucesso do MVP, com valor e meta",
            description = "As metas são as de docs/05-mvp-web-plano.md e valem para a janela de 30 dias.")
    public MetricsDtos.MvpMetrics mvp(
            @RequestParam(defaultValue = "" + MvpMetricsService.DEFAULT_WINDOW_DAYS) int days) {
        return metricsService.metrics(currentUser.currentUserId(), LocalDate.now(clock), days);
    }
}
