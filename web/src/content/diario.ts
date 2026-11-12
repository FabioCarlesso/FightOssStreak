// Rótulos e datas do diário moram em `@fos/domain` desde a #141, para o web e o app lerem os mesmos.
export {
  ATTIRE_LABELS,
  FEELING_LABELS,
  SESSION_KIND_LABELS,
  feelingLabel,
  formatDay,
  kindRecordsAttire,
  monthRange,
  sessionKindLabel,
  todayIso,
} from '@fos/domain';

/** "Com kimono" ou "Sem kimono"; nada quando a sessão não disse. */
export function attireLabel(attire: string | null | undefined): string {
  if (attire === 'GI') return 'Com kimono';
  if (attire === 'NOGI') return 'Sem kimono';
  return '';
}
