import { type ReactNode, useCallback, useMemo, useState } from 'react';
import { type DemoMode, DemoModeContext, readStoredDemoMode, storeDemoMode } from './demoMode.ts';

/** Estado do modo demonstração, compartilhado entre a árvore, o nó e a faixa do topo (D31). */
export function DemoModeProvider({ children }: { children: ReactNode }) {
  const [enabled, setEnabledState] = useState(readStoredDemoMode);

  const setEnabled = useCallback((next: boolean) => {
    setEnabledState(next);
    storeDemoMode(next);
  }, []);

  const value = useMemo<DemoMode>(() => ({ enabled, setEnabled }), [enabled, setEnabled]);

  return <DemoModeContext.Provider value={value}>{children}</DemoModeContext.Provider>;
}
