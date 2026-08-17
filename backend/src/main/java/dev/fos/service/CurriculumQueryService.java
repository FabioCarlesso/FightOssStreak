package dev.fos.service;

import dev.fos.model.Node;
import dev.fos.model.ProgressStatus;
import dev.fos.model.QuizOption;
import dev.fos.model.QuizQuestion;
import dev.fos.model.SrsReview;
import dev.fos.model.UserProgress;
import dev.fos.model.VideoRef;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.NodeRepository;
import dev.fos.repo.QuizQuestionRepository;
import dev.fos.repo.SrsReviewRepository;
import dev.fos.repo.UserProgressRepository;
import dev.fos.web.dto.CurriculumDtos;
import dev.fos.web.dto.QuizDtos;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leitura da árvore e do detalhe de nó, já resolvidos contra o progresso do usuário. */
@Service
public class CurriculumQueryService {

    /** Aviso curto exibido no rodapé de todo nó (docs/06-disclaimer-responsabilidade.md). */
    static final String SHORT_SAFETY_NOTICE =
            "⚠️ Conteúdo de apoio ao estudo. Não substitui a instrução do seu professor. "
                    + "Pratique somente em academia, com supervisão e parceiro consciente. "
                    + "Respeite o tap sempre.";

    private final NodeRepository nodeRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final UserProgressRepository progressRepository;
    private final SrsReviewRepository srsRepository;
    private final DrillLogRepository drillLogRepository;
    private final UnlockService unlockService;

    public CurriculumQueryService(
            NodeRepository nodeRepository,
            QuizQuestionRepository quizQuestionRepository,
            UserProgressRepository progressRepository,
            SrsReviewRepository srsRepository,
            DrillLogRepository drillLogRepository,
            UnlockService unlockService) {
        this.nodeRepository = nodeRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.progressRepository = progressRepository;
        this.srsRepository = srsRepository;
        this.drillLogRepository = drillLogRepository;
        this.unlockService = unlockService;
    }

    @Transactional(readOnly = true)
    public CurriculumDtos.TreeView tree(Long userId) {
        List<Node> nodes = nodeRepository.findAllOrdered();
        Map<Long, ProgressStatus> statuses =
                unlockService.resolveStatuses(nodes, persistedStatuses(userId));
        Map<Long, UserProgress> progress = progressByNodeId(userId);
        Map<Long, SrsReview> srs = srsByNodeId(userId);
        Map<Long, String> codeById =
                nodes.stream().collect(Collectors.toMap(Node::getId, Node::getCode));
        Map<Long, Integer> quizCounts = quizCountsByNodeId();

        Map<String, List<CurriculumDtos.NodeSummaryView>> byModule = new LinkedHashMap<>();
        Map<String, Node> moduleAnchor = new LinkedHashMap<>();

        for (Node node : nodes) {
            String moduleCode = node.getModule().getCode();
            moduleAnchor.putIfAbsent(moduleCode, node);
            byModule.computeIfAbsent(moduleCode, key -> new ArrayList<>())
                    .add(
                            new CurriculumDtos.NodeSummaryView(
                                    node.getCode(),
                                    node.getTitle(),
                                    node.getBelt(),
                                    statuses.get(node.getId()),
                                    node.getUnlockRule(),
                                    node.getPrereqNodeIds().stream().map(codeById::get).toList(),
                                    node.getVideo() != null && node.getVideo().isCatalogued(),
                                    quizCounts.getOrDefault(node.getId(), 0),
                                    progress.containsKey(node.getId())
                                            ? progress.get(node.getId()).getLastQuizScore()
                                            : null,
                                    srs.containsKey(node.getId())
                                            ? srs.get(node.getId()).getNextReviewOn()
                                            : null));
        }

        List<CurriculumDtos.ModuleView> modules =
                byModule.entrySet().stream()
                        .map(
                                entry -> {
                                    var module = moduleAnchor.get(entry.getKey()).getModule();
                                    return new CurriculumDtos.ModuleView(
                                            module.getCode(),
                                            module.getTitle(),
                                            module.getSummary(),
                                            entry.getValue());
                                })
                        .toList();

        return new CurriculumDtos.TreeView(modules, summarize(statuses));
    }

    @Transactional(readOnly = true)
    public CurriculumDtos.NodeDetailView nodeDetail(Long userId, String code, LocalDate today) {
        Node node = requireNode(code);
        List<Node> allNodes = nodeRepository.findAllOrdered();
        Map<Long, ProgressStatus> statuses =
                unlockService.resolveStatuses(allNodes, persistedStatuses(userId));
        Map<Long, Node> byId =
                allNodes.stream().collect(Collectors.toMap(Node::getId, Function.identity()));

        List<CurriculumDtos.PrereqView> prereqs =
                node.getPrereqNodeIds().stream()
                        .map(byId::get)
                        .filter(java.util.Objects::nonNull)
                        .map(
                                prereq ->
                                        new CurriculumDtos.PrereqView(
                                                prereq.getCode(),
                                                prereq.getTitle(),
                                                statuses.get(prereq.getId())
                                                        == ProgressStatus.COMPLETED))
                        .toList();

        List<QuizDtos.QuestionView> quiz =
                quizQuestionRepository.findByNodeIdWithOptions(node.getId()).stream()
                        .map(
                                question ->
                                        new QuizDtos.QuestionView(
                                                question.getId(),
                                                question.getPrompt(),
                                                shuffleOptions(question)))
                        .toList();

        List<CurriculumDtos.DrillEntryView> drills =
                drillLogRepository
                        .findByUserIdAndNodeIdOrderByDrilledOnDesc(userId, node.getId())
                        .stream()
                        .limit(10)
                        .map(
                                entry ->
                                        new CurriculumDtos.DrillEntryView(
                                                entry.getDrilledOn(),
                                                entry.getRecall().name(),
                                                entry.getNote()))
                        .toList();

        return new CurriculumDtos.NodeDetailView(
                node.getCode(),
                node.getTitle(),
                node.getBelt(),
                node.getConcept(),
                node.getModule().getCode(),
                node.getModule().getTitle(),
                statuses.get(node.getId()),
                node.getUnlockRule(),
                prereqs,
                toVideoView(node.getVideo()),
                quiz,
                toSrsView(srsByNodeId(userId).get(node.getId()), today),
                drills,
                SHORT_SAFETY_NOTICE);
    }

