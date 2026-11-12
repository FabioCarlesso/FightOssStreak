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
    public record Index(String version, String scope, String positions, List<ModuleRef> modules) {}

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
            List<QuestionSpec> quiz,
            List<String> positions,
            List<String> aliases) {

        /** Nó sem posição nem apelido — o formato de antes da #194, que continua válido. */
        public NodeSpec(
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
            this(
                    code,
                    title,
                    belt,
                    order,
                    unlockRule,
                    prereqs,
                    concept,
                    video,
                    extraVideos,
                    quiz,
                    null,
                    null);
        }

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

        /**
         * Posições de onde a técnica parte (#194, D76). Opcional: técnica de etiqueta ou de jogo
         * integrado não parte de posição nenhuma.
         */
        public List<String> positions() {
            return positions == null ? List.of() : positions;
        }

        /** Apelidos do nó para a busca do diário (#194, D76). Opcional. */
        public List<String> aliases() {
            return aliases == null ? List.of() : aliases;
        }
    }

    /** Conteúdo de {@code positions.json} (#194, D76). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PositionsFile(List<PositionSpec> positions) {

        public List<PositionSpec> positions() {
            return positions == null ? List.of() : positions;
        }
    }

    /**
     * Uma posição do mapa (#194, D76).
     *
     * @param family {@code GUARDA} | {@code CONTROLE_POR_CIMA} | {@code COSTAS} | {@code TRANSICAO}
     *     | {@code EM_PE}
     * @param perspective {@code BAIXO} | {@code CIMA} | {@code NEUTRA}
     * @param identify como reconhecer a posição, em uma ou duas frases — nunca como chegar nela
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PositionSpec(
            String code,
            String name,
            List<String> aliases,
            String family,
            String perspective,
            String identify) {

        public List<String> aliases() {
            return aliases == null ? List.of() : aliases;
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
