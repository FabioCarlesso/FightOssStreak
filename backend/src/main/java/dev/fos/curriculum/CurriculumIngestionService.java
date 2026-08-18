package dev.fos.curriculum;

import dev.fos.config.FosProperties;
import dev.fos.model.Belt;
import dev.fos.model.CurriculumModule;
import dev.fos.model.ExtraVideoRef;
import dev.fos.model.Node;
import dev.fos.model.QuizOption;
import dev.fos.model.QuizQuestion;
import dev.fos.model.UnlockRule;
import dev.fos.model.VideoOrientation;
import dev.fos.model.VideoRef;
import dev.fos.repo.ModuleRepository;
import dev.fos.repo.NodeRepository;
import dev.fos.repo.QuizQuestionRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingere o currículo versionado no banco.
 *
 * <p>O JSON é a fonte da verdade (D11) e o banco é projeção dele: por isso a ingestão é idempotente
 * e reexecutada na subida. Progresso, drills e SRS ficam intocados — eles referenciam nós por id, e
 * ids de nós existentes são preservados entre execuções.
 */
@Service
public class CurriculumIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CurriculumIngestionService.class);

    private final CurriculumLoader loader;
    private final CurriculumValidator validator;
    private final ModuleRepository moduleRepository;
    private final NodeRepository nodeRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final EntityManager entityManager;
    private final boolean syncOnStartup;

    public CurriculumIngestionService(
            CurriculumLoader loader,
            CurriculumValidator validator,
            ModuleRepository moduleRepository,
            NodeRepository nodeRepository,
            QuizQuestionRepository quizQuestionRepository,
            EntityManager entityManager,
            FosProperties properties) {
        this.loader = loader;
        this.validator = validator;
        this.moduleRepository = moduleRepository;
        this.nodeRepository = nodeRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.entityManager = entityManager;
        this.syncOnStartup = properties.curriculum().syncOnStartup();
    }

    /** Se a sincronização automática na subida está ligada. */
    public boolean isSyncOnStartupEnabled() {
        return syncOnStartup;
    }

    /**
     * Valida e grava o currículo. Falha alto: subir com currículo quebrado seria pior do que não
     * subir.
     */
    @Transactional
    public void sync() {
        List<CurriculumSource.Module> modules = loader.load();
        validator.validate(modules);

        Map<String, Node> nodesByCode = new HashMap<>();
        int moduleOrder = 0;

        for (CurriculumSource.Module source : modules) {
            CurriculumModule module = upsertModule(source, moduleOrder++);
            for (CurriculumSource.NodeSpec spec : source.nodes()) {
                nodesByCode.put(spec.code(), upsertNode(module, spec));
            }
        }

        // Arestas só depois de todos os nós existirem: pré-requisitos cruzam módulos.
        entityManager.flush();
        linkPrereqs(modules, nodesByCode);
        replaceQuizzes(modules, nodesByCode);

        log.info("Currículo sincronizado: {} módulos, {} nós", modules.size(), nodesByCode.size());
    }

    private CurriculumModule upsertModule(CurriculumSource.Module source, int orderIndex) {
        return moduleRepository
                .findByCode(source.code())
                .map(
                        existing -> {
                            existing.update(source.title(), source.summary(), orderIndex);
                            return moduleRepository.save(existing);
                        })
                .orElseGet(
                        () ->
                                moduleRepository.save(
                                        new CurriculumModule(
                                                source.code(),
                                                source.title(),
                                                source.summary(),
                                                orderIndex)));
    }

    private Node upsertNode(CurriculumModule module, CurriculumSource.NodeSpec spec) {
        VideoRef video = toVideoRef(spec.video());
        List<ExtraVideoRef> extras = toExtraVideoRefs(spec);
        Belt belt = Belt.valueOf(spec.belt());
        UnlockRule rule = UnlockRule.valueOf(spec.unlockRule());

        return nodeRepository
                .findByCode(spec.code())
                .map(
                        existing -> {
                            existing.update(
                                    module,
                                    spec.title(),
                                    belt,
                                    spec.concept(),
                                    spec.order(),
                                    rule,
                                    video,
                                    extras);
                            return nodeRepository.save(existing);
                        })
                .orElseGet(
                        () ->
                                nodeRepository.save(
                                        new Node(
                                                module,
                                                spec.code(),
                                                spec.title(),
                                                belt,
                                                spec.concept(),
                                                spec.order(),
                                                rule,
                                                video,
                                                extras)));
    }

    private void linkPrereqs(List<CurriculumSource.Module> modules, Map<String, Node> nodesByCode) {
        for (CurriculumSource.Module source : modules) {
            for (CurriculumSource.NodeSpec spec : source.nodes()) {
                Node node = nodesByCode.get(spec.code());
                Set<Long> prereqIds = new LinkedHashSet<>();
                for (String prereqCode : spec.prereqs()) {
                    prereqIds.add(nodesByCode.get(prereqCode).getId());
                }
                node.setPrereqNodeIds(prereqIds);
                nodeRepository.save(node);
            }
        }
    }

    /**
     * Quizzes são substituídos por completo a cada sync. É a opção honesta: reconciliar pergunta a
     * pergunta exigiria ids estáveis no JSON, e o histórico que importa (score do nó) vive em
     * user_progress, não nas perguntas.
     */
    private void replaceQuizzes(
            List<CurriculumSource.Module> modules, Map<String, Node> nodesByCode) {
        entityManager.flush();
        entityManager.createQuery("delete from QuizOption").executeUpdate();
        entityManager.createQuery("delete from QuizQuestion").executeUpdate();

        for (CurriculumSource.Module source : modules) {
            for (CurriculumSource.NodeSpec spec : source.nodes()) {
                Node node = nodesByCode.get(spec.code());
                int questionOrder = 0;
                for (CurriculumSource.QuestionSpec questionSpec : spec.quiz()) {
                    QuizQuestion question =
                            new QuizQuestion(
                                    node,
                                    questionSpec.prompt(),
                                    questionSpec.explanation(),
                                    questionOrder++);
                    int optionOrder = 0;
                    for (CurriculumSource.OptionSpec optionSpec : questionSpec.options()) {
                        question.addOption(
                                new QuizOption(
                                        question,
                                        optionSpec.text(),
                                        optionSpec.correct(),
                                        optionOrder++));
                    }
                    quizQuestionRepository.save(question);
                }
            }
        }
    }

    private VideoRef toVideoRef(CurriculumSource.VideoSpec spec) {
        if (spec == null || spec.youtubeId() == null || spec.youtubeId().isBlank()) {
            return new VideoRef(null, null, null, null);
        }
        return new VideoRef(spec.youtubeId(), spec.title(), spec.channel(), spec.startSeconds());
    }

    private List<ExtraVideoRef> toExtraVideoRefs(CurriculumSource.NodeSpec spec) {
        List<ExtraVideoRef> refs = new ArrayList<>();
        for (CurriculumSource.ExtraVideoSpec extra : spec.extraVideos()) {
            refs.add(
                    new ExtraVideoRef(
                            extra.youtubeId(),
                            extra.title(),
                            extra.channel(),
                            extra.startSeconds(),
                            extra.orientation() == null
                                    ? VideoOrientation.HORIZONTAL
                                    : VideoOrientation.valueOf(extra.orientation()),
                            extra.note()));
        }
        return refs;
    }
}
