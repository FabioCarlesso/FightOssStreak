import { useCallback, useState } from 'react';

/**
 * Tema da interface (D74, #187): segue o sistema por padrão, e a tela Conta deixa escolher
 * escuro ou claro à mão.
 *
 * A escolha fica **só neste navegador** (`localStorage`): não é dado de conta, não vai para a API e
 * a coleta de uso (D50) não a registra. Sem `localStorage` (aba privada, armazenamento bloqueado)
 * a escolha vale até fechar a aba, e o tema volta a seguir o sistema.
 *
 * Quem pinta é o CSS gerado de `shared/tokens`: `data-theme` no `<html>` vence o
 * `prefers-color-scheme`, e sem o atributo vale o sistema. O `public/tema.js` faz o mesmo antes do
 * bundle, para a página não piscar escura antes de ficar clara — a chave e os valores dele têm de
 * ser os daqui (há teste que confere).
 */

export type PreferenciaDeTema = 'sistema' | 'escuro' | 'claro';

export const CHAVE_DO_TEMA = 'fos.tema';

export const OPCOES_DE_TEMA: ReadonlyArray<{ valor: PreferenciaDeTema; rotulo: string }> = [
  { valor: 'sistema', rotulo: 'Sistema' },
  { valor: 'escuro', rotulo: 'Escuro' },
  { valor: 'claro', rotulo: 'Claro' },
];

const ATRIBUTO: Record<PreferenciaDeTema, 'dark' | 'light' | null> = {
  sistema: null,
  escuro: 'dark',
  claro: 'light',
};

export function lerPreferenciaDeTema(): PreferenciaDeTema {
  try {
    const salvo = localStorage.getItem(CHAVE_DO_TEMA);
    return salvo === 'escuro' || salvo === 'claro' ? salvo : 'sistema';
  } catch {
    return 'sistema';
  }
}

/** Põe (ou tira) o `data-theme` do `<html>`. É o que troca o tema sem recarregar. */
export function aplicarTema(
  preferencia: PreferenciaDeTema,
  raiz: HTMLElement = document.documentElement,
): void {
  const atributo = ATRIBUTO[preferencia];
  if (atributo) raiz.setAttribute('data-theme', atributo);
  else raiz.removeAttribute('data-theme');
}

function gravar(preferencia: PreferenciaDeTema): void {
  try {
    if (preferencia === 'sistema') localStorage.removeItem(CHAVE_DO_TEMA);
    else localStorage.setItem(CHAVE_DO_TEMA, preferencia);
  } catch {
    // Sem armazenamento a escolha só não sobrevive ao próximo acesso; a tela continua valendo.
  }
}

/** A preferência atual e o jeito de trocá-la: aplica na hora e guarda no navegador. */
export function useTema(): [PreferenciaDeTema, (preferencia: PreferenciaDeTema) => void] {
  const [preferencia, setPreferencia] = useState(lerPreferenciaDeTema);
  const escolher = useCallback((nova: PreferenciaDeTema) => {
    aplicarTema(nova);
    gravar(nova);
    setPreferencia(nova);
  }, []);
  return [preferencia, escolher];
}
