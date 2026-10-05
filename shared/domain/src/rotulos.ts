import type { Feeling, SessionKind } from '@fos/types';
import type { Recall } from './srs.ts';

// Rótulos de tela que o web e o app compartilham (#141). Não são regra de negócio, mas são texto
// que precisa ser igual nos dois clientes: a mesma nota com dois nomes seria dois produtos.

/**
 * Como a auto-avaliação do drill é nomeada na tela.
 *
 * Vive aqui, e não dentro de uma tela, porque vários lugares a usam — no web e no app (#141): o
 * formulário, para escolher, e o histórico, para reler. Rótulo duplicado é rótulo que diverge — e aqui a divergência
 * seria a mesma nota aparecendo com dois nomes na mesma página.
 */
export const RECALL_LABELS: ReadonlyArray<{ value: Recall; label: string; hint: string }> = [
  { value: 'FORGOT', label: 'Não lembrava', hint: 'Precisou reaprender do zero' },
  { value: 'HARD', label: 'Travei', hint: 'Lembrava, mas saiu errado' },
  { value: 'OK', label: 'Saiu', hint: 'Funcionou, com esforço' },
  { value: 'EASY', label: 'Saiu limpo', hint: 'Sem pensar' },
];

/**
 * O histórico vem do backend com o nome do enum. Valor desconhecido cai de volta nele próprio em
 * vez de sumir: um recall novo no backend não pode apagar a linha inteira da tela.
 */
export function recallLabel(recall: string | undefined): string {
  if (!recall) return '';
  return RECALL_LABELS.find((option) => option.value === recall)?.label ?? recall;
}

/**
 * Como o diário é nomeado na tela (#114, D56/D57).
 *
 * Vive aqui, e não dentro dos componentes, pelo mesmo motivo dos rótulos de recall: várias telas,
 * no web e no app, leem estes rótulos, e rótulo duplicado é rótulo que diverge.
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
