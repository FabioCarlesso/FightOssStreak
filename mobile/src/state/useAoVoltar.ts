import { useFocusEffect } from 'expo-router';
import { useCallback, useRef } from 'react';

/**
 * Recarrega a tela quando ela volta ao foco, sem repetir a carga da primeira montagem.
 *
 * As abas do Expo Router **não desmontam**: quem registra um drill no nó e volta para Hoje veria o
 * streak de antes, porque a tela carregou uma vez só. No web isso não acontece porque cada
 * navegação remonta a página. Foi pego no emulador, com o streak em 0 depois do registro.
 */
export function useAoVoltar(recarregar: () => void): void {
  const primeira = useRef(true);
  useFocusEffect(
    useCallback(() => {
      if (primeira.current) {
        primeira.current = false;
        return;
      }
      recarregar();
    }, [recarregar]),
  );
}
