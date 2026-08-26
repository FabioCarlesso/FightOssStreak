import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api/client.ts';
import { SHORT_DISCLAIMER } from '../content/disclaimer.ts';
import {
  APP_PATH,
  LOGIN_PATH,
  DEMO,
  DISCLAIMER_DOC_URL,
  DOCS_URL,
  FEATURES,
  NAO_E,
  NUMEROS,
  PRINT_DESKTOP,
  PRINT_MOBILE,
  type Print,
  REPO_URL,
  PRINT_HOJE,
  STATUS,
  STEPS,
} from '../content/landing.ts';
import { hasVisitedApp } from '../state/appVisit.ts';
import { useStartDemo } from '../state/demoAccount.ts';
import { useAsync } from '../state/useAsync.ts';

/**
 * Apresentação pública do projeto — a única tela fora do portão de aceite.
 *
 * Duas coisas a página não pode fazer, e as duas são estruturais. **Não depende da API para
 * renderizar**: era o `DisclaimerGate` que decidia a raiz, e ele depende de `GET /api/disclaimer`
 * para aparecer, então com o backend frio a primeira tela de quem recebia o link era "não foi
 * possível falar com a API". E **não é caminho para dentro do app sem aceite**: o botão de entrada
 * leva a `/entrar`, e de lá para dentro passa pelo aviso.
 *
 * A regra que mudou com a #62 é a primeira, e mudou para melhor definida: a página *pergunta* se
 * este ambiente tem demonstração, mas nada nela espera a resposta. Backend frio, offline ou sem
 * conta-modelo dão o mesmo resultado — a página inteira, sem o botão de demonstração. O que a
 * regra proíbe é a tela depender da API para existir, não trocar uma palavra com ela.
 *
 * O aviso curto aparece aqui mesmo, e não só depois do clique, porque quem lê a página e não entra
 * também precisa saber o que o app não é (D1).
 */
