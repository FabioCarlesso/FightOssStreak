package dev.fos.service;

import dev.fos.model.Node;
import dev.fos.model.ProgressStatus;
import dev.fos.model.UserNodeKey;
import dev.fos.model.UserProgress;
import dev.fos.repo.UserProgressRepository;
import dev.fos.web.dto.CurriculumDtos;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anotação fixada do usuário sobre um nó.
 *
 * <p>Serviço próprio, e não um método a mais em {@link DrillService}: anotar não é treinar. O drill
 * alimenta streak, reagenda o SRS e conclui nó sem quiz; a anotação não faz nenhuma das três, e
 * misturá-las na mesma classe convidaria a que uma dessas três acabasse acontecendo por descuido —
 * o que estragaria justamente as métricas de docs/05-mvp-web-plano.md.
 */
@Service
public class NodeNoteService {

    private final UserProgressRepository progressRepository;
    private final CurriculumQueryService curriculumQueryService;
    private final Clock clock;

    public NodeNoteService(
            UserProgressRepository progressRepository,
            CurriculumQueryService curriculumQueryService,
            Clock clock) {
        this.progressRepository = progressRepository;
        this.curriculumQueryService = curriculumQueryService;
        this.clock = clock;
    }

    /**
     * Grava (ou limpa) a anotação fixada.
     *
     * <p>Nó nunca tocado ainda não tem linha em {@code user_progress}, e anotar precisa criar uma.
     * Ela nasce com {@link ProgressStatus#AVAILABLE}, que é <em>exatamente</em> o que {@link
     * UnlockService} derivaria para esse nó se a linha não existisse: status persistido diferente
     * de LOCKED é devolvido como está, e nó com pré-requisito pendente continua sendo resolvido
     * como LOCKED antes de o valor gravado ser consultado. Ou seja, a linha nova não muda um único
     * número da árvore nem de {@code /progresso} — que é a garantia que interessa, porque anotar
     * não pode parecer ter iniciado o nó.
     *
     * <p>Gravar {@code IN_PROGRESS} aqui seria o erro fácil: é o que o drill usa, e faria a árvore
     * anunciar "em andamento" um nó que a pessoa só comentou.
     */
    @Transactional
    public CurriculumDtos.PinnedNoteView save(Long userId, String nodeCode, String note) {
        Node node = curriculumQueryService.requireNode(nodeCode);
        UserNodeKey key = new UserNodeKey(userId, node.getId());

        UserProgress progress =
                progressRepository
                        .findById(key)
                        .orElseGet(
                                () ->
                                        new UserProgress(
                                                key, ProgressStatus.AVAILABLE, clock.instant()));

        progress.setPinnedNote(normalize(note));
        progress.setUpdatedAt(clock.instant());
        progressRepository.save(progress);

        return new CurriculumDtos.PinnedNoteView(node.getCode(), progress.getPinnedNote());
    }

    /**
     * Nota em branco é pedido de limpeza, não conteúdo.
     *
     * <p>Sem isto, "sem anotação" teria duas representações — {@code null} e {@code ""} — e a UI
     * teria de checar as duas em todo lugar para não abrir um bloco de anotação vazio.
     */
    private static String normalize(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
