import { useState } from 'react';
import type { HealthHours } from '@fos/api-client';
import type { HealthPoint, HealthView } from '@fos/types';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';

/**
 * A seção de saúde do painel (#86).
 *
 * Ela responde três perguntas operacionais — **está errando?**, **está lento?**, **reiniciou?** — e
 * carrega o próprio dado, com o próprio recorte de tempo. Não é teimosia de componente: o painel de
 * uso mede dias fechados porque um número que muda depois de lido engana a leitura de tendência, e
 * aqui a leitura é a oposta — o incidente que interessa é o de agora, e esconder a hora corrente
 * seria publicar sempre a notícia de ontem.
 *
 * **O que esta seção não diz, e está escrito na tela:** se o site ficou fora do ar. Aplicação
 * parada não escreve estatística, então uma hora zerada aqui é indistinguível de uma madrugada sem
 * visita. Quem responde isso é a verificação externa em cron (`.github/workflows/saude.yml`) — e é
 * exatamente por essa razão que ela existe fora do app.
 *
 * Nada nesta tela é de alguém: são contagens de requisição por rota. A rota é sempre o padrão do
 * roteamento, nunca um caminho com segmento preenchido.
 */
export function SiteHealth() {
  const [horas, setHoras] = useState<HealthHours>(24);
  const saude = useAsync(() => api.getAdminHealth(horas), [horas]);
  const dados = saude.data;

  return (
    <section className="card">
      <header className="card__header">
        <h3>Saúde</h3>
        <div className="painel__periodos" role="group" aria-label="Janela">
          {JANELAS.map((janela) => (
            <button
              key={janela.horas}
              type="button"
              className={
                janela.horas === horas
                  ? 'painel__periodo painel__periodo--ativo'
                  : 'painel__periodo'
              }
              aria-pressed={janela.horas === horas}
              onClick={() => setHoras(janela.horas)}
            >
              {janela.rotulo}
            </button>
          ))}
        </div>
      </header>

      <p className="hint">
        O que a aplicação mede sobre si mesma: requisições, erro e latência por rota. Ela não sabe
        dizer se o site esteve fora do ar — app parado não mede nada. Essa parte é verificada de
        fora, e abre issue no repositório quando falha duas vezes seguidas.
      </p>

      {saude.loading && !dados && <p className="empty">Carregando a saúde…</p>}

      {saude.error && (
        <div className="admin-users__falha" role="alert">
          <p className="error">Não foi possível carregar a saúde: {saude.error.message}</p>
          <button type="button" onClick={saude.reload}>
            Tentar de novo
          </button>
        </div>
      )}

      {dados && <Conteudo saude={dados} />}
    </section>
  );
}

/** Os três recortes. Qualquer outro valor é `400` no backend. */
const JANELAS: readonly { horas: HealthHours; rotulo: string }[] = [
  { horas: 24, rotulo: '24 h' },
  { horas: 72, rotulo: '3 dias' },
  { horas: 168, rotulo: '7 dias' },
];

function Conteudo({ saude }: { saude: HealthView }) {
  const requisicoes = saude.requests ?? 0;
  const teto = saude.latencyCeilingMs ?? 0;

  if (saude.collectedThrough == null) {
    return (
      <p className="painel__aviso" role="status">
        Ainda não há medição nenhuma. Ela é gravada a cada poucos minutos — os zeros abaixo não
        querem dizer que ninguém chamou o app, querem dizer que ainda não há o que contar.
      </p>
    );
  }

  return (
    <>
      <div className="painel__numeros">
        <Numero
          titulo="Disponibilidade"
          valor={`${(saude.availabilityPercent ?? 100).toFixed(1)}%`}
          nota={requisicoes === 0 ? 'sem requisição na janela' : `${requisicoes} requisições`}
        />
        <Numero
          titulo="Erros 5xx"
          valor={String(saude.serverErrors ?? 0)}
          nota={`${saude.clientErrors ?? 0} respostas 4xx`}
        />
        <Numero
          titulo="p95"
          valor={rotuloDeLatencia(saude.p95Ms, teto)}
          nota="do período inteiro"
        />
        <Numero
          titulo="Subidas"
          valor={String(saude.startsInPeriod ?? 0)}
          nota="da aplicação, na janela"
        />
      </div>

      <SerieHoraria pontos={saude.hourly ?? []} />

      <h4 className="painel__coluna-titulo">Rotas que erraram</h4>
      {(saude.routes ?? []).length === 0 ? (
        <p className="empty">Nenhuma rota respondeu 5xx nesta janela.</p>
      ) : (
        <ul className="painel__lista saude__rotas">
          {(saude.routes ?? []).map((rota) => (
            <li key={rota.path} className="saude__rota">
              <code className="saude__caminho">{rota.path}</code>
              <span className="saude__erro">{(rota.errorPercent ?? 0).toFixed(1)}% de erro</span>
              <span className="painel__numero">
                {rota.serverErrors ?? 0}/{rota.requests ?? 0}
              </span>
            </li>
          ))}
        </ul>
      )}

      <h4 className="painel__coluna-titulo">Rotas mais lentas</h4>
      {(saude.slowest ?? []).length === 0 ? (
        <p className="empty">Nenhuma rota com requisições suficientes para medir latência.</p>
      ) : (
        <ul className="painel__lista saude__rotas">
          {(saude.slowest ?? []).map((rota) => (
            <li key={rota.path} className="saude__rota">
              <code className="saude__caminho">{rota.path}</code>
              <span className="saude__latencia">p95 {rotuloDeLatencia(rota.p95Ms, teto)}</span>
              <span className="painel__numero">{rota.avgMs ?? 0} ms</span>
            </li>
          ))}
        </ul>
      )}
      <p className="hint">
        O p95 é o teto da faixa em que ele cai, e não um número exato: o que se guarda é um
        histograma, porque percentil não se soma — o p95 de uma semana não sai da média dos p95 de
        cada hora. A média fica ao lado de propósito, para mostrar o que ela esconde.
      </p>

      <h4 className="painel__coluna-titulo">Últimas subidas</h4>
      {(saude.starts ?? []).length === 0 ? (
        <p className="empty">Nenhuma subida registrada ainda.</p>
      ) : (
        <ul className="painel__lista saude__subidas">
          {(saude.starts ?? []).map((subida) => (
            <li key={subida.startedAt} className="saude__subida">
              <span>{formatarInstante(subida.startedAt)}</span>
              <span className="saude__perfis">{subida.profiles}</span>
            </li>
          ))}
        </ul>
      )}
      <p className="hint">
        Não há registro de <em>parada</em>: o processo que morre sozinho não escreve nada, e uma
        lista de paradas só teria as saídas educadas. O que se lê é a distância entre duas subidas.
      </p>
    </>
  );
}

