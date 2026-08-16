package dev.fos.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
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
        FosProperties properties = new FosProperties(
                "test", new FosProperties.Curriculum("classpath:curriculum/modules.json", true));
        CurriculumLoader loader =
                new CurriculumLoader(new ObjectMapper(), new DefaultResourceLoader(), properties);
        modules = loader.load();
        validator = new CurriculumValidator();
    }

    @Test
    @DisplayName("o currículo versionado é íntegro: sem ciclos, referências quebradas ou quiz inválido")
    void curriculumIsValid() {
        validator.validate(modules);
    }

    @Test
    @DisplayName("todos os 9 módulos de docs/04 estão presentes e na ordem")
    void hasAllModules() {
        assertThat(modules).extracting(CurriculumSource.Module::code)
                .containsExactly("M0", "M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8");
    }

    @Test
    @DisplayName("a contagem por módulo bate com a tabela de docs/04")
    void nodeCountsMatchPlanningDoc() {
        Map<String, Integer> countByModule = modules.stream()
                .collect(Collectors.toMap(
                        CurriculumSource.Module::code, module -> module.nodes().size()));

        assertThat(countByModule).containsExactlyInAnyOrderEntriesOf(Map.of(
                "M0", 5, "M1", 6, "M2", 7, "M3", 7, "M4", 6,
                "M5", 4, "M6", 4, "M7", 4, "M8", 3));

        // docs/04 afirma "Total: 43 nós", mas a soma das tabelas do próprio documento dá 46.
        // O código segue as tabelas — elas são o conteúdo real. Divergência anotada em docs/07 (D14).
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
    @DisplayName("sobrevivência antes de ataque: nenhuma finalização é alcançável sem passar por M1")
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
    @DisplayName("M0 e M1 têm quiz escrito; os demais módulos ainda não")
    void quizCoverageMatchesCuratedScope() {
        Map<String, CurriculumSource.NodeSpec> byCode = byCode();

        for (CurriculumSource.NodeSpec node : byCode.values()) {
            if (isCurated(node)) {
                assertThat(node.quiz())
                        .as("quiz de %s", node.code())
                        .hasSizeBetween(3, 5);
            }
        }
    }

    @Test
    @DisplayName("M0 e M1 têm vídeo catalogado; os demais módulos seguem pendentes de curadoria")
    void videoCoverageMatchesCuratedScope() {
        for (CurriculumSource.NodeSpec node : byCode().values()) {
            if (isCurated(node)) {
                assertThat(node.video())
                        .as("vídeo de %s", node.code())
                        .isNotNull();
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
                .allSatisfy(node -> {
                    CurriculumSource.VideoSpec video = node.video();
                    assertThat(video.youtubeId())
                            .as("id de vídeo de %s", node.code())
                            .matches(CurriculumValidator.YOUTUBE_ID_REGEX);
                    assertThat(video.title()).as("título do vídeo de %s", node.code()).isNotBlank();
                    assertThat(video.channel()).as("canal do vídeo de %s", node.code()).isNotBlank();
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
        CurriculumSource.Module cyclic = new CurriculumSource.Module(
                "MX", "Ciclo", "resumo",
                List.of(
                        node("MX.1", List.of("MX.2")),
                        node("MX.2", List.of("MX.1"))));

        assertThatThrownBy(() -> validator.validate(List.of(cyclic)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("Ciclo de pré-requisitos");
    }

    @Test
    @DisplayName("o validador rejeita pré-requisito inexistente")
    void rejectsDanglingPrereq() {
        CurriculumSource.Module broken = new CurriculumSource.Module(
                "MX", "Quebrado", "resumo", List.of(node("MX.1", List.of("MZ.9"))));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("pré-requisito inexistente");
    }

    @Test
    @DisplayName("o validador rejeita quiz sem exatamente uma alternativa correta")
    void rejectsQuizWithoutSingleCorrectOption() {
        CurriculumSource.QuestionSpec twoCorrect = new CurriculumSource.QuestionSpec(
                "pergunta",
                "explicação",
                List.of(
                        new CurriculumSource.OptionSpec("a", true),
                        new CurriculumSource.OptionSpec("b", true)));

        CurriculumSource.Module broken = new CurriculumSource.Module(
                "MX", "Quebrado", "resumo",
                List.of(new CurriculumSource.NodeSpec(
                        "MX.1", "t", "BRANCA", 1, "ALL", List.of(), "conceito", null,
                        List.of(twoCorrect))));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("exatamente uma alternativa correta");
    }

    @Test
    @DisplayName("o validador exige crédito ao canal em vídeo catalogado (política D7)")
    void rejectsVideoWithoutChannelCredit() {
        CurriculumSource.Module broken =
                moduleWithVideo(new CurriculumSource.VideoSpec("REFdmhRCsSQ", "titulo", null, null));

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
        CurriculumSource.Module broken = moduleWithVideo(
                new CurriculumSource.VideoSpec("abc123", "titulo", "canal", null));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("fora do formato do YouTube");
    }

    @Test
    @DisplayName("o validador rejeita startSeconds negativo")
    void rejectsNegativeStartSeconds() {
        CurriculumSource.Module broken = moduleWithVideo(
                new CurriculumSource.VideoSpec("REFdmhRCsSQ", "titulo", "canal", -1));

        assertThatThrownBy(() -> validator.validate(List.of(broken)))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("startSeconds negativo");
    }

    private CurriculumSource.Module moduleWithVideo(CurriculumSource.VideoSpec video) {
        return new CurriculumSource.Module(
                "MX", "Quebrado", "resumo",
                List.of(new CurriculumSource.NodeSpec(
                        "MX.1", "t", "BRANCA", 1, "ALL", List.of(), "conceito", video, List.of())));
    }

    /** M0 e M1 são o escopo curado do MVP: quiz escrito e vídeo catalogado. */
    private boolean isCurated(CurriculumSource.NodeSpec node) {
        return node.code().startsWith("M0.") || node.code().startsWith("M1.");
    }

    private CurriculumSource.NodeSpec node(String code, List<String> prereqs) {
        return new CurriculumSource.NodeSpec(
                code, "titulo", "BRANCA", 1, "ALL", prereqs, "conceito", null, List.of());
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
