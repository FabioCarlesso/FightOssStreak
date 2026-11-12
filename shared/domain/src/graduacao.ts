/**
 * Próximo grau (#193, D75): aula com ou sem kimono, e quantas aulas desde o último grau.
 *
 * Os mínimos vêm **de fora** — são o que a academia de quem usa exige, digitados pela própria
 * pessoa. Nada aqui traz lista nem número de academia nenhuma: material de academia costuma ser
 * protegido, e o repositório é público.
 *
 * Regra pura espelhada no backend (D17): `countClassesSince` é a consulta
 * `TrainingSessionRepository.countClassesSince` mais `GraduationService.count`.
 */

import type { SessionAttire } from '@fos/types';

/**
 * `GI` com kimono, `NOGI` sem. Não é conteúdo No-Gi — o currículo segue só Gi (D6). Sai do tipo
 * gerado, como `SessionKind`: valor novo no backend aparece aqui sem segunda lista para manter.
 */
export type Attire = SessionAttire;

/** Rótulos do campo, na ordem do vestiário: com kimono primeiro, porque é o caso dominante. */
export const ATTIRE_LABELS: ReadonlyArray<{ value: Attire; label: string }> = [
  { value: 'GI', label: 'Com kimono' },
  { value: 'NOGI', label: 'Sem kimono' },
];

/**
 * Se a sessão registra com ou sem kimono: só o que acontece no tatame.
 *
 * Físico é fora dele, e descanso é dia sem treino — marcar kimono nos dois seria afirmar uma aula
 * que não houve. O backend recusa a combinação com 400, e a tela nem oferece o campo.
 */
export function kindRecordsAttire(kind: string): boolean {
  return kind !== 'FISICO' && kind !== 'DESCANSO';
}

/** O que conta como aula para o próximo grau: só `AULA`. Rola, drill e físico não são aula. */
export function kindCountsAsClass(kind: string): boolean {
  return kind === 'AULA';
}

export interface ClassCount {
  readonly total: number;
  readonly gi: number;
  readonly nogi: number;
  /** Aulas sem marcação de kimono: contam no total e em nenhuma das metades. */
  readonly unspecified: number;
}

export interface CountableSession {
  readonly trainedOn: string;
  readonly kind: string;
  readonly attire?: Attire | null;
}

/**
 * Aulas desde o último grau, inclusive o próprio dia — a aula do dia da graduação já conta para o
 * próximo grau, e não para o que acabou de ser dado.
 *
 * Datas `YYYY-MM-DD` se comparam como texto, sem fuso horário no meio.
 */
export function countClassesSince(
  sessions: readonly CountableSession[],
  since: string,
): ClassCount {
  let gi = 0;
  let nogi = 0;
  let unspecified = 0;
  for (const session of sessions) {
    if (!kindCountsAsClass(session.kind) || session.trainedOn < since) continue;
    if (session.attire === 'GI') gi += 1;
    else if (session.attire === 'NOGI') nogi += 1;
    else unspecified += 1;
  }
  return { total: gi + nogi + unspecified, gi, nogi, unspecified };
}

export interface GraduationLine {
  readonly done: number;
  /** `null` quando a academia não exige mínimo nesta linha — aí a linha só conta. */
  readonly minimum: number | null;
  /** Quanto falta, nunca negativo. `null` junto com `minimum`. */
  readonly remaining: number | null;
}

export interface GraduationGoalNumbers {
  readonly minTotal?: number | null;
  readonly minGi?: number | null;
  readonly minNogi?: number | null;
}

/**
 * O que o card do próximo grau mostra: feito, exigido e o que falta, por linha.
 *
 * Passar do mínimo não vira "120%" nem destaque: o app informa e não premia, como os marcos do
 * streak. E bater os mínimos não é graduar — quem decide o grau é o professor.
 */
export function graduationLines(
  goal: GraduationGoalNumbers,
  classes: ClassCount,
): { total: GraduationLine; gi: GraduationLine; nogi: GraduationLine } {
  return {
    total: line(classes.total, goal.minTotal),
    gi: line(classes.gi, goal.minGi),
    nogi: line(classes.nogi, goal.minNogi),
  };
}

function line(done: number, minimum: number | null | undefined): GraduationLine {
  if (minimum === null || minimum === undefined) {
    return { done, minimum: null, remaining: null };
  }
  return { done, minimum, remaining: Math.max(0, minimum - done) };
}

/**
 * O formulário da meta, como o `<input>` devolve: tudo texto, para "4" não virar número antes de a
 * pessoa terminar de digitar "40". Mesmo princípio do `SessionFieldsState`.
 */
export interface GraduationFormState {
  readonly lastGraduationOn: string;
  readonly currentLabel: string;
  readonly nextLabel: string;
  readonly minTotal: string;
  readonly minGi: string;
  readonly minNogi: string;
}

export interface GraduationGoalLike extends GraduationGoalNumbers {
  readonly lastGraduationOn?: string | null;
  readonly currentLabel?: string | null;
  readonly nextLabel?: string | null;
}

/** O formulário a partir da meta gravada; sem meta, tudo vazio. */
export function graduationForm(goal: GraduationGoalLike | null | undefined): GraduationFormState {
  return {
    lastGraduationOn: goal?.lastGraduationOn ?? '',
    currentLabel: goal?.currentLabel ?? '',
    nextLabel: goal?.nextLabel ?? '',
    minTotal: numberText(goal?.minTotal),
    minGi: numberText(goal?.minGi),
    minNogi: numberText(goal?.minNogi),
  };
}

/**
 * O formulário vira corpo do `PUT /api/graduacao`. Campo vazio vira `undefined` — "a academia não
 * exige" —, e não zero, que diria "exige nenhuma aula".
 */
export function toGraduationGoalBody(form: GraduationFormState) {
  return {
    lastGraduationOn: form.lastGraduationOn,
    currentLabel: form.currentLabel.trim() || undefined,
    nextLabel: form.nextLabel.trim() || undefined,
    minTotal: wholeNumber(form.minTotal),
    minGi: wholeNumber(form.minGi),
    minNogi: wholeNumber(form.minNogi),
  };
}

function numberText(value: number | null | undefined): string {
  return value === null || value === undefined ? '' : String(value);
}

function wholeNumber(text: string): number | undefined {
  const limpo = text.trim();
  if (limpo === '') return undefined;
  const numero = Number(limpo);
  return Number.isInteger(numero) && numero >= 0 ? numero : undefined;
}
