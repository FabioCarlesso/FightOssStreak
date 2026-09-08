import type { Feeling, SessionKind } from '@fos/types';

/**
 * Como o diário é nomeado na tela (#114, D56/D57).
 *
 * Vive aqui, e não dentro dos componentes, pelo mesmo motivo do `recall.ts`: três telas leem estes
 * rótulos — a linha do tempo, o formulário e o detalhe —, e rótulo duplicado é rótulo que diverge.
 *
 * A ordem é a do vestiário, não a do enum: `AULA` primeiro porque é o caso dominante, `DESCANSO`
 * por último porque é o único que não conta como dia de treino.
 */
export const SESSION_KIND_LABELS: ReadonlyArray<{
  value: SessionKind;
  label: string;
  hint: string;
}> = [
  { value: 'AULA', label: 'Aula', hint: 'Treino com professor' },
  { value: 'DRILL', label: 'Drill', hint: 'Repetição de técnica' },
  { value: 'ROLA', label: 'Rola', hint: 'Só luta' },
  { value: 'FISICO', label: 'Físico', hint: 'Fora do tatame' },
  { value: 'OUTRO', label: 'Outro', hint: 'Seminário, open mat, o que for' },
  { value: 'DESCANSO', label: 'Descanso', hint: 'Fica no diário e não conta no streak' },
];

/** Valor desconhecido cai de volta nele próprio: tipo novo no backend não apaga a linha. */
export function sessionKindLabel(kind: string | undefined): string {
  if (!kind) return '';
  return SESSION_KIND_LABELS.find((option) => option.value === kind)?.label ?? kind;
}

/**
 * Sensação: guardada e mostrada, nunca interpretada (D57).
 *
 * Os rótulos descrevem o corpo e não prescrevem nada — não há "descanse amanhã" nem faixa
 * saudável, porque isso seria conselho, e o FOS não aconselha (D1).
 */
export const FEELING_LABELS: ReadonlyArray<{ value: Feeling; label: string; icon: string }> = [
  { value: 'BEM', label: 'Bem', icon: '🙂' },
  { value: 'NEUTRO', label: 'Neutro', icon: '😐' },
  { value: 'MAL', label: 'Mal', icon: '😖' },
];

export function feelingLabel(feeling: string | undefined): string {
  if (!feeling) return '';
  return FEELING_LABELS.find((option) => option.value === feeling)?.label ?? feeling;
}

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
