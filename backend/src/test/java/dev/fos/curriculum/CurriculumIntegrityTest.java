package dev.fos.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fos.config.FosProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Guarda-corpo do currículo versionado.
 *
 * <p>Este teste é o que torna seguro editar a árvore em um PR: qualquer referência quebrada, ciclo
 * ou quiz malformado quebra o build antes de virar um nó impossível de desbloquear.
 */
class CurriculumIntegrityTest {

    private List<CurriculumSource.Module> modules;
    private CurriculumValidator validator;

    @BeforeEach
    void setUp() {
        FosProperties properties =
                new FosProperties(
                        "test",
                        new FosProperties.Curriculum("classpath:curriculum/modules.json", true),
                        null,
                        null,
                        null,
                        null);
        CurriculumLoader loader =
                new CurriculumLoader(new ObjectMapper(), new DefaultResourceLoader(), properties);
        modules = loader.load();
        validator = new CurriculumValidator();
    }

    @Test
    @DisplayName(
            "o currículo versionado é íntegro: sem ciclos, referências quebradas ou quiz inválido")
    void curriculumIsValid() {
        validator.validate(modules);
    }

    @Test
    @DisplayName("todos os 9 módulos de docs/04 estão presentes e na ordem")
    void hasAllModules() {
        assertThat(modules)
                .extracting(CurriculumSource.Module::code)
                .containsExactly("M0", "M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8");
    }

