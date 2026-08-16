package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fos.model.Belt;
import dev.fos.model.CurriculumModule;
import dev.fos.model.Node;
import dev.fos.model.ProgressStatus;
import dev.fos.model.UnlockRule;
import dev.fos.model.VideoRef;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnlockServiceTest {

    private final UnlockService service = new UnlockService();

    @Test
    @DisplayName("nó sem pré-requisito está sempre desbloqueado")
    void entryNodeIsAlwaysUnlocked() {
        Node node = node(1L, UnlockRule.ALL);

        assertThat(service.isUnlocked(node, Set.of())).isTrue();
    }

    @Test
    @DisplayName("regra ALL exige todos os pré-requisitos concluídos")
    void allRuleRequiresEveryPrereq() {
        Node node = node(3L, UnlockRule.ALL, 1L, 2L);

        assertThat(service.isUnlocked(node, Set.of(1L))).isFalse();
        assertThat(service.isUnlocked(node, Set.of(1L, 2L))).isTrue();
    }

    @Test
    @DisplayName("regra ANY exige apenas um — o caso 'qualquer passagem serve' (D13)")
    void anyRuleRequiresJustOne() {
        Node node = node(3L, UnlockRule.ANY, 1L, 2L);

        assertThat(service.isUnlocked(node, Set.of())).isFalse();
        assertThat(service.isUnlocked(node, Set.of(2L))).isTrue();
    }

    @Test
    @DisplayName("nó bloqueado ignora qualquer status persistido")
    void lockedOverridesPersistedStatus() {
        Node open = node(1L, UnlockRule.ALL);
        Node gated = node(2L, UnlockRule.ALL, 1L);

        Map<Long, ProgressStatus> resolved = service.resolveStatuses(
                List.of(open, gated), Map.of(2L, ProgressStatus.IN_PROGRESS));

        assertThat(resolved.get(2L)).isEqualTo(ProgressStatus.LOCKED);
    }

    @Test
    @DisplayName("nó desbloqueado e nunca tocado aparece como AVAILABLE")
    void untouchedUnlockedNodeIsAvailable() {
        Node open = node(1L, UnlockRule.ALL);

        Map<Long, ProgressStatus> resolved = service.resolveStatuses(List.of(open), Map.of());

        assertThat(resolved.get(1L)).isEqualTo(ProgressStatus.AVAILABLE);
    }

    @Test
    @DisplayName("concluir um nó desbloqueia o sucessor")
    void completingPrereqUnlocksSuccessor() {
        Node first = node(1L, UnlockRule.ALL);
        Node second = node(2L, UnlockRule.ALL, 1L);

        Map<Long, ProgressStatus> resolved = service.resolveStatuses(
                List.of(first, second), Map.of(1L, ProgressStatus.COMPLETED));

        assertThat(resolved.get(2L)).isEqualTo(ProgressStatus.AVAILABLE);
    }

    private Node node(long id, UnlockRule rule, Long... prereqs) {
        CurriculumModule module = new CurriculumModule("M0", "Fundamentos", "resumo", 0);
        Node node = new Node(
                module, "N" + id, "titulo", Belt.BRANCA, "conceito", 1, rule,
                new VideoRef(null, null, null, null));
        setId(node, id);
        node.setPrereqNodeIds(new LinkedHashSet<>(List.of(prereqs)));
        return node;
    }

    /** Ids são gerados pelo banco; nos testes de regra pura eles são fixados por reflexão. */
    private void setId(Node node, long id) {
        try {
            Field field = Node.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(node, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
