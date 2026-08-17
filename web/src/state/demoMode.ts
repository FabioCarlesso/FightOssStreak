import { createContext, useContext } from 'react';

const STORAGE_KEY = 'fos.demo';

export interface DemoMode {
  readonly enabled: boolean;
  readonly setEnabled: (enabled: boolean) => void;
}

/**
 * Modo demonstração: percorrer o currículo inteiro ignorando os pré-requisitos.
 *
 * Existe para revisar conteúdo — conceito, vídeo, redação das perguntas — sem ter que passar no
 * quiz de cada nó anterior. É ferramenta de inspeção do dono do app, não recurso de produto (D31):
 * o bloqueio continua sendo derivado pelo backend, os contadores continuam reais e a demonstração
 * **não grava nada**, senão a varredura destravaria nós de verdade e contaminaria as métricas de
 * `docs/05-mvp-web-plano.md`.
 *
 * O provider vive em `DemoModeProvider.tsx`: arquivo que exporta componente não pode exportar
 * hook junto sem quebrar o fast refresh (e o lint).
 */
export const DemoModeContext = createContext<DemoMode | null>(null);

export function useDemoMode(): DemoMode {
  const mode = useContext(DemoModeContext);
  if (!mode) {
    throw new Error('useDemoMode precisa de um <DemoModeProvider> acima na árvore');
  }
  return mode;
}

/**
 * `sessionStorage`, não `localStorage`: o modo precisa sobreviver a abrir um nó e voltar e a um
 * F5, mas um modo que ignora a progressão não pode ficar ligado por semanas sem que se note.
 */
export function readStoredDemoMode(): boolean {
  try {
    return sessionStorage.getItem(STORAGE_KEY) === 'on';
  } catch {
    return false;
  }
}

export function storeDemoMode(enabled: boolean): void {
  try {
    if (enabled) sessionStorage.setItem(STORAGE_KEY, 'on');
    else sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Armazenamento bloqueado pelo navegador: o modo segue valendo na tela, só não sobrevive ao
    // recarregamento. Perder isso é bem menos grave que a tela quebrar.
  }
}
