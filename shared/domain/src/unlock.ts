/**
 * Desbloqueio de nó a partir do grafo de pré-requisitos.
 *
 * O backend já devolve o status resolvido — esta implementação serve para a UI reagir na hora
 * (marcar o sucessor como liberado assim que o quiz passa, sem recarregar a árvore) e para
 * explicar *por que* um nó está travado.
 */

export type ProgressStatus = 'LOCKED' | 'AVAILABLE' | 'IN_PROGRESS' | 'COMPLETED';

/** `ALL`: todos os pré-requisitos concluídos. `ANY`: pelo menos um (ver D13). */
export type UnlockRule = 'ALL' | 'ANY';

export interface GraphNode {
  readonly code: string;
  readonly unlockRule: UnlockRule;
  readonly prereqCodes: readonly string[];
}

/** Um nó sem pré-requisito é sempre aberto. */
export function isUnlocked(node: GraphNode, completedCodes: ReadonlySet<string>): boolean {
  if (node.prereqCodes.length === 0) return true;

  return node.unlockRule === 'ANY'
    ? node.prereqCodes.some((code) => completedCodes.has(code))
    : node.prereqCodes.every((code) => completedCodes.has(code));
}

/**
 * Pré-requisitos que ainda faltam para abrir o nó.
 *
 * Com a regra `ANY` basta um, então a lista devolvida são as alternativas ainda disponíveis — a UI
 * apresenta como "conclua qualquer um destes" em vez de "conclua todos estes".
 */
export function missingPrereqs(node: GraphNode, completedCodes: ReadonlySet<string>): string[] {
  if (isUnlocked(node, completedCodes)) return [];
  return node.prereqCodes.filter((code) => !completedCodes.has(code));
}

/**
 * Resolve o status exibível de cada nó.
 *
 * Bloqueio é sempre derivado do grafo: um status persistido nunca sobrepõe o cálculo, porque o
 * currículo é dado versionado e pode mudar sob um progresso já gravado.
 */
export function resolveStatuses(
  nodes: readonly GraphNode[],
  persisted: ReadonlyMap<string, ProgressStatus>,
): Map<string, ProgressStatus> {
  const completed = new Set(
    [...persisted.entries()].filter(([, status]) => status === 'COMPLETED').map(([code]) => code),
  );

  const result = new Map<string, ProgressStatus>();
  for (const node of nodes) {
    if (!isUnlocked(node, completed)) {
      result.set(node.code, 'LOCKED');
      continue;
    }
    const stored = persisted.get(node.code);
    result.set(node.code, stored === undefined || stored === 'LOCKED' ? 'AVAILABLE' : stored);
  }
  return result;
}
