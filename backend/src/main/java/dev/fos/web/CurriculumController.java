package dev.fos.web;

import dev.fos.service.CurrentUserProvider;
import dev.fos.service.CurriculumQueryService;
import dev.fos.service.DrillService;
import dev.fos.service.NodeNoteService;
import dev.fos.service.QuizService;
import dev.fos.web.dto.ActivityDtos;
import dev.fos.web.dto.CurriculumDtos;
import dev.fos.web.dto.QuizDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Currículo", description = "Árvore de currículo, nós, quiz e registro de drill")
public class CurriculumController {

    private final CurriculumQueryService curriculumQueryService;
    private final QuizService quizService;
    private final DrillService drillService;
    private final NodeNoteService nodeNoteService;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public CurriculumController(
            CurriculumQueryService curriculumQueryService,
            QuizService quizService,
            DrillService drillService,
            NodeNoteService nodeNoteService,
            CurrentUserProvider currentUser,
            Clock clock) {
        this.curriculumQueryService = curriculumQueryService;
        this.quizService = quizService;
        this.drillService = drillService;
        this.nodeNoteService = nodeNoteService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping("/curriculum/tree")
    @Operation(summary = "Árvore completa com estado de bloqueio resolvido para o usuário")
    public CurriculumDtos.TreeView tree() {
        return curriculumQueryService.tree(currentUser.currentUserId());
    }

    @GetMapping("/nodes/{code}")
    @Operation(summary = "Detalhe de um nó: conceito, vídeo, quiz e agenda de revisão")
    public CurriculumDtos.NodeDetailView node(@PathVariable String code) {
        return curriculumQueryService.nodeDetail(currentUser.currentUserId(), code, today());
    }

    @PostMapping("/nodes/{code}/quiz")
    @Operation(summary = "Submete o quiz conceitual e devolve nota e explicações")
    public QuizDtos.QuizResult submitQuiz(
            @PathVariable String code, @Valid @RequestBody QuizDtos.QuizSubmission submission) {
        return quizService.submit(currentUser.currentUserId(), code, submission, today());
    }

    @PostMapping("/nodes/{code}/drill")
    @Operation(summary = "Registra 'treinei isso hoje' — alimenta streak e reagenda o SRS")
    public ActivityDtos.DrillResult logDrill(
            @PathVariable String code, @Valid @RequestBody ActivityDtos.DrillRequest request) {
        return drillService.log(currentUser.currentUserId(), code, request, today());
    }

    /**
     * {@code PUT} e não {@code POST}: a anotação fixada é um valor que se substitui, não um evento
     * que se acumula — o oposto do drill, que é log. Reenviar o mesmo corpo tem de deixar o mesmo
     * estado.
     */
    @PutMapping("/nodes/{code}/note")
    @Operation(summary = "Grava a anotação fixada do nó; nota em branco limpa a anotação")
    public CurriculumDtos.PinnedNoteView savePinnedNote(
            @PathVariable String code,
            @Valid @RequestBody CurriculumDtos.PinnedNoteRequest request) {
        return nodeNoteService.save(currentUser.currentUserId(), code, request.note());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
