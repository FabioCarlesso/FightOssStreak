package dev.fos.service;

import dev.fos.model.AppUser;
import dev.fos.model.Feedback;
import dev.fos.model.FeedbackStatus;
import dev.fos.model.Node;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.FeedbackRepository;
import dev.fos.repo.NodeRepository;
import dev.fos.web.dto.FeedbackDtos;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feedback de usuário: bug, conteúdo errado, troca de vídeo, sugestão
 * (docs/13-feedback-usuarios.md).
 *
 * <p>Segue o mesmo desenho da fila de acesso (D36–D38): quem manda escreve, e só o dono
 * (`fos.auth.owner-emails`, via {@code OwnerOnlyInterceptor}) lê e decide.
 */
@Service
public class FeedbackService {

    private final FeedbackRepository feedbacks;
    private final NodeRepository nodes;
    private final AppUserRepository users;
    private final AccountService accounts;
    private final Clock clock;

    public FeedbackService(
            FeedbackRepository feedbacks,
            NodeRepository nodes,
            AppUserRepository users,
            AccountService accounts,
            Clock clock) {
        this.feedbacks = feedbacks;
        this.nodes = nodes;
        this.users = users;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public FeedbackDtos.FeedbackView submit(AppUser author, FeedbackDtos.FeedbackRequest request) {
        // A demo (D39) não tem identidade própria e é descartável em duas horas — reaproveita o
        // mesmo guarda que já nega poder de dono a ela em AccountService.isOwner.
        if (author.isDemo()) {
            throw new FeedbackNotAllowedException();
        }

        Long nodeId = null;
        if (request.nodeCode() != null && !request.nodeCode().isBlank()) {
            nodeId =
                    nodes.findByCode(request.nodeCode())
                            .map(Node::getId)
                            .orElseThrow(() -> new NodeNotFoundException(request.nodeCode()));
        }

        Feedback saved =
                feedbacks.save(
                        new Feedback(
                                author.getId(),
                                nodeId,
                                request.category(),
                                request.message().trim(),
                                clock.instant()));
        return toView(saved, author);
    }

    @Transactional(readOnly = true)
    public FeedbackDtos.FeedbackList queue() {
        List<Feedback> all = feedbacks.findAllByOrderByCreatedAtAsc();
        Map<Long, AppUser> authorsById =
                users
                        .findAllById(all.stream().map(Feedback::getUserId).distinct().toList())
                        .stream()
                        .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return new FeedbackDtos.FeedbackList(
                all.stream().map(f -> toView(f, authorsById.get(f.getUserId()))).toList());
    }

    @Transactional
    public FeedbackDtos.FeedbackView decide(Long id, FeedbackStatus status, Long decidedBy) {
        Feedback feedback =
                feedbacks
                        .findById(id)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Feedback inexistente: " + id));
        feedback.decide(status, decidedBy, clock.instant());
        return toView(feedback, users.findById(feedback.getUserId()).orElse(null));
    }

    private FeedbackDtos.FeedbackView toView(Feedback feedback, AppUser author) {
        String nodeCode =
                feedback.getNodeId() == null
                        ? null
                        : nodes.findById(feedback.getNodeId()).map(Node::getCode).orElse(null);
        return new FeedbackDtos.FeedbackView(
                feedback.getId(),
                feedback.getCategory(),
                nodeCode,
                feedback.getMessage(),
                feedback.getStatus(),
                feedback.getCreatedAt(),
                feedback.getDecidedAt(),
                authorLabel(author));
    }

    private String authorLabel(AppUser author) {
        if (author == null) {
            return null;
        }
        Optional<UserIdentity> identity =
                accounts.identitiesOf(author.getId()).stream().findFirst();
        return identity.map(UserIdentity::getDisplayName).orElseGet(author::getLabel);
    }
}