export function LandingPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  // Sem `await` em nada que a página renderize: enquanto (ou se) isto não responde, a landing é
  // exatamente a de antes.
  const ambiente = useAsync(() => api.getAuthProviders(), []);
  const demo = useStartDemo((destino) => navigate(destino));
  const demoDisponivel = ambiente.data?.demoEnabled ?? false;

  // Quem já entrou no app uma vez tem `/` como atalho para a agenda do dia — a apresentação já
  // cumpriu o papel dela. `?ver` traz a página de volta, para o link continuar compartilhável e
  // para dar como revisar a própria landing sem limpar o navegador.
  if (!params.has('ver') && hasVisitedApp()) {
    return <Navigate to={APP_PATH} replace />;
  }

  return (
    <div className="landing">
      <header className="landing__hero">
        <div className="landing__hero-text">
          <p className="landing__brand">
            <span aria-hidden="true">🥋</span> FightOssStreak
          </p>
          <h1>
            Na segunda você aprendeu três técnicas.
            <br />
            Na sexta, lembra de uma.
          </h1>
          <p className="landing__lead">
            O FightOssStreak organiza o que você viu no tatame, cobra o conceito e diz o que drillar
            hoje. Quem ensina jiu-jitsu é o seu professor — isto aqui é a revisão.
          </p>
          <p className="landing__cta">
            {/* A demonstração vem primeiro quando existe: é o degrau que a pessoa consegue subir
                agora, sem criar conta nenhuma, e é decidindo aqui que ela vai saber se vale
                criar uma. */}
            {demoDisponivel && (
              <button
                type="button"
                className="landing__button"
                onClick={demo.start}
                disabled={demo.starting}
              >
                {demo.starting ? DEMO.ctaCarregando : DEMO.cta}
              </button>
            )}
            {/* A copy já disse "pedir acesso" (fila da D36) e "Criar conta" (D47). Agora diz as
                duas saídas porque a tela de destino tem as duas: mandar quem chega da apresentação
                direto ao formulário de senha escondia o Google de quem ainda não tem conta
                nenhuma. Prometer menos do que o produto entrega é o mesmo defeito que prometer
                mais. */}
            <Link
              className={`landing__button${demoDisponivel ? ' landing__button--ghost' : ''}`}
              to={LOGIN_PATH}
            >
              Entrar ou criar conta
            </Link>
            <a className="landing__button landing__button--ghost" href={REPO_URL}>
              Ver o código
            </a>
          </p>
          {demo.failure && <p className="landing__erro">{demo.failure}</p>}
          <p className="landing__note">
            {demoDisponivel && `${DEMO.nota} `}
            Cadastro aberto e sem cobrança: e-mail e senha, ou a conta do Google. O aviso de
            responsabilidade aparece antes da primeira tela.
          </p>
        </div>

        <div className="landing__phone">
          <img
            src={PRINT_HOJE.mobile}
            alt="Tela inicial do app em um celular, com o streak e a lista do que revisar hoje."
            width={PRINT_MOBILE.width}
            height={PRINT_MOBILE.height}
          />
        </div>
      </header>

      <section
        className="landing__section landing__section--split landing__problem"
        aria-labelledby="problema"
      >
        <h2 id="problema">Toda aula tem três técnicas novas. A retenção não acompanha.</h2>
        <div>
          <p>
            Você anota no caderno e não volta. Salva o vídeo e não assiste. Na vez seguinte em que a
            posição aparece no rolamento, o corpo não lembra do detalhe que o professor repetiu duas
            vezes.
          </p>
          <p className="landing__punch">
            O problema não é falta de conteúdo. É falta de revisão na hora certa.
          </p>
        </div>
      </section>

      <section className="landing__section" aria-labelledby="como-funciona">
        <h2 id="como-funciona">Como funciona</h2>
        <ol className="landing__steps">
          {STEPS.map((step, index) => (
            <li key={step.title} className="landing__step">
              <div className="landing__step-text">
                <p className="landing__step-number" aria-hidden="true">
                  {index + 1}
                </p>
                <h3>{step.title}</h3>
                <p>{step.text}</p>
              </div>
              <PrintFigure print={step.print} />
            </li>
          ))}
        </ol>
      </section>

      <section className="landing__section" aria-labelledby="o-que-faz">
        <h2 id="o-que-faz">O que ele faz</h2>
        <ul className="landing__grid">
          {FEATURES.map((feature) => (
            <li key={feature.title} className="landing__feature">
              <h3>{feature.title}</h3>
              <p>{feature.text}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="landing__section" aria-labelledby="o-que-nao-e">
        <h2 id="o-que-nao-e">O que ele não é</h2>
        <ul className="landing__grid landing__grid--plain">
          {NAO_E.map((item) => (
            <li key={item.title} className="landing__feature landing__feature--limit">
              <h3>{item.title}</h3>
              <p>{item.text}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="landing__section landing__section--split" aria-labelledby="estado">
        <h2 id="estado">Em que pé está</h2>
        <div>
          <p className="landing__lead landing__lead--small">
            Ferramenta de uso pessoal, em uso real e com o inacabado à mostra.
          </p>
          <ul className="landing__status">
            {STATUS.map((item) => (
              <li key={item.label} className={item.done ? 'is-done' : 'is-pending'}>
                <span className="landing__status-mark" aria-hidden="true">
                  {item.done ? '✓' : '○'}
                </span>
                <span>{item.label}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section
        className="landing__section landing__section--split landing__warning"
        aria-labelledby="aviso"
      >
        <h2 id="aviso">Antes de entrar</h2>
        <div>
          <p>⚠️ {SHORT_DISCLAIMER}</p>
          <p className="landing__note">
            O <a href={DISCLAIMER_DOC_URL}>aviso completo</a> é exibido com aceite obrigatório na
            primeira abertura do app.
          </p>
        </div>
      </section>

      <section className="landing__section landing__close">
        <h2>
          {NUMEROS.nodes} nós esperando. Comece pelo primeiro.
          <br />
          <span className="landing__lead landing__lead--small">
            O do tatame, não o da tela — este aqui é só para você lembrar dele na sexta.
          </span>
        </h2>
        <p className="landing__cta">
          <Link className="landing__button" to={LOGIN_PATH}>
            Entrar ou criar conta
          </Link>
        </p>
      </section>

      <footer className="landing__footer">
        <p>
          Projeto pessoal, código aberto sob licença MIT. Os vídeos são conteúdo de terceiros,
          incorporados do YouTube e creditados ao canal em cada nó.
        </p>
        <p>
          <a href={REPO_URL}>Repositório</a> · <a href={DOCS_URL}>Planejamento e decisões</a> ·{' '}
          <Link to={APP_PATH}>Entrar no app</Link>
        </p>
      </footer>
    </div>
  );
}

/**
 * Print da tela real, em dois tamanhos.
 *
 * O `<picture>` não é refinamento: um print de 1280px de largura exibido em um celular de 390px
 * vira borrão cinza, e a página inteira depende de a pessoa conseguir ver o app antes de entrar.
 * `width`/`height` fixam a proporção do desktop; a do celular vem por `aspect-ratio` no CSS, senão
 * a troca de origem no breakpoint empurraria o layout.
 */
function PrintFigure({ print }: { print: Print }) {
  return (
    <figure className="landing__print">
      <picture>
        <source media="(max-width: 640px)" srcSet={print.mobile} />
        <img
          src={print.desktop}
          alt={print.alt}
          width={PRINT_DESKTOP.width}
          height={PRINT_DESKTOP.height}
          loading="lazy"
          decoding="async"
        />
      </picture>
    </figure>
  );
}
