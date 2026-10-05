import Constants from 'expo-constants';
import type { ReactNode } from 'react';

import { TelaLogin } from '../screens/TelaLogin';
import { TelaAtualizar, TelaAviso, TelaBloqueada } from '../screens/TelasDePortao';
import { useApi } from '../state/api';
import { useSessao } from '../state/session';
import { useAsync } from '../state/useAsync';
import { abaixoDaMinima } from '../state/versao';
import { Carregando, FalhaDaApi } from './ui';

/**
 * Os portões do app, na ordem em que decidem: versão mínima, conta e aviso. É a mesma sequência do
 * web (`AuthGate` e `DisclaimerGate`), com a versão na frente porque só o app instalado envelhece.
 */

/**
 * A versão vem antes do login: app velho não deve nem tentar entrar. Falha ao consultar **não**
 * trava: a tela de atualização bloqueia o app inteiro, e a falha de rede aparece de novo no portão
 * seguinte, com o botão de tentar outra vez.
 */
export function PortaoVersao({
  children,
  versaoAtual = Constants.expoConfig?.version,
}: {
  children: ReactNode;
  versaoAtual?: string;
}) {
  const api = useApi();
  const versao = useAsync(() => api.getAppVersion(), []);

  if (versao.loading && !versao.data && !versao.error) return <Carregando />;
  const minima = versao.data?.minimumVersion;
  if (minima && abaixoDaMinima(versaoAtual, minima)) return <TelaAtualizar minima={minima} />;
  return <>{children}</>;
}

/** Sem token, login. Conta bloqueada, o motivo (#90). Só depois disso, o app. */
export function PortaoConta({ children }: { children: ReactNode }) {
  const { fase, recarregar } = useSessao();

  switch (fase.tipo) {
    case 'carregando':
      return <Carregando />;
    case 'sem-token':
      return <TelaLogin />;
    case 'erro':
      return <FalhaDaApi erro={fase.erro} onRetry={recarregar} />;
    case 'conta':
      return fase.conta.accessStatus === 'RECUSADO' ? <TelaBloqueada /> : <>{children}</>;
  }
}

/** Aceite do aviso por versão, uma vez por conta (regra 5). */
export function PortaoAviso({ children }: { children: ReactNode }) {
  const api = useApi();
  const { fase } = useSessao();
  const status = useAsync(() => api.getDisclaimer(), []);

  if (status.loading && !status.data) return <Carregando />;
  if (status.error && !status.data)
    return <FalhaDaApi erro={status.error} onRetry={status.reload} />;
  if (status.data?.accepted) return <>{children}</>;
  return (
    <TelaAviso
      versao={status.data?.currentVersion ?? ''}
      nome={fase.tipo === 'conta' ? fase.conta.displayName : undefined}
      onAceito={status.reload}
    />
  );
}
