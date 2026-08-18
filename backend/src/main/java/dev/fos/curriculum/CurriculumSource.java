package dev.fos.curriculum;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Espelho fiel dos arquivos JSON de currículo.
 *
 * <p>Estes records existem para não espalhar leitura de JSON solto pelo código: o formato do
 * arquivo é declarado uma vez, aqui, e validado em {@link CurriculumValidator}.
 */
public final class CurriculumSource {

    private CurriculumSource() {}

    /** Conteúdo de {@code modules.json}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Index(String version, String scope, List<ModuleRef> modules) {}

    public record ModuleRef(int order, String file) {}

    /** Conteúdo de {@code m0.json} … {@code m8.json}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Module(String code, String title, String summary, List<NodeSpec> nodes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NodeSpec(
            String code,
            String title,
            String belt,
            int order,
            String unlockRule,
            List<String> prereqs,
            String concept,
            VideoSpec video,
            List<ExtraVideoSpec> extraVideos,
            List<QuestionSpec> quiz) {

        public List<String> prereqs() {
            return prereqs == null ? List.of() : prereqs;
        }

        /** Campo opcional: a esmagadora maioria dos nós não tem complementar, e isso é normal. */
        public List<ExtraVideoSpec> extraVideos() {
            return extraVideos == null ? List.of() : extraVideos;
        }

        public List<QuestionSpec> quiz() {
            return quiz == null ? List.of() : quiz;
        }

        public String unlockRule() {
            return unlockRule == null || unlockRule.isBlank() ? "ALL" : unlockRule;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoSpec(String youtubeId, String title, String channel, Integer startSeconds) {}

    /**
     * Vídeo complementar. Mesmos campos do canônico mais os dois que só fazem sentido aqui: {@code
     * orientation}, preenchida pelo script a partir do formato real, e {@code note}, a anotação de
     * quem catalogou.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtraVideoSpec(
            String youtubeId,
            String title,
            String channel,
            Integer startSeconds,
            String orientation,
            String note) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuestionSpec(String prompt, String explanation, List<OptionSpec> options) {

        public List<OptionSpec> options() {
            return options == null ? List.of() : options;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptionSpec(String text, boolean correct) {}
}
