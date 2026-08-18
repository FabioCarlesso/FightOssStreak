package dev.fos.web.dto;

import dev.fos.model.Belt;
import dev.fos.model.ProgressStatus;
import dev.fos.model.UnlockRule;
import dev.fos.model.VideoOrientation;
import java.time.LocalDate;
import java.util.List;

/** Formas de leitura do currículo expostas pela API. */
public final class CurriculumDtos {

    private CurriculumDtos() {}

    /** Árvore completa com o estado do usuário — payload da tela principal. */
    public record TreeView(List<ModuleView> modules, ProgressSummary summary) {}

    public record ModuleView(
            String code, String title, String summary, List<NodeSummaryView> nodes) {}

    /**
     * @param prereqCodes códigos dos pré-requisitos, para a UI explicar <em>por que</em> está
     *     travado
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
            LocalDate nextReviewOn) {}

    public record ProgressSummary(
            int totalNodes, int completedNodes, int availableNodes, int lockedNodes) {}

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
            List<ExtraVideoView> extraVideos,
            List<QuizDtos.QuestionView> quiz,
            SrsView srs,
            List<DrillEntryView> recentDrills,
            String safetyNotice) {}

    public record PrereqView(String code, String title, boolean completed) {}

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

    /**
     * Vídeo complementar: ajuda a lembrar a técnica, sem ser a referência dela (D32).
     *
     * <p>Tipo próprio, e não {@link VideoView} reaproveitado, porque carrega três coisas que o
     * canônico não tem. {@code orientation} decide o formato do frame — clipe de celular é 9:16 e
     * fica torto num frame 16:9. {@code note} é a anotação do curador, o único campo que não vem do
     * YouTube. E {@code thumbnailUrl} existe porque a tira mostra miniatura em vez de montar
     * player: é o que permite ver os clipes disponíveis sem carregar um {@code <iframe>} por clipe.
     *
     * <p>O {@code embedUrl} já vem com {@code autoplay} e {@code loop} porque o iframe só é montado
     * quando o usuário clica na miniatura — o clique <em>é</em> o gesto de dar play. Um clipe de
     * nove segundos em loop é assistido três vezes sem ninguém tocar em nada, que é exatamente o
     * gesto de revisão.
     */
    public record ExtraVideoView(
            String youtubeId,
            String title,
            String channel,
            Integer startSeconds,
            VideoOrientation orientation,
            String note,
            String embedUrl,
            String watchUrl,
            String thumbnailUrl) {}

    public record SrsView(
            boolean scheduled,
            LocalDate nextReviewOn,
            int intervalDays,
            int repetitions,
            boolean due) {

        public static SrsView notScheduled() {
            return new SrsView(false, null, 0, 0, false);
        }
    }

    public record DrillEntryView(LocalDate drilledOn, String recall, String note) {}
}