    @Test
    @DisplayName("a contagem por módulo bate com a tabela de docs/04")
    void nodeCountsMatchPlanningDoc() {
        Map<String, Integer> countByModule =
                modules.stream()
                        .collect(
                                Collectors.toMap(
                                        CurriculumSource.Module::code,
                                        module -> module.nodes().size()));

        assertThat(countByModule)
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "M0", 5, "M1", 6, "M2", 7, "M3", 7, "M4", 6, "M5", 4, "M6", 4, "M7",
                                4, "M8", 3));

        // docs/04 afirma "Total: 43 nós", mas a soma das tabelas do próprio documento dá 46.
        // O código segue as tabelas — elas são o conteúdo real. Divergência anotada em docs/07
        // (D14).
        int total = countByModule.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(46);
    }

    @Test
    @DisplayName("M0 é porta de entrada: nenhum nó do módulo tem pré-requisito")
    void moduleZeroIsEntryPoint() {
        List<CurriculumSource.NodeSpec> m0 = modules.getFirst().nodes();
        assertThat(m0).allSatisfy(node -> assertThat(node.prereqs()).isEmpty());
    }

    @Test
    @DisplayName(
            "sobrevivência antes de ataque: nenhuma finalização é alcançável sem passar por M1")
    void survivalPrecedesAttack() {
        Map<String, CurriculumSource.NodeSpec> byCode = byCode();

        // Toda finalização da guarda fechada depende de M2.1, que depende da recuperação
        // de guarda (M1.5) — a inversão pedagógica corrigida em D10.
        for (String submission : List.of("M2.4", "M2.5", "M2.6")) {
            assertThat(ancestorsOf(submission, byCode))
                    .as("pré-requisitos transitivos de %s", submission)
                    .contains("M1.5", "M1.1");
        }
    }

    @Test
    @DisplayName("todos os 46 nós têm banco de quiz com pelo menos 8 perguntas (issue #59, D44)")
    void everyNodeHasAQuizBankOfAtLeastEight() {
        for (CurriculumSource.NodeSpec node : byCode().values()) {
            assertThat(node.quiz())
                    .as("banco de quiz de %s", node.code())
                    .hasSizeGreaterThanOrEqualTo(CurriculumValidator.MIN_QUIZ_QUESTIONS);
        }
    }

    @Test
    @DisplayName("a alternativa correta é sempre a primeira do array (convenção de D16)")
    void correctOptionIsAlwaysWrittenFirst() {
        for (CurriculumSource.NodeSpec node : byCode().values()) {
            for (int i = 0; i < node.quiz().size(); i++) {
                CurriculumSource.QuestionSpec question = node.quiz().get(i);
                assertThat(question.options().getFirst().correct())
                        .as(
                                "nó %s, pergunta %d: gabarito fora da primeira posição",
                                node.code(), i + 1)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("M0 e M1 têm vídeo catalogado; os demais módulos seguem pendentes de curadoria")
    void videoCoverageMatchesCuratedScope() {
        for (CurriculumSource.NodeSpec node : byCode().values()) {
            if (hasCuratedVideo(node)) {
                assertThat(node.video()).as("vídeo de %s", node.code()).isNotNull();
            } else {
                assertThat(node.video())
                        .as("vídeo de %s — M2–M8 entram em issues próprias", node.code())
                        .isNull();
            }
        }
    }

    @Test
    @DisplayName("todo vídeo catalogado tem id utilizável, título e crédito de canal (D7)")
    void cataloguedVideosAreUsableAndCredited() {
        assertThat(byCode().values())
                .filteredOn(node -> node.video() != null)
                .isNotEmpty()
                .allSatisfy(
                        node -> {
                            CurriculumSource.VideoSpec video = node.video();
                            assertThat(video.youtubeId())
                                    .as("id de vídeo de %s", node.code())
                                    .matches(CurriculumValidator.YOUTUBE_ID_REGEX);
                            assertThat(video.title())
                                    .as("título do vídeo de %s", node.code())
                                    .isNotBlank();
                            assertThat(video.channel())
                                    .as("canal do vídeo de %s", node.code())
                                    .isNotBlank();
                            if (video.startSeconds() != null) {
                                assertThat(video.startSeconds())
                                        .as("startSeconds de %s", node.code())
                                        .isNotNegative();
                            }
                        });
    }

    @Test
    @DisplayName("M4.1 usa unlockRule ANY: qualquer passagem serve (D13)")
    void passingGuardUsesAnyRule() {
        CurriculumSource.NodeSpec m41 = byCode().get("M4.1");
        assertThat(m41.unlockRule()).isEqualTo("ANY");
        assertThat(m41.prereqs()).containsExactlyInAnyOrder("M3.2", "M3.3");
    }

    @Test
    @DisplayName("guilhotina depende do sprawl, não de guarda aberta")
    void guillotineDependsOnSprawl() {
        assertThat(byCode().get("M7.2").prereqs()).containsExactly("M6.4");
    }

    @Test
    @DisplayName("o validador rejeita ciclo de pré-requisitos")
    void rejectsCycle() {
        CurriculumSource.Module cyclic =
                new CurriculumSource.Module(
                        "MX",
                        "Ciclo",
                        "resumo",
                        List.of(node("MX.1", List.of("MX.2")), node("MX.2", List.of("MX.1"))));

        assertThatThrownBy(() -> validator.validate(List.of(cyclic)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("Ciclo de pré-requisitos");
    }

    @Test
    @DisplayName("o validador rejeita pré-requisito inexistente")
    void rejectsDanglingPrereq() {
        CurriculumSource.Module broken =
                new CurriculumSource.Module(
                        "MX", "Quebrado", "resumo", List.of(node("MX.1", List.of("MZ.9"))));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("pré-requisito inexistente");
    }

    @Test
    @DisplayName("o validador rejeita conceito com mais de 3 parágrafos")
    void rejectsConceptWithTooManyParagraphs() {
        CurriculumSource.Module broken =
                moduleWithConcept("primeiro\n\nsegundo\n\nterceiro\n\nquarto");

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("4 parágrafos")
                .hasMessageContaining("o máximo é 3");
    }

    @Test
    @DisplayName("o validador aceita conceito com até 3 parágrafos, e conceito de bloco único")
    void acceptsConceptWithUpToThreeParagraphs() {
        assertThatCode(
                        () ->
                                validator.validate(
                                        List.of(
                                                moduleWithConcept(
                                                        "primeiro\n\nsegundo\n\nterceiro"))))
                .doesNotThrowAnyException();

        assertThatCode(
                        () ->
                                validator.validate(
                                        List.of(moduleWithConcept("bloco único, sem quebra"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
            "a faixa de tamanho do conceito (450-900) só vale para módulos já reescritos "
                    + "(D42): módulo fora de CONCEPT_LENGTH_CURATED_MODULES continua livre")
    void conceptLengthGuardOnlyAppliesToCuratedModules() {
        // "MX" não está em CONCEPT_LENGTH_CURATED_MODULES (hoje M0-M8, o currículo inteiro), então
        // um conceito curto demais nesse módulo sintético não quebra validate() — é o
        // comportamento esperado para um módulo futuro ainda não escrito no padrão.
        assertThatCode(
                        () ->
                                validator.validate(
                                        List.of(moduleWithConcept("curto demais, de propósito"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a faixa de tamanho do conceito é aplicada de verdade para M0 e M1 (D42)")
    void conceptLengthGuardAppliesToCuratedModules() {
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        List.of(
                                                moduleWithConceptAndCode(
                                                        "M0",
                                                        "M0.1",
                                                        "curto demais, de propósito"))))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("fora da faixa de 450 a 900");

        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        List.of(
                                                moduleWithConceptAndCode(
                                                        "M1",
                                                        "M1.1",
                                                        "curto demais, de propósito"))))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("fora da faixa de 450 a 900");

        assertThatCode(
                        () ->
                                validator.validate(
                                        List.of(
                                                moduleWithConceptAndCode(
                                                        "M0", "M0.1", "a".repeat(450)))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("o método de faixa de tamanho rejeita conceito curto demais")
    void validateConceptLengthRejectsTooShort() {
        assertThatThrownBy(() -> validator.validateConceptLength("curto", "nó MX.1"))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("fora da faixa de 450 a 900");
    }

    @Test
    @DisplayName("o método de faixa de tamanho rejeita conceito longo demais")
    void validateConceptLengthRejectsTooLong() {
        String longo = "a".repeat(901);

        assertThatThrownBy(() -> validator.validateConceptLength(longo, "nó MX.1"))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("fora da faixa de 450 a 900");
    }

    @Test
    @DisplayName("o método de faixa de tamanho aceita conceito dentro de 450-900 caracteres")
    void validateConceptLengthAcceptsWithinRange() {
        String valido = "a".repeat(450);

        assertThatCode(() -> validator.validateConceptLength(valido, "nó MX.1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("o validador rejeita quiz sem exatamente uma alternativa correta")
    void rejectsQuizWithoutSingleCorrectOption() {
        CurriculumSource.QuestionSpec twoCorrect =
                new CurriculumSource.QuestionSpec(
                        "pergunta com duas corretas",
                        "explicação",
                        List.of(
                                new CurriculumSource.OptionSpec("a", true),
                                new CurriculumSource.OptionSpec("b", true)));

        // Sete perguntas válidas de preenchimento: o banco precisa passar do mínimo de 8 (D44)
        // para que este teste continue verificando a regra de gabarito, e não a de tamanho.
        List<CurriculumSource.QuestionSpec> quiz = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            quiz.add(question("pergunta de preenchimento " + i));
        }
        quiz.add(twoCorrect);

        CurriculumSource.Module broken = moduleWithQuiz(quiz);

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("exatamente uma alternativa correta");
    }

    @Test
    @DisplayName(
            "o validador rejeita banco de quiz com menos de 8 perguntas, mas aceita quiz vazio "
                    + "(issue #59, D44)")
    void rejectsQuizBankSmallerThanMinimum() {
        List<CurriculumSource.QuestionSpec> sevenQuestions = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            sevenQuestions.add(question("pergunta " + i));
        }
        CurriculumSource.Module broken = moduleWithQuiz(sevenQuestions);

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("banco de quiz com 7 perguntas")
                .hasMessageContaining("o mínimo é 8");

        assertThatCode(() -> validator.validate(List.of(moduleWithQuiz(List.of()))))
                .as("quiz: [] continua aceito (D15) — o mínimo só vale para quem já tem alguma")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("o validador rejeita enunciado repetido no mesmo nó")
    void rejectsDuplicatePromptInSameNode() {
        List<CurriculumSource.QuestionSpec> eightQuestions = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            eightQuestions.add(question("pergunta " + i));
        }
        eightQuestions.add(question("pergunta 0"));
        CurriculumSource.Module broken = moduleWithQuiz(eightQuestions);

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("enunciado repetido no mesmo nó");
    }

    @Test
    @DisplayName("o validador exige crédito ao canal em vídeo catalogado (política D7)")
    void rejectsVideoWithoutChannelCredit() {
        CurriculumSource.Module broken =
                moduleWithVideo(
                        new CurriculumSource.VideoSpec("REFdmhRCsSQ", "titulo", null, null));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("canal creditado");
    }

    @Test
    @DisplayName("o validador exige título em vídeo catalogado")
    void rejectsVideoWithoutTitle() {
        CurriculumSource.Module broken =
                moduleWithVideo(new CurriculumSource.VideoSpec("REFdmhRCsSQ", null, "canal", null));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("sem título");
    }

    @Test
    @DisplayName("o validador rejeita id de vídeo fora do formato do YouTube")
    void rejectsMalformedYoutubeId() {
        CurriculumSource.Module broken =
                moduleWithVideo(new CurriculumSource.VideoSpec("abc123", "titulo", "canal", null));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("fora do formato do YouTube");
    }

    @Test
    @DisplayName("o validador rejeita startSeconds negativo")
    void rejectsNegativeStartSeconds() {
        CurriculumSource.Module broken =
                moduleWithVideo(
                        new CurriculumSource.VideoSpec("REFdmhRCsSQ", "titulo", "canal", -1));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("startSeconds negativo");
    }

    @Test
    @DisplayName("complementares hoje só existem nos nós de M1 que já têm canônico (D32)")
    void extraVideosAreScopedToTheFirstBatch() {
        Map<String, List<CurriculumSource.ExtraVideoSpec>> comExtras = new java.util.TreeMap<>();
        for (CurriculumSource.NodeSpec node : byCode().values()) {
            if (!node.extraVideos().isEmpty()) {
                comExtras.put(node.code(), node.extraVideos());
            }
        }

        assertThat(comExtras.keySet()).containsExactly("M1.3", "M1.5", "M1.6");
        // O primeiro lote entrou em nó que já tem canônico de propósito: é onde a tela mostra a
        // hierarquia inteira — o vídeo que ensina em cima, os clipes que lembram embaixo.
        assertThat(comExtras.keySet())
                .allSatisfy(code -> assertThat(byCode().get(code).video()).isNotNull());
    }

    @Test
    @DisplayName("todo complementar tem id utilizável, título, canal (D7) e cabe no teto")
    void extraVideosAreUsableAndCredited() {
        for (CurriculumSource.NodeSpec node : byCode().values()) {
            List<CurriculumSource.ExtraVideoSpec> extras = node.extraVideos();
            assertThat(extras.size())
                    .as("quantidade de complementares de %s", node.code())
                    .isLessThanOrEqualTo(CurriculumValidator.MAX_EXTRA_VIDEOS);

            for (CurriculumSource.ExtraVideoSpec extra : extras) {
                assertThat(extra.youtubeId())
                        .as("id de complementar de %s", node.code())
                        .matches(CurriculumValidator.YOUTUBE_ID_REGEX);
                assertThat(extra.title())
                        .as("título de complementar de %s", node.code())
                        .isNotBlank();
                assertThat(extra.channel())
                        .as("canal de complementar de %s", node.code())
                        .isNotBlank();
            }
            assertThat(extras.stream().map(CurriculumSource.ExtraVideoSpec::youtubeId))
                    .as("complementares repetidos em %s", node.code())
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    @DisplayName("o validador exige crédito ao canal também no complementar (D7)")
    void rejectsExtraVideoWithoutChannelCredit() {
        CurriculumSource.Module broken =
                moduleWithExtras(extra("AkQxiCrsGo4", "titulo", null, null));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("complementar 1")
                .hasMessageContaining("canal creditado");
    }

    @Test
    @DisplayName("o validador rejeita complementar que repete o vídeo canônico do nó")
    void rejectsExtraVideoThatRepeatsCanonical() {
        CurriculumSource.Module broken =
                moduleWithVideoAndExtras(
                        new CurriculumSource.VideoSpec("REFdmhRCsSQ", "titulo", "canal", null),
                        List.of(extra("REFdmhRCsSQ", "titulo", "canal", null)));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("repete o vídeo canônico");
    }

    @Test
    @DisplayName("o validador rejeita o mesmo complementar duas vezes no mesmo nó")
    void rejectsDuplicateExtraVideoInSameNode() {
        CurriculumSource.Module broken =
                moduleWithExtras(
                        extra("AkQxiCrsGo4", "t", "canal", null),
                        extra("AkQxiCrsGo4", "t", "canal", null));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("complementar repetido no mesmo nó");
    }

    /**
     * O mesmo clipe servindo a dois nós é intencional, não descuido: uma reposição de guarda contra
     * joelho na barriga é pista de memória tanto do nó de recuperação quanto do de joelho na
     * barriga.
     */
    @Test
    @DisplayName("o mesmo complementar pode servir a nós diferentes")
    void acceptsSameExtraVideoAcrossDifferentNodes() {
        CurriculumSource.ExtraVideoSpec compartilhado = extra("AkQxiCrsGo4", "t", "canal", null);
        CurriculumSource.Module module =
                new CurriculumSource.Module(
                        "MX",
                        "Modulo",
                        "resumo",
                        List.of(
                                nodeWith("MX.1", null, List.of(compartilhado)),
                                nodeWith("MX.2", null, List.of(compartilhado))));

        assertThatCode(() -> validator.validate(List.of(module))).doesNotThrowAnyException();
    }

    /**
     * Exigir canônico como pré-condição travaria a catalogação de clipes atrás da curadoria inteira
     * de M2–M8, e a tela já é honesta nesse caso: o estado vazio do canônico continua aparecendo.
     */
    @Test
    @DisplayName("nó pode ter complementar sem ter vídeo canônico")
    void acceptsExtraVideosWithoutCanonical() {
        CurriculumSource.Module module =
                moduleWithExtras(extra("AkQxiCrsGo4", "titulo", "canal", null));

        assertThatCode(() -> validator.validate(List.of(module))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("o validador rejeita mais complementares que o teto (D32)")
    void rejectsTooManyExtraVideos() {
        CurriculumSource.Module broken =
                moduleWithExtras(
                        extra("AkQxiCrsGo4", "t", "canal", null),
                        extra("9wEhZ1PdoMI", "t", "canal", null),
                        extra("_sZ--Clk218", "t", "canal", null),
                        extra("7JozpLcKQvc", "t", "canal", null),
                        extra("3ZlMiI92TjU", "t", "canal", null));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("o máximo é " + CurriculumValidator.MAX_EXTRA_VIDEOS);
    }

    @Test
    @DisplayName("o validador rejeita orientação que não existe")
    void rejectsUnknownOrientation() {
        CurriculumSource.Module broken =
                moduleWithExtras(
                        new CurriculumSource.ExtraVideoSpec(
                                "AkQxiCrsGo4", "t", "canal", null, "DIAGONAL", null));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("orientação inválida");
    }

    private CurriculumSource.ExtraVideoSpec extra(
            String youtubeId, String title, String channel, Integer startSeconds) {
        return new CurriculumSource.ExtraVideoSpec(
                youtubeId, title, channel, startSeconds, null, null);
    }

    private CurriculumSource.Module moduleWithExtras(CurriculumSource.ExtraVideoSpec... extras) {
        return moduleWithVideoAndExtras(null, List.of(extras));
    }

    private CurriculumSource.Module moduleWithVideoAndExtras(
            CurriculumSource.VideoSpec video, List<CurriculumSource.ExtraVideoSpec> extras) {
        return new CurriculumSource.Module(
                "MX", "Quebrado", "resumo", List.of(nodeWith("MX.1", video, extras)));
    }

    private CurriculumSource.NodeSpec nodeWith(
            String code,
            CurriculumSource.VideoSpec video,
            List<CurriculumSource.ExtraVideoSpec> extras) {
        return new CurriculumSource.NodeSpec(
                code, "t", "BRANCA", 1, "ALL", List.of(), "conceito", video, extras, List.of());
    }

    private CurriculumSource.QuestionSpec question(String prompt) {
        return new CurriculumSource.QuestionSpec(
                prompt,
                "explicação",
                List.of(
                        new CurriculumSource.OptionSpec("certa", true),
                        new CurriculumSource.OptionSpec("errada", false)));
    }

    private CurriculumSource.Module moduleWithQuiz(List<CurriculumSource.QuestionSpec> quiz) {
        return new CurriculumSource.Module(
                "MX",
                "Quebrado",
                "resumo",
                List.of(
                        new CurriculumSource.NodeSpec(
                                "MX.1",
                                "t",
                                "BRANCA",
                                1,
                                "ALL",
                                List.of(),
                                "conceito",
                                null,
                                List.of(),
                                quiz)));
    }

    private CurriculumSource.Module moduleWithConcept(String concept) {
        return moduleWithConceptAndCode("MX", "MX.1", concept);
    }

    private CurriculumSource.Module moduleWithConceptAndCode(
            String moduleCode, String nodeCode, String concept) {
        return new CurriculumSource.Module(
                moduleCode,
                "Quebrado",
                "resumo",
                List.of(
                        new CurriculumSource.NodeSpec(
                                nodeCode, "t", "BRANCA", 1, "ALL", List.of(), concept, null,
                                List.of(), List.of())));
    }

    private CurriculumSource.Module moduleWithVideo(CurriculumSource.VideoSpec video) {
        return new CurriculumSource.Module(
                "MX",
                "Quebrado",
                "resumo",
                List.of(
                        new CurriculumSource.NodeSpec(
                                "MX.1",
                                "t",
                                "BRANCA",
                                1,
                                "ALL",
                                List.of(),
                                "conceito",
                                video,
                                List.of(),
                                List.of())));
    }

    /** Catalogar vídeo exige assistir (D21); M2–M8 entram em issues próprias. */
    private boolean hasCuratedVideo(CurriculumSource.NodeSpec node) {
        return node.code().startsWith("M0.") || node.code().startsWith("M1.");
    }

    private CurriculumSource.NodeSpec node(String code, List<String> prereqs) {
        return new CurriculumSource.NodeSpec(
                code,
                "titulo",
                "BRANCA",
                1,
                "ALL",
                prereqs,
                "conceito",
                null,
                List.of(),
                List.of());
    }

    private Map<String, CurriculumSource.NodeSpec> byCode() {
        return modules.stream()
                .flatMap(module -> module.nodes().stream())
                .collect(Collectors.toMap(CurriculumSource.NodeSpec::code, node -> node));
    }

    /** Fecho transitivo dos pré-requisitos de um nó. */
    private Set<String> ancestorsOf(String code, Map<String, CurriculumSource.NodeSpec> byCode) {
        Set<String> seen = new java.util.LinkedHashSet<>();
        List<String> queue = new ArrayList<>(byCode.get(code).prereqs());
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (seen.add(current)) {
                queue.addAll(byCode.get(current).prereqs());
            }
        }
        return seen;
    }
}
