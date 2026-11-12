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
 * Com a checklist do próximo grau (#196, D78), os passos 2 e 3 olham **primeiro** os nós ligados a
 * ela (`preferred`), e só depois a árvore inteira. É preferência no atalho, e nunca reordenação: a
 * agenda do SRS (passo 1) continua vindo antes e na ordem do servidor (D56c).
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
  /** O nó foi escolhido por estar na checklist do próximo grau (#196). */
  readonly nextGrade?: boolean;
}

export function nextNodeToResume(
  due: readonly DueItem[],
  modules: readonly ResumeTreeModule[],
  preferred: ReadonlySet<string> = new Set(),
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

  // A checklist do próximo grau primeiro, a árvore inteira depois (#196). Sem checklist, `preferred`
  // é vazio e os dois primeiros passos não acham nada: o comportamento da #104 fica idêntico.
  const naChecklist = nodes.filter((node) => node.code !== undefined && preferred.has(node.code));
  const candidatos: Array<[readonly ResumeTreeNode[], ProgressStatus, boolean]> = [
    [naChecklist, 'IN_PROGRESS', true],
    [naChecklist, 'AVAILABLE', true],
    [nodes, 'IN_PROGRESS', false],
    [nodes, 'AVAILABLE', false],
  ];
  for (const [lista, status, doGrau] of candidatos) {
    const achado = lista.find((node) => elegivel(node, status));
    if (achado?.code) {
      return {
        code: achado.code,
        title: achado.title ?? achado.code,
        reason: status === 'IN_PROGRESS' ? 'EM_ANDAMENTO' : 'NOVO',
        ...(doGrau ? { nextGrade: true } : {}),
      };
    }
  }

  return null;
}
