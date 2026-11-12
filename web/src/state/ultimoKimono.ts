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

/**
 * Guarda a escolha, inclusive "Não dizer": quem desmarcou de propósito não quer o kimono antigo
 * voltando marcado na próxima sessão. Quem chama decide se a sessão registrava kimono — sessão de
 * físico ou descanso não deve apagar a lembrança da última aula.
 */
export function lembrarKimono(attire: Attire | ''): void {
  try {
    if (attire === '') localStorage.removeItem(CHAVE_DO_KIMONO);
    else localStorage.setItem(CHAVE_DO_KIMONO, attire);
  } catch {
    // Sem armazenamento a próxima sessão só não vem marcada.
  }
}
