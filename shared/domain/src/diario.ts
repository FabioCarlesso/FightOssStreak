import type { Feeling, SessionKind } from '@fos/types';

// Datas de calendário e o formulário de sessão do diário, compartilhados entre web e app (#141).

/**
 * `2026-08-16` vira `16/08/2026` sem passar por `new Date`, que interpretaria a data como UTC e
 * mostraria o dia anterior para quem está a oeste de Greenwich — que é o caso aqui.
 */
export function formatDay(iso: string | undefined): string {
  if (!iso) return '';
  const [year, month, day] = iso.split('-');
  return day && month && year ? `${day}/${month}/${year}` : iso;
}

/** Hoje pelo relógio do navegador, em `YYYY-MM-DD`. É o padrão do campo de data do formulário. */
export function todayIso(): string {
  const agora = new Date();
  const mes = String(agora.getMonth() + 1).padStart(2, '0');
  const dia = String(agora.getDate()).padStart(2, '0');
  return `${agora.getFullYear()}-${mes}-${dia}`;
}

/** Primeiro e último dia do mês de uma data `YYYY-MM-DD`, sem envolver fuso horário. */
export function monthRange(iso: string): { de: string; ate: string } {
  const [year, month] = iso.split('-');
  const ano = Number(year);
  const mes = Number(month);
  // Dia 0 do mês seguinte é o último do mês corrente — a única conta de calendário que o
  // `Date` faz melhor que uma tabela de dias, e ela é feita em UTC para não escorregar de mês.
  const ultimo = new Date(Date.UTC(ano, mes, 0)).getUTCDate();
  return {
    de: `${year}-${month}-01`,
    ate: `${year}-${month}-${String(ultimo).padStart(2, '0')}`,
  };
}

/**
 * O estado do formulário de sessão, separado do componente que o desenha (#114, D56).
 *
 * Mora em `shared/` desde a #141: o web e o app escrevem a mesma sessão, e a conversão para o corpo
 * da requisição é onde um campo vazio vira "não informado" — divergir aqui seria gravar zero de um
 * lado e nada do outro.
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