function Numero({ titulo, valor, nota }: { titulo: string; valor: string; nota: string }) {
  return (
    <div className="painel__numero-bloco">
      <p className="painel__numero-titulo">{titulo}</p>
      <p className="painel__numero-valor">{valor}</p>
      <p className="painel__numero-comparativo">{nota}</p>
    </div>
  );
}

/**
 * A série horária, em duas linhas: requisições e 5xx.
 *
 * Mesmo desenho da série do painel de uso — SVG à mão, `aria-hidden`, e a mesma informação escrita
 * embaixo. Uma polilinha não é legível por leitor de tela, e um `alt` descrevendo a forma do
 * desenho não é a informação, é a aparência dela.
 */
function SerieHoraria({ pontos }: { pontos: HealthPoint[] }) {
  if (pontos.length === 0) return null;

  const largura = 320;
  const altura = 90;
  const margem = 4;
  const util = altura - margem * 2;
  const teto = Math.max(...pontos.map((ponto) => ponto.requests ?? 0), 1);
  const passo = pontos.length === 1 ? 0 : (largura - margem * 2) / (pontos.length - 1);

  const caminho = (valor: (ponto: HealthPoint) => number) =>
    pontos
      .map((ponto, indice) => {
        const x = margem + passo * indice;
        const y = margem + util - (valor(ponto) / teto) * util;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');

  const pico = pontos.reduce((maior, ponto) =>
    (ponto.requests ?? 0) > (maior.requests ?? 0) ? ponto : maior,
  );

  return (
    <div className="painel__grafico">
      <svg
        viewBox={`0 0 ${largura} ${altura}`}
        className="painel__linha"
        role="presentation"
        aria-hidden="true"
      >
        <polyline className="painel__linha-visitas" points={caminho((p) => p.requests ?? 0)} />
        <polyline className="saude__linha-erros" points={caminho((p) => p.serverErrors ?? 0)} />
      </svg>
      <p className="painel__legenda">
        <span className="painel__chave painel__chave--visitas">Requisições</span>
        <span className="painel__chave saude__chave--erros">Erros 5xx</span>
      </p>
      <p className="hint">
        Pico de {pico.requests ?? 0} requisições em {formatarInstante(pico.hour)}. Hora sem barra é
        hora sem requisição medida — o que pode ser ninguém chamando, a descarga ainda não ter
        rodado, ou o app não ter estado de pé.
      </p>
    </div>
  );
}

/**
 * O p95 como frase.
 *
 * Três respostas diferentes, e confundi-las seria o pior tipo de erro num painel: `-1` é "não
 * medimos", `0` é "acima do último degrau da escada" — o caso mais lento de todos —, e qualquer
 * outro número é o teto da faixa. O teto vem do servidor justamente para não ser escrito aqui.
 */
function rotuloDeLatencia(p95?: number, teto?: number): string {
  if (p95 == null || p95 < 0) return 'sem medição';
  if (p95 === 0) return `> ${teto ?? 0} ms`;
  return `≤ ${p95} ms`;
}

/** `2026-08-27T10:00:00Z` vira `27/08 07:00` no fuso de quem lê. */
function formatarInstante(iso?: string): string {
  if (!iso) return '—';
  const quando = new Date(iso);
  if (Number.isNaN(quando.getTime())) return iso;
  const dois = (valor: number) => String(valor).padStart(2, '0');
  return `${dois(quando.getDate())}/${dois(quando.getMonth() + 1)} ${dois(quando.getHours())}:${dois(
    quando.getMinutes(),
  )}`;
}
