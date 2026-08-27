import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { api } from '../api/client.ts';

/**
 * Registra cada mudança de rota na coleta de uso (#84, D50).
 *
 * Existe porque o projeto não sabia se alguém usa o app: não havia como responder "quantas pessoas
 * chegaram esta semana", "de qual link vieram" ou "isso está sendo aberto no celular". Com o
 * cadastro aberto, essas perguntas deixaram de ser curiosidade.
 *
 * **O que este arquivo manda é tudo que o cliente decide.** Dispositivo, navegador, sistema, idioma
 * e país o servidor deriva da própria requisição, e os eventos de funil (cadastro criado, e-mail
 * confirmado, primeiro drill) são emitidos pelo backend — vindos daqui seriam forjáveis. Não há
 * cookie de rastreio, não há identificador de visitante, e nenhum script de terceiro entra na
 * página: quem separa "100 acessos" de "100 pessoas" é um hash com sal diário que só existe na
 * memória do servidor.
 *
 * **E nada aqui pode quebrar tela.** `recordUsage` não rejeita, e o efeito não tem estado que a UI
 * leia — a landing (D33) continua renderizando com o backend frio, offline, ou com a coleta
 * desligada no servidor.
 */
export function useUsageTracking(): void {
  const { pathname, search } = useLocation();
  // React Router remonta e o StrictMode roda o efeito duas vezes em dev: sem esta marca, cada
  // navegação viraria dois acessos, e o painel contaria o dobro em desenvolvimento.
  const ultimo = useRef<string | null>(null);

  useEffect(() => {
    const chave = pathname + search;
    if (ultimo.current === chave) return;
    ultimo.current = chave;

    // O `catch` é redundante com o cliente, que já não rejeita, e fica assim mesmo: é aqui que
    // uma regressão lá viraria erro não tratado na primeira tela de quem recebe o link.
    api
      .recordUsage({
        caminho: pathname,
        referrer: referrerHost(),
        ...utm(search),
      })
      .catch(() => {});
  }, [pathname, search]);
}

/**
 * Só o host de onde a pessoa veio, e nunca o próprio app.
 *
 * O caminho e a query do referrer ficam de fora de propósito: é navegação em site de terceiro, e
 * não é da conta deste projeto. Navegação interna vira string vazia — o servidor a descarta, e
 * contá-la faria o host do próprio app ser a "origem" mais comum do painel.
 */
function referrerHost(): string {
  try {
    if (!document.referrer) return '';
    const origem = new URL(document.referrer);
    return origem.host === window.location.host ? '' : origem.host;
  } catch {
    return '';
  }
}

/** Os três `utm_*`, e só eles: o resto da query string é descartado e nunca sai daqui. */
function utm(search: string): {
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
} {
  const params = new URLSearchParams(search);
  const campos = {
    utmSource: params.get('utm_source') ?? undefined,
    utmMedium: params.get('utm_medium') ?? undefined,
    utmCampaign: params.get('utm_campaign') ?? undefined,
  };
  return campos;
}
