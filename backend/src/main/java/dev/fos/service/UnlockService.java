package dev.fos.service;

import dev.fos.model.Node;
import dev.fos.model.ProgressStatus;
import dev.fos.model.UnlockRule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Resolve o estado de cada nó a partir do grafo de pré-requisitos e do progresso persistido.
 *
 * <p>Bloqueio é sempre derivado, nunca gravado: mudar o currículo (que é dado versionado, D11)
 * não deve exigir reescrever linhas de progresso.
 */
@Service
public class UnlockService {

    /**
     * Calcula o estado exibível de cada nó.
     *
     * @param nodes      todos os nós do currículo
     * @param persisted  status persistido por id de nó (LOCKED é ignorado se aparecer)
     * @return status efetivo por id de nó
     */
    public Map<Long, ProgressStatus> resolveStatuses(
            List<Node> nodes, Map<Long, ProgressStatus> persisted) {

        Set<Long> completed = persisted.entrySet().stream()
                .filter(e -> e.getValue() == ProgressStatus.COMPLETED)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());

        Map<Long, ProgressStatus> result = new HashMap<>(nodes.size());
        for (Node node : nodes) {
            if (!isUnlocked(node, completed)) {
                result.put(node.getId(), ProgressStatus.LOCKED);
                continue;
            }
            ProgressStatus stored = persisted.get(node.getId());
            result.put(
                    node.getId(),
                    stored == null || stored == ProgressStatus.LOCKED ? ProgressStatus.AVAILABLE : stored);
        }
        return result;
    }

    /**
     * Um nó está desbloqueado quando seus pré-requisitos são satisfeitos.
     *
     * <p>{@link UnlockRule#ALL} exige todos concluídos; {@link UnlockRule#ANY} exige ao menos um —
     * o caso de "qualquer passagem serve" do Módulo 4 (D13). Nó sem pré-requisito é sempre aberto.
     */
    public boolean isUnlocked(Node node, Set<Long> completedNodeIds) {
        Set<Long> prereqs = node.getPrereqNodeIds();
        if (prereqs.isEmpty()) {
            return true;
        }
        return node.getUnlockRule() == UnlockRule.ANY
                ? prereqs.stream().anyMatch(completedNodeIds::contains)
                : completedNodeIds.containsAll(prereqs);
    }
}