    @Transactional(readOnly = true)
    public Node requireNode(String code) {
        return nodeRepository.findByCode(code).orElseThrow(() -> new NodeNotFoundException(code));
    }

    Map<Long, ProgressStatus> persistedStatuses(Long userId) {
        return progressRepository.findByIdUserId(userId).stream()
                .collect(Collectors.toMap(p -> p.getId().getNodeId(), UserProgress::getStatus));
    }

    Map<Long, UserProgress> progressByNodeId(Long userId) {
        return progressRepository.findByIdUserId(userId).stream()
                .collect(Collectors.toMap(p -> p.getId().getNodeId(), Function.identity()));
    }

    Map<Long, SrsReview> srsByNodeId(Long userId) {
        return srsRepository.findByIdUserId(userId).stream()
                .collect(Collectors.toMap(s -> s.getId().getNodeId(), Function.identity()));
    }

    /**
     * Ordena as alternativas de forma determinística, mas descorrelacionada da ordem de autoria.
     *
     * <p>No JSON versionado a alternativa correta é escrita em primeiro lugar — é o que torna o
     * currículo legível em um PR. Servi-las nessa ordem entregaria o gabarito de graça. A ordem é
     * embaralhada por um hash de (pergunta, alternativa): estável entre requisições, para o usuário
     * não ver as opções pulando de lugar ao recarregar, e sem relação com a posição original.
     */
    private static List<QuizDtos.OptionView> shuffleOptions(QuizQuestion question) {
        Comparator<QuizOption> deterministicShuffle =
                Comparator.comparingLong(
                                (QuizOption option) ->
                                        mix(question.getId() * 1_000_003L + option.getId()))
                        .thenComparing(QuizOption::getId);

        return question.getOptions().stream()
                .sorted(deterministicShuffle)
                .map(option -> new QuizDtos.OptionView(option.getId(), option.getLabel()))
                .toList();
    }

    /**
     * Espalha os bits de uma chave pequena e sequencial (finalizador do splitmix64).
     *
     * <p>Aqui estava o bug que fazia o embaralhamento não embaralhar: a versão anterior usava
     * {@code Long.hashCode}, que para qualquer valor abaixo de 2³¹ devolve o próprio valor — e as
     * chaves reais não passam de ~91 milhões. A ordenação virava id crescente, ou seja, a ordem do
     * JSON, com a correta em primeiro lugar nas 91 perguntas. Um multiplicador só não resolveria:
     * multiplicação preserva a ordem dos bits altos, e é justamente deles que a comparação depende.
     */
    private static long mix(long key) {
        long z = key;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private Map<Long, Integer> quizCountsByNodeId() {
        return quizQuestionRepository.findAll().stream()
                .collect(
                        Collectors.groupingBy(
                                question -> question.getNode().getId(),
                                Collectors.reducing(0, question -> 1, Integer::sum)));
    }

    private CurriculumDtos.ProgressSummary summarize(Map<Long, ProgressStatus> statuses) {
        int completed = 0;
        int available = 0;
        int locked = 0;
        for (ProgressStatus status : statuses.values()) {
            switch (status) {
                case COMPLETED -> completed++;
                case LOCKED -> locked++;
                default -> available++;
            }
        }
        return new CurriculumDtos.ProgressSummary(statuses.size(), completed, available, locked);
    }

    static CurriculumDtos.VideoView toVideoView(VideoRef video) {
        if (video == null || !video.isCatalogued()) {
            return CurriculumDtos.VideoView.notCatalogued();
        }
        String start =
                video.getStartSeconds() != null && video.getStartSeconds() > 0
                        ? "?start=" + video.getStartSeconds()
                        : "";
        return new CurriculumDtos.VideoView(
                true,
                video.getYoutubeId(),
                video.getTitle(),
                video.getChannel(),
                video.getStartSeconds(),
                "https://www.youtube-nocookie.com/embed/" + video.getYoutubeId() + start,
                "https://www.youtube.com/watch?v=" + video.getYoutubeId());
    }

    static CurriculumDtos.SrsView toSrsView(SrsReview review, LocalDate today) {
        if (review == null) {
            return CurriculumDtos.SrsView.notScheduled();
        }
        return new CurriculumDtos.SrsView(
                true,
                review.getNextReviewOn(),
                review.getIntervalDays(),
                review.getRepetitions(),
                !review.getNextReviewOn().isAfter(today));
    }

    /** Ordena nós por módulo e posição — reutilizado pela agenda de revisão. */
    static final Comparator<Node> DISPLAY_ORDER =
            Comparator.comparingInt((Node n) -> n.getModule().getOrderIndex())
                    .thenComparingInt(Node::getOrderIndex);
}
