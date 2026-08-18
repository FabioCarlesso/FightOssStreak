import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { SHORT_DISCLAIMER } from '../content/disclaimer.ts';
import {
  APP_PATH,
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

/**
 * Apresentação pública do projeto — a única tela fora do portão de aceite.
 *
 * Duas coisas a página não pode fazer, e as duas são estruturais. **Não chama a API**: era o
 * `DisclaimerGate` que decidia a raiz, e ele depende de `GET /api/disclaimer` para renderizar, então
 * com o backend frio a primeira tela de quem recebia o link era "não foi possível falar com a API".
 * E **não é caminho para dentro do app**: o botão leva a `/hoje`, que continua atrás do aceite.
 *
 * O aviso curto aparece aqui mesmo, e não só depois do clique, porque quem lê a página e não entra
 * também precisa saber o que o app não é (D1).
 */
export function LandingPage() {
  const [params] = useSearchParams();

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
            <Link className="landing__button" to={APP_PATH}>
              Abrir o app
            </Link>
            <a className="landing__button landing__button--ghost" href={REPO_URL}>
              Ver o código
            </a>
          </p>
          <p className="landing__note">
            Sem cadastro e sem cobrança. O aviso de responsabilidade aparece antes da primeira tela.
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

      <section className="landing__section landing__problem" aria-labelledby="problema">
        <h2 id="problema">Toda aula tem três técnicas novas. A retenção não acompanha.</h2>
        <p>
          Você anota no caderno e não volta. Salva o vídeo e não assiste. Na vez seguinte em que a
          posição aparece no rolamento, o corpo não lembra do detalhe que o professor repetiu duas
          vezes.
        </p>
        <p className="landing__punch">
          O problema não é falta de conteúdo. É falta de revisão na hora certa.
        </p>
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

      <section className="landing__section" aria-labelledby="estado">
        <h2 id="estado">Em que pé está</h2>
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
      </section>

      <section className="landing__section landing__warning" aria-labelledby="aviso">
        <h2 id="aviso">Antes de entrar</h2>
        <p>⚠️ {SHORT_DISCLAIMER}</p>
        <p className="landing__note">
          O <a href={DISCLAIMER_DOC_URL}>aviso completo</a> é exibido com aceite obrigatório na
          primeira abertura do app.
        </p>
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
          <Link className="landing__button" to={APP_PATH}>
            Abrir o app
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
          <Link to={APP_PATH}>Abrir o app</Link>
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
