package dev.fos.web.dto;

import dev.fos.model.Belt;
import dev.fos.model.ProgressStatus;
import dev.fos.model.UnlockRule;
import java.time.LocalDate;
import java.util.List;

/** Formas de leitura do currículo expostas pela API. */
public final class CurriculumDtos {

    private CurriculumDtos() {
    }

    /** Árvore completa com o estado do usuário — payload da tela principal. */
    public record TreeView(List<ModuleView> modules, ProgressSummary summary) {
    }

    public record ModuleView(String code, String title, String summary, List<NodeSummaryView> nodes) {
    }

    /**
     * @param prereqCodes códigos dos pré-requisitos, para a UI explicar <em>por que</em> está travado
     */
    public record NodeSummaryView(
            String code,
            String title,
            Belt belt,
            ProgressStatus status,
            UnlockRule unlockRule,
            List<String> prereqCodes,
            boolean hasVideo,
            int quizQuestionCount,
            Integer lastQuizScore,
            LocalDate nextReviewOn) {
    }

    public record ProgressSummary(int totalNodes, int completedNodes, int availableNodes, int lockedNodes) {
    }

    /** Detalhe de um nó: conceito, vídeo, quiz e estado de revisão. */
    public record NodeDetailView(
            String code,
            String title,
            Belt belt,
            String concept,
            String moduleCode,
            String moduleTitle,
            ProgressStatus status,
            UnlockRule unlockRule,
            List<PrereqView> prereqs,
            VideoView video,
            List<QuizDtos.QuestionView> quiz,
            SrsView srs,
            List<DrillEntryView> recentDrills,
            String safetyNotice) {
    }

    public record PrereqView(String code, String title, boolean completed) {
    }

    /**
     * Vídeo sempre por embed do player oficial (D7). {@code catalogued=false} é estado normal: a
     * curadoria é incremental e a UI mostra o nó sem vídeo em vez de esconder o conteúdo.
     */
    public record VideoView(
            boolean catalogued,
            String youtubeId,
            String title,
            String channel,
            Integer startSeconds,
            String embedUrl,
            String watchUrl) {

        public static VideoView notCatalogued() {
            return new VideoView(false, null, null, null, null, null, null);
        }
    }

    public record SrsView(
            boolean scheduled, LocalDate nextReviewOn, int intervalDays, int repetitions, boolean due) {

        public static SrsView notScheduled() {
            return new SrsView(false, null, 0, 0, false);
        }
    }

    public record DrillEntryView(LocalDate drilledOn, String recall, String note) {
    }
}
