import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import type { PanelDays } from '@fos/api-client';
import type { PanelAccessPoint, PanelFunnelStep, PanelSlice, PanelView } from '@fos/types';
import { api } from '../api/client.ts';
import { useAccount } from '../state/account.ts';
import { useAsync } from '../state/useAsync.ts';

/**
 * O painel do administrador (#85, D50).
 *
 * É a primeira tela do app cujo assunto não é jiu-jitsu, e por isso a que mais precisa de disciplina
 * de escopo. Ela responde três perguntas — **quantos** chegaram, **de onde** vieram, **onde
 * desistiram** — e nenhuma sobre alguém em particular. Não há aqui lista de pessoas, sessão
 * individual nem "últimos acessos de fulano": o backend não devolve isso, e o dia em que devolver
 * será porque a D50 foi reaberta e `docs/11-privacidade.md` reescrito, não porque esta tela pediu.
 *
 * Os gráficos são SVG escrito à mão. O web tem três dependências de runtime, e um painel de uso
 * pessoal com uma linha e umas barras não justifica a quarta — nem o peso que uma biblioteca de
 * charts coloca no bundle de todo mundo que abre a landing.
 *
 * Três estados vazios, e eles são diferentes: **nunca agregou** (o job não rodou, e o número zero
 * não quer dizer nada), **período sem acesso** (o número zero quer dizer exatamente isso), e
 * **dimensão sem linha** (aquele recorte não existe nos dias já fechados). Uma tela que mostrasse
 * gráfico vazio nos três casos faria o administrador procurar defeito onde não há.
 */
export function AdminPanelPage() {
  const { account } = useAccount();

  // Mesmo desenho da tela de Usuários (#91): o backend recusa de qualquer jeito, e o
  // redirecionamento existe para não oferecer uma tela que só saberia dizer 403.
  if (account.role !== 'ADMIN') {
    return <Navigate to="/hoje" replace />;
  }
  return <Painel />;
}

/** Os três recortes. Período livre está fora de escopo — e o backend recusa qualquer outro. */
const PERIODOS: readonly { dias: PanelDays; rotulo: string }[] = [
  { dias: 7, rotulo: '7 dias' },
  { dias: 30, rotulo: '30 dias' },
  { dias: 90, rotulo: '90 dias' },
];

function Painel() {
  const [dias, setDias] = useState<PanelDays>(7);
  const painel = useAsync(() => api.getAdminPanel(dias), [dias]);

  const dados = painel.data;
  const carregando = painel.loading && !dados;

  return (
    <div className="stack">
      <section className="card">
        <header className="card__header">
          <h2>Painel</h2>
          <div className="painel__periodos" role="group" aria-label="Período">
            {PERIODOS.map((periodo) => (
              <button
                key={periodo.dias}
                type="button"
                className={
                  periodo.dias === dias
                    ? 'painel__periodo painel__periodo--ativo'
                    : 'painel__periodo'
                }
                aria-pressed={periodo.dias === dias}
                onClick={() => setDias(periodo.dias)}
              >
                {periodo.rotulo}
              </button>
            ))}
          </div>
        </header>
        <p className="hint">
          Quantas pessoas chegam, de onde vêm e onde param. Tudo agregado: nenhum número desta tela
          é de alguém em particular.
        </p>
        {dados && <Janela painel={dados} />}
      </section>

      {carregando && <p className="empty">Carregando o painel…</p>}

      {painel.error && (
        <section className="card">
          <div className="admin-users__falha" role="alert">
            <p className="error">Não foi possível carregar o painel: {painel.error.message}</p>
            <button type="button" onClick={painel.reload}>
              Tentar de novo
            </button>
          </div>
        </section>
      )}

      {dados && <Conteudo painel={dados} />}
    </div>
  );
}

/**
 * A janela medida, e o que ainda não entrou nela.
 *
 * O período termina **ontem** de propósito: hoje ainda recebe evento, e um número publicado que
 * muda depois de lido é pior que um número que falta. Quando a agregação está atrasada, quem lê
 * precisa saber disso antes de tirar conclusão de uma queda que é só o job não ter rodado.
 */
function Janela({ painel }: { painel: PanelView }) {
  const ultimoAgregado = painel.aggregatedThrough ?? null;
  const atrasado = ultimoAgregado != null && painel.to != null && ultimoAgregado < painel.to;

  return (
    <>
      <p className="hint">
        De {formatarData(painel.from)} a {formatarData(painel.to)}. O comparativo é{' '}
        {formatarData(painel.previousFrom)} a {formatarData(painel.previousTo)}. Hoje fica de fora:
        o dia só entra na contagem depois de fechado.
      </p>
      {ultimoAgregado == null && (
        <p className="painel__aviso" role="status">
          A agregação ainda não fechou nenhum dia. Os zeros abaixo não querem dizer que ninguém
          apareceu — querem dizer que ainda não há o que contar.
        </p>
      )}
      {atrasado && (
        <p className="painel__aviso" role="status">
          O último dia com contagem é {formatarData(ultimoAgregado)}. Os dias posteriores aparecem
          zerados até a agregação rodar.
        </p>
      )}
    </>
  );
}

