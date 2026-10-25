import { isUnlocked, type ProgressStatus, type UnlockRule } from './unlock.ts';

/**
 * "Retomar de onde parei" (#104): o nó para onde o atalho da home aponta.
 *
 * Não é uma segunda fila. A revisão vencida sai da agenda do SRS (`GET /api/reviews/today`), que o
 * servidor já devolve do mais atrasado para o menos — o atalho pega o **primeiro** dela e não
 * reordena, senão a home afirmaria duas prioridades diferentes na mesma tela. Só sem nada vencido
 * ele olha a árvore, e aí a ordem é a do currículo (módulo, depois nó, como `GET
 * /api/curriculum/tree` devolve):
 *
 * 1. revisão vencida — o primeiro da agenda;
 * 2. nó **em andamento** (`IN_PROGRESS`: drillado, quiz ainda não passou) — é literalmente onde a
 *    pessoa parou, e vem antes de abrir um nó novo;
 * 3. nó **liberado** (`AVAILABLE`) ainda não começado;
 * 4. `null` — tudo em dia.
 *
 * Nunca `LOCKED` nem `COMPLETED`. O status já vem resolvido do servidor (D17), e o pré-requisito é
 * conferido de novo aqui com `isUnlocked` contra os concluídos da própria árvore: é a mesma regra, e
 * custa nada garantir que o atalho não sugira um nó travado se os dois um dia divergirem.
 */
export interface DueItem {
  readonly nodeCode?: string;
  readonly title?: string;
}

export interface ResumeTreeNode {
  readonly code?: string;
  readonly title?: string;
  readonly status?: ProgressStatus;
  readonly unlockRule?: UnlockRule;
  readonly prereqCodes?: readonly string[];
}

export interface ResumeTreeModule {
  readonly nodes?: readonly ResumeTreeNode[];
}

export type ResumeReason = 'REVISAO' | 'EM_ANDAMENTO' | 'NOVO';

export interface ResumeTarget {
  readonly code: string;
  readonly title: string;
  readonly reason: ResumeReason;
}

export function nextNodeToResume(
  due: readonly DueItem[],
  modules: readonly ResumeTreeModule[],
): ResumeTarget | null {
  const vencido = due.find((item) => item.nodeCode);
  if (vencido?.nodeCode) {
    return { code: vencido.nodeCode, title: vencido.title ?? vencido.nodeCode, reason: 'REVISAO' };
  }

  const nodes = modules.flatMap((module) => module.nodes ?? []);
  const completed = new Set(
    nodes.filter((node) => node.status === 'COMPLETED' && node.code).map((node) => node.code!),
  );
  const elegivel = (node: ResumeTreeNode, status: ProgressStatus) =>
    node.code !== undefined &&
    node.status === status &&
    isUnlocked(
      {
        code: node.code,
        unlockRule: node.unlockRule ?? 'ALL',
        prereqCodes: node.prereqCodes ?? [],
      },
      completed,
    );

  const emAndamento = nodes.find((node) => elegivel(node, 'IN_PROGRESS'));
  if (emAndamento?.code) {
    return {
      code: emAndamento.code,
      title: emAndamento.title ?? emAndamento.code,
      reason: 'EM_ANDAMENTO',
    };
  }

  const novo = nodes.find((node) => elegivel(node, 'AVAILABLE'));
  if (novo?.code) return { code: novo.code, title: novo.title ?? novo.code, reason: 'NOVO' };

  return null;
}
