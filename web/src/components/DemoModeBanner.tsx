import { useDemoMode } from '../state/demoMode.ts';

/**
 * Faixa de "o que você está vendo não é a sua progressão".
 *
 * Fica no topo de todas as telas, e não só na árvore, porque o modo atravessa a navegação: sem
 * indicador, um nó aberto em demonstração seria indistinguível de um nó realmente destravado.
 */
export function DemoModeBanner() {
  const demo = useDemoMode();

  if (!demo.enabled) return null;

  return (
    <div className="demo-banner" role="status">
      <p className="demo-banner__text">
        <strong>Modo demonstração ligado.</strong> A árvore está aberta para inspeção: os
        pré-requisitos estão sendo ignorados e nada é gravado no progresso.
      </p>
      <button type="button" onClick={() => demo.setEnabled(false)}>
        Desligar
      </button>
    </div>
  );
}