function Conteudo({ painel }: { painel: PanelView }) {
  const serie = painel.access?.series ?? [];
  const visitas = painel.access?.visits ?? 0;
  const visitantes = painel.access?.visitors ?? 0;
  const semAcesso = visitas === 0 && visitantes === 0;

  return (
    <>
      <section className="card">
        <header className="card__header">
          <h3>Acessos</h3>
        </header>
        {semAcesso ? (
          <p className="empty">Nenhum acesso neste período.</p>
        ) : (
          <>
            <div className="painel__numeros">
              <Numero
                titulo="Acessos"
                valor={visitas}
                anterior={painel.access?.previousVisits ?? 0}
              />
              <Numero
                titulo="Visitantes"
                valor={visitantes}
                anterior={painel.access?.previousVisitors ?? 0}
              />
            </div>
            <SerieDiaria pontos={serie} />
            <p className="hint">
              Visitante é contado por dia. A chave de visita muda de um dia para o outro (D50),
              então a soma do período é a soma dos dias — não são pessoas distintas no mês.
            </p>
          </>
        )}
      </section>

      <Funil degraus={painel.funnel ?? []} />

      <Ranking
        titulo="Origem"
        fatias={painel.origins ?? []}
        vazio="Nenhuma origem registrada neste período."
        rodape="“direto” é quem chegou sem referrer e sem campanha — categoria, não falha."
      />

      <section className="card">
        <header className="card__header">
          <h3>Perfil</h3>
        </header>
        <div className="painel__perfil">
          <Coluna titulo="Dispositivo" fatias={painel.profile?.devices ?? []} />
          <Coluna titulo="Navegador" fatias={painel.profile?.browsers ?? []} />
          <Coluna titulo="Idioma" fatias={painel.profile?.languages ?? []} />
          <Coluna titulo="País" fatias={painel.profile?.countries ?? []} />
        </div>
        {painel.geoIpCredit ? <p className="hint">{painel.geoIpCredit}</p> : null}
      </section>

      <Ranking
        titulo="Conteúdo"
        fatias={painel.content ?? []}
        vazio="Nenhuma tela aberta neste período."
        rodape={
          'As telas de nó aparecem juntas em /no/{codigo}: o código do nó não é gravado, porque' +
          ' segmento variável não entra na coleta (D50).'
        }
      />

      <section className="card">
        <header className="card__header">
          <h3>Contas</h3>
        </header>
        <div className="painel__numeros">
          <Numero titulo="Total" valor={painel.accounts?.total ?? 0} />
          <Numero titulo="Criadas no período" valor={painel.accounts?.createdInPeriod ?? 0} />
          <Numero titulo="Ativas no período" valor={painel.accounts?.activeInPeriod ?? 0} />
        </div>
        <p className="hint">
          Ativa é a conta que registrou drill no período — usar o app, e não só abri-lo. Quem são
          elas é outra tela: esta responde quantas.
        </p>
      </section>
    </>
  );
}

/**
 * Um número, com o comparativo do período anterior quando ele existe.
 *
 * Sem base anterior não há variação a mostrar — e "0%" ali afirmaria uma estabilidade que ninguém
 * mediu. É o mesmo cuidado do percentual do funil.
 */
function Numero({ titulo, valor, anterior }: { titulo: string; valor: number; anterior?: number }) {
  const variacao =
    anterior == null || anterior === 0 ? null : Math.round(((valor - anterior) / anterior) * 100);

  return (
    <div className="painel__numero-bloco">
      <p className="painel__numero-titulo">{titulo}</p>
      <p className="painel__numero-valor">{valor}</p>
      {anterior != null && (
        <p className="painel__numero-comparativo">
          {variacao == null ? (
            <span className="painel__variacao">sem base anterior</span>
          ) : (
            <span
              className={
                variacao >= 0
                  ? 'painel__variacao painel__variacao--sobe'
                  : 'painel__variacao painel__variacao--desce'
              }
            >
              {variacao >= 0 ? '+' : '−'}
              {Math.abs(variacao)}%
            </span>
          )}{' '}
          vs. {anterior} antes
        </p>
      )}
    </div>
  );
}

/** Altura e largura do desenho. Só o sistema de coordenadas: a largura na tela é 100% do card. */
const GRAFICO = { largura: 320, altura: 90, margem: 4 };

/**
 * A série diária, em duas linhas.
 *
 * `preserveAspectRatio` no default (meet) e não `none`: esticar o viewBox para a largura do card
 * deformaria a espessura do traço, que fica grosso no eixo esticado e fino no outro.
 *
 * O gráfico é `aria-hidden` e vem acompanhado da leitura em texto. Uma polilinha não tem como ser
 * lida por leitor de tela, e a alternativa honesta é a mesma informação escrita — não um `alt` que
 * descreva a forma do desenho.
 */
