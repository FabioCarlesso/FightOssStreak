import { useEffect, useState } from 'react';
import { api } from '../api/client.ts';
import { useAccount } from '../state/account.ts';
import { clearDemoSession, remainingLabel } from '../state/demoAccount.ts';

/**
 * Faixa de "nada aqui é seu" (#62).
 *
 * Distinta da faixa do modo demonstração (D31), e o texto precisa deixar isso claro: aquela avisa
 * que a árvore está aberta para inspeção e **nada é gravado**; esta avisa o contrário — grava, sim,
 * numa conta que não é de ninguém e que some sozinha.
 *
 * Fica no topo de todas as telas do app porque a sessão atravessa a navegação: sem indicador, um
 * streak de demonstração é indistinguível de um streak conquistado.
 */
export function DemoAccountBanner() {
  const { account, reload } = useAccount();
  const expiresAt = account.demoExpiresAt;
  const [, tick] = useState(0);

  // Um tique por minuto só para o "restam" não congelar. Sem contagem regressiva de segundos: o
  // que interessa é a ordem de grandeza, e um cronômetro na faixa roubaria a atenção da tela.
  useEffect(() => {
    if (!expiresAt) return undefined;
    const timer = setInterval(() => tick((value) => value + 1), 60_000);
    return () => clearInterval(timer);
  }, [expiresAt]);

  if (!expiresAt) return null;

  async function sair() {
    clearDemoSession();
    try {
      await api.logout();
    } finally {
      // Mesmo se o logout falhar, recarregar a conta é o certo: ou a sessão caiu, ou ela venceu.
      reload();
    }
  }

  return (
    <div className="demo-account" role="status">
      <p className="demo-account__text">
        <strong>Você está numa demonstração.</strong> Esta conta é temporária e some sozinha em{' '}
        {remainingLabel(expiresAt)} — o progresso, o streak e as anotações são exemplos, não são
        seus. O que você fizer aqui é gravado de verdade, mas só nela.
      </p>
      <button type="button" onClick={() => void sair()}>
        Quero acesso de verdade
      </button>
    </div>
  );
}
