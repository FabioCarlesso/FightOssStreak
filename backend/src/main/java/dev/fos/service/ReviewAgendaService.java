package dev.fos.service;

import dev.fos.model.Node;
import dev.fos.model.SrsReview;
import dev.fos.model.UserProgress;
import dev.fos.repo.NodeRepository;
import dev.fos.repo.SrsReviewRepository;
import dev.fos.web.dto.ActivityDtos;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A agenda de "o que drillar hoje".
 *
 * <p>É o diferencial declarado do produto: o app abre e diz o que revisar, em vez de só exibir um
 * streak. A ordenação é intencional — mais atrasado primeiro e, em empate, o nó cujo quiz saiu pior
 * (docs/04-arvore-curriculo-bjj.md, "notas de design").
 */
@Service
public class ReviewAgendaService {

    private final SrsReviewRepository srsRepository;
    private final NodeRepository nodeRepository;
    private final CurriculumQueryService curriculumQueryService;

    public ReviewAgendaService(
            SrsReviewRepository srsRepository,
            NodeRepository nodeRepository,
            CurriculumQueryService curriculumQueryService) {
        this.srsRepository = srsRepository;
        this.nodeRepository = nodeRepository;
        this.curriculumQueryService = curriculumQueryService;
    }

    @Transactional(readOnly = true)
    public ActivityDtos.ReviewAgenda dueToday(Long userId, LocalDate today) {
        List<SrsReview> due =
                srsRepository.findByIdUserIdAndNextReviewOnLessThanEqual(userId, today);
        Map<Long, Node> nodesById =
                nodeRepository.findAllOrdered().stream()
                        .collect(Collectors.toMap(Node::getId, Function.identity()));
        Map<Long, UserProgress> progress = curriculumQueryService.progressByNodeId(userId);

        List<ActivityDtos.DueItemView> items =
                due.stream()
                        .map(
                                review -> {
                                    Node node = nodesById.get(review.getId().getNodeId());
                                    if (node == null) {
                                        return null;
                                    }
                                    UserProgress nodeProgress = progress.get(node.getId());
                                    return new ActivityDtos.DueItemView(
                                            node.getCode(),
                                            node.getTitle(),
                                            node.getBelt(),
                                            node.getModule().getCode(),
                                            review.getNextReviewOn(),
                                            ChronoUnit.DAYS.between(
                                                    review.getNextReviewOn(), today),
                                            nodeProgress == null
                                                    ? null
                                                    : nodeProgress.getLastQuizScore());
                                })
                        .filter(java.util.Objects::nonNull)
                        .sorted(
                                Comparator.comparingLong(ActivityDtos.DueItemView::daysOverdue)
                                        .reversed()
                                        .thenComparing(
                                                ActivityDtos.DueItemView::lastQuizScore,
                                                Comparator.nullsFirst(Comparator.naturalOrder()))
                                        .thenComparing(ActivityDtos.DueItemView::nodeCode))
                        .toList();

        return new ActivityDtos.ReviewAgenda(today, items.size(), items);
    }
}
