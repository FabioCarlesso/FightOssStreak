package dev.fos.web;

import dev.fos.service.CurrentUserProvider;
import dev.fos.service.TrainingSessionService;
import dev.fos.web.dto.DiaryDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Diário de treino (#114, D56): a sessão como unidade do que aconteceu no tatame.
 *
 * <p>Não há {@code DELETE} de sessão, e é escopo declarado: desfazer uma sessão inteira envolveria
 * desfazer SRS, progresso e freeze. Correção é por edição — e desvincular técnica, que é a parte
 * que de fato se erra, tem rota própria.
 */
@RestController
@RequestMapping("/api/sessoes")
@Tag(name = "Diário", description = "Sessões de treino e vínculo de técnicas do currículo")
public class DiaryController {

    private final TrainingSessionService sessoes;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public DiaryController(
            TrainingSessionService sessoes, CurrentUserProvider currentUser, Clock clock) {
        this.sessoes = sessoes;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @PostMapping
    @Operation(summary = "Registra um treino — só a data é obrigatória")
    public DiaryDtos.SessionView create(@Valid @RequestBody DiaryDtos.SessionRequest request) {
        return sessoes.create(currentUser.currentUserId(), request, today());
    }

    @GetMapping
    @Operation(summary = "Linha do tempo do diário, por dia, do mais recente para o mais antigo")
    public DiaryDtos.DiaryTimeline timeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate ate,
            @RequestParam(required = false) Integer limite) {
        return sessoes.timeline(currentUser.currentUserId(), de, ate, limite, today());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe de uma sessão")
    public DiaryDtos.SessionView get(@PathVariable Long id) {
        return sessoes.get(currentUser.currentUserId(), id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Edita os campos da sessão; as técnicas vinculadas não são tocadas")
    public DiaryDtos.SessionView update(
            @PathVariable Long id, @Valid @RequestBody DiaryDtos.SessionPatch patch) {
        return sessoes.update(currentUser.currentUserId(), id, patch, today());
    }

    @PostMapping("/{id}/tecnicas")
    @Operation(summary = "Vincula uma técnica do currículo — alimenta o SRS pelo caminho de sempre")
    public DiaryDtos.SessionView addTechnique(
            @PathVariable Long id, @Valid @RequestBody DiaryDtos.TechniqueRequest tecnica) {
        return sessoes.addTechnique(currentUser.currentUserId(), id, tecnica, today());
    }

    @DeleteMapping("/{id}/tecnicas/{code}")
    @Operation(summary = "Desvincula a técnica da sessão; o drill permanece no histórico do nó")
    public DiaryDtos.SessionView removeTechnique(@PathVariable Long id, @PathVariable String code) {
        return sessoes.removeTechnique(currentUser.currentUserId(), id, code);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