function SerieDiaria({ pontos }: { pontos: PanelAccessPoint[] }) {
  if (pontos.length === 0) return null;

  const teto = Math.max(...pontos.map((ponto) => ponto.visits ?? 0), 1);
  const { largura, altura, margem } = GRAFICO;
  const util = altura - margem * 2;
  const passo = pontos.length === 1 ? 0 : (largura - margem * 2) / (pontos.length - 1);

  const caminho = (valor: (ponto: PanelAccessPoint) => number) =>
    pontos
      .map((ponto, indice) => {
        const x = margem + passo * indice;
        const y = margem + util - (valor(ponto) / teto) * util;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');

  const pico = pontos.reduce((maior, ponto) =>
    (ponto.visits ?? 0) > (maior.visits ?? 0) ? ponto : maior,
  );

  return (
    <div className="painel__grafico">
      <svg
        viewBox={`0 0 ${largura} ${altura}`}
        className="painel__linha"
        role="presentation"
        aria-hidden="true"
      >
        <polyline className="painel__linha-visitas" points={caminho((p) => p.visits ?? 0)} />
        <polyline className="painel__linha-visitantes" points={caminho((p) => p.visitors ?? 0)} />
      </svg>
      <p className="painel__legenda">
        <span className="painel__chave painel__chave--visitas">Acessos</span>
        <span className="painel__chave painel__chave--visitantes">Visitantes</span>
      </p>
      <p className="hint">
        Pico de {pico.visits ?? 0} acessos em {formatarData(pico.day)}.
      </p>
    </div>
  );
}

/**
 * Os seis degraus, sempre os seis.
 *
 * Degrau zerado fica na tela mostrando zero: some, e a tela esconde exatamente o lugar onde as
 * pessoas desistem — que é a única coisa que um funil existe para mostrar.
 */
function Funil({ degraus }: { degraus: PanelFunnelStep[] }) {
  const topo = Math.max(...degraus.map((degrau) => degrau.total ?? 0), 1);

  return (
    <section className="card">
      <header className="card__header">
        <h3>Funil</h3>
      </header>
      <ol className="painel__funil">
        {degraus.map((degrau) => (
          <li key={degrau.step} className="painel__degrau">
            <span className="painel__rotulo">{degrau.label}</span>
            <Barra proporcao={(degrau.total ?? 0) / topo} />
            <span className="painel__numero">{degrau.total ?? 0}</span>
            <span className="painel__conversao">
              {degrau.percentOfPrevious == null ? '—' : `${degrau.percentOfPrevious}%`}
            </span>
          </li>
        ))}
      </ol>
      <p className="hint">
        O primeiro degrau conta visitantes; os outros contam ocorrências. A porcentagem é a
        conversão do degrau imediatamente acima — “—” quando o degrau acima é zero e não há o que
        converter.
      </p>
    </section>
  );
}

function Ranking({
  titulo,
  fatias,
  vazio,
  rodape,
}: {
  titulo: string;
  fatias: PanelSlice[];
  vazio: string;
  rodape?: string;
}) {
  return (
    <section className="card">
      <header className="card__header">
        <h3>{titulo}</h3>
      </header>
      {fatias.length === 0 ? <p className="empty">{vazio}</p> : <Lista fatias={fatias} />}
      {rodape && fatias.length > 0 && <p className="hint">{rodape}</p>}
    </section>
  );
}

function Coluna({ titulo, fatias }: { titulo: string; fatias: PanelSlice[] }) {
  return (
    <div className="painel__coluna">
      <h4 className="painel__coluna-titulo">{titulo}</h4>
      {fatias.length === 0 ? <p className="empty">Sem dado ainda.</p> : <Lista fatias={fatias} />}
    </div>
  );
}

function Lista({ fatias }: { fatias: PanelSlice[] }) {
  const topo = Math.max(...fatias.map((fatia) => fatia.total ?? 0), 1);

  return (
    <ul className="painel__lista">
      {fatias.map((fatia) => (
        <li key={fatia.value} className="painel__item">
          <span className="painel__rotulo">{fatia.value}</span>
          <Barra proporcao={(fatia.total ?? 0) / topo} />
          <span className="painel__numero">{fatia.total ?? 0}</span>
        </li>
      ))}
    </ul>
  );
}

/**
 * A barra de uma linha.
 *
 * `preserveAspectRatio="none"` aqui, ao contrário da linha: é um retângulo sólido, e esticá-lo é o
 * comportamento desejado. `aria-hidden` porque o número está escrito ao lado — a barra é a mesma
 * informação em forma de comprimento, e anunciá-la de novo só repetiria.
 */
function Barra({ proporcao }: { proporcao: number }) {
  const largura = Math.max(0, Math.min(1, proporcao)) * 100;

  return (
    <svg
      className="painel__barra"
      viewBox="0 0 100 10"
      preserveAspectRatio="none"
      role="presentation"
      aria-hidden="true"
    >
      <rect x="0" y="0" width={largura} height="10" rx="2" />
    </svg>
  );
}

/** `2026-08-26` vira `26/08`. Sem `Date`: a string ISO já é o dia, e converter arrisca fuso. */
function formatarData(iso?: string): string {
  if (!iso) return '—';
  const [, mes, dia] = iso.split('-');
  return mes && dia ? `${dia}/${mes}` : iso;
}
