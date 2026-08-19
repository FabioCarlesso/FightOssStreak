import type { Recall } from '@fos/domain';

/**
 * Como a auto-avaliação do drill é nomeada na tela.
 *
 * Vive aqui, e não dentro do `DrillForm`, porque dois lugares a usam: o formulário, para escolher,
 * e o histórico do nó, para reler. Rótulo duplicado é rótulo que diverge — e aqui a divergência
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
