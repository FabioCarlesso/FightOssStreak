import type { FeedbackCategory } from '@fos/types';

/**
 * Como a categoria de feedback é nomeada na tela.
 *
 * O dono prioriza pela categoria antes de abrir cada item (docs/13-feedback-usuarios.md) — daí
 * ela ser obrigatória no formulário, e não um campo livre de "assunto" além da mensagem.
 */
export const FEEDBACK_CATEGORY_LABELS: ReadonlyArray<{
  value: FeedbackCategory;
  label: string;
}> = [
  { value: 'BUG', label: 'Bug' },
  { value: 'CONTEUDO_ERRADO', label: 'Conteúdo errado' },
  { value: 'TROCA_DE_VIDEO', label: 'Troca de vídeo' },
  { value: 'SUGESTAO_FUNCIONALIDADE', label: 'Sugestão de funcionalidade' },
  { value: 'OUTRO', label: 'Outro' },
];

/** Valor desconhecido cai de volta nele próprio, em vez de sumir da tela do dono. */
export function feedbackCategoryLabel(category: string | undefined): string {
  if (!category) return '';
  return FEEDBACK_CATEGORY_LABELS.find((option) => option.value === category)?.label ?? category;
}

export const FEEDBACK_STATUS_LABELS: Record<string, string> = {
  ABERTO: 'Aberto',
  EM_ANALISE: 'Em análise',
  RESOLVIDO: 'Resolvido',
  RECUSADO: 'Recusado',
};

export function feedbackStatusLabel(status: string | undefined): string {
  if (!status) return '';
  return FEEDBACK_STATUS_LABELS[status] ?? status;
}
