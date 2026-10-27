import { useSyncExternalStore } from 'react';

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

/** A escolha quando o `localStorage` não responde: vale até fechar a aba. */
let semArmazenamento: PreferenciaDeTema = 'sistema';

export function lerPreferenciaDeTema(): PreferenciaDeTema {
  try {
    const salvo = localStorage.getItem(CHAVE_DO_TEMA);
    return salvo === 'escuro' || salvo === 'claro' ? salvo : 'sistema';
  } catch {
    return semArmazenamento;
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
  semArmazenamento = preferencia;
  try {
    if (preferencia === 'sistema') localStorage.removeItem(CHAVE_DO_TEMA);
    else localStorage.setItem(CHAVE_DO_TEMA, preferencia);
  } catch {
    // Sem armazenamento a escolha só não sobrevive ao próximo acesso; a tela continua valendo.
  }
}

/*
 * Quem mostra a escolha são dois lugares ao mesmo tempo — o atalho do cabeçalho e o seletor da
 * tela Conta —, então o estado é um só, fora dos componentes: trocar num atualiza o outro.
 */
const ouvintes = new Set<() => void>();

function assinar(ouvinte: () => void): () => void {
  ouvintes.add(ouvinte);
  return () => ouvintes.delete(ouvinte);
}

/** Aplica na hora, guarda no navegador e avisa quem estiver mostrando a escolha. */
export function escolherTema(preferencia: PreferenciaDeTema): void {
  aplicarTema(preferencia);
  gravar(preferencia);
  ouvintes.forEach((ouvinte) => ouvinte());
}

/** A preferência atual e o jeito de trocá-la. */
export function useTema(): [PreferenciaDeTema, (preferencia: PreferenciaDeTema) => void] {
  return [useSyncExternalStore(assinar, lerPreferenciaDeTema), escolherTema];
}

const CONSULTA_CLARO = '(prefers-color-scheme: light)';

function assinarSistema(ouvinte: () => void): () => void {
  if (typeof window.matchMedia !== 'function') return () => undefined;
  const consulta = window.matchMedia(CONSULTA_CLARO);
  consulta.addEventListener('change', ouvinte);
  return () => consulta.removeEventListener('change', ouvinte);
}

function sistemaEstaClaro(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(CONSULTA_CLARO).matches;
}

/**
 * O tema que está na tela agora: a escolha, ou o do sistema quando a escolha é Sistema. Sistema
 * que não diz nada é escuro, como no CSS gerado.
 */
export function useTemaEfetivo(): 'escuro' | 'claro' {
  const [preferencia] = useTema();
  const claro = useSyncExternalStore(assinarSistema, sistemaEstaClaro);
  if (preferencia !== 'sistema') return preferencia;
  return claro ? 'claro' : 'escuro';
}
