import type { Feeling, SessionKind } from '@fos/types';

/**
 * O estado do formulário de sessão, separado do componente que o desenha (#114, D56).
 *
 * Mora fora do `.tsx` pelo mesmo motivo do `demoMode.ts`: arquivo que exporta componente não pode
 * exportar função junto sem quebrar o fast refresh (e o lint).
 *
 * Tudo é `string` aqui, inclusive duração e peso: é o que o `<input>` devolve, e converter na
 * digitação faria "78," virar 78 antes de a pessoa terminar de escrever "78,4".
 */
export interface SessionFieldsState {
  readonly trainedOn: string;
  readonly kind: SessionKind;
  readonly durationMinutes: string;
  readonly feeling: Feeling | '';
  readonly weightKg: string;
  readonly learned: string;
  readonly improve: string;
}

export function emptySessionFields(trainedOn: string): SessionFieldsState {
  return {
    trainedOn,
    kind: 'AULA',
    durationMinutes: '',
    feeling: '',
    weightKg: '',
    learned: '',
    improve: '',
  };
}

/**
 * O estado da tela vira corpo de requisição.
 *
 * Campo vazio vira `undefined`, e não string vazia ou zero: no backend, ausente é "não informado",
 * e um zero em duração diria que o treino durou zero minutos.
 */
export function toSessionBody(fields: SessionFieldsState) {
  const duracao = Number.parseInt(fields.durationMinutes, 10);
  const peso = Number.parseFloat(fields.weightKg.replace(',', '.'));
  return {
    trainedOn: fields.trainedOn,
    kind: fields.kind,
    durationMinutes: Number.isFinite(duracao) && duracao > 0 ? duracao : undefined,
    feeling: fields.feeling || undefined,
    weightKg: Number.isFinite(peso) && peso > 0 ? peso : undefined,
    learned: fields.learned.trim() || undefined,
    improve: fields.improve.trim() || undefined,
  };
}
