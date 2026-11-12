import type { Attire } from '@fos/domain';

/**
 * O último "com ou sem kimono" que a pessoa registrou (#193), para a próxima sessão já vir com ele
 * marcado — registrar a aula não pode custar um toque a mais.
 *
 * Fica **só neste navegador**, como o tema: é conveniência de quem digita, não dado da conta. Sem
 * armazenamento (aba privada, bloqueio) a tela só vem sem nada marcado, e o registro continua
 * funcionando.
 */
export const CHAVE_DO_KIMONO = 'fos.ultimoKimono';

export function lerUltimoKimono(): Attire | '' {
  try {
    const salvo = localStorage.getItem(CHAVE_DO_KIMONO);
    return salvo === 'GI' || salvo === 'NOGI' ? salvo : '';
  } catch {
    return '';
  }
}

export function lembrarKimono(attire: Attire | '' | null | undefined): void {
  if (attire !== 'GI' && attire !== 'NOGI') return;
  try {
    localStorage.setItem(CHAVE_DO_KIMONO, attire);
  } catch {
    // Sem armazenamento a próxima sessão só não vem marcada.
  }
}
