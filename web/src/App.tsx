import { useEffect } from 'react';
import { Link, NavLink, Outlet, Route, Routes } from 'react-router-dom';
import { AuthGate } from './components/AuthGate.tsx';
import { DemoAccountBanner } from './components/DemoAccountBanner.tsx';
import { DemoModeBanner } from './components/DemoModeBanner.tsx';
import { DisclaimerGate } from './components/DisclaimerGate.tsx';
import { SignOutButton } from './components/SignOutButton.tsx';
import { AccountPage } from './pages/AccountPage.tsx';
import { FeedbackPage } from './pages/FeedbackPage.tsx';
import { HomePage } from './pages/HomePage.tsx';
import { LandingPage } from './pages/LandingPage.tsx';
import { NodePage } from './pages/NodePage.tsx';
import { ProgressPage } from './pages/ProgressPage.tsx';
import { TreePage } from './pages/TreePage.tsx';
import { ConfirmEmailPage } from './pages/auth/ConfirmEmailPage.tsx';
import { ForgotPasswordPage } from './pages/auth/ForgotPasswordPage.tsx';
import { ResetPasswordPage } from './pages/auth/ResetPasswordPage.tsx';
import { SignInPage } from './pages/auth/SignInPage.tsx';
import { SignUpPage } from './pages/auth/SignUpPage.tsx';
import { DemoModeProvider } from './state/DemoModeProvider.tsx';
import { useAccount } from './state/account.ts';
import { markAppVisited } from './state/appVisit.ts';

/**
 * Duas superfícies, e a fronteira entre elas são os portões.
 *
 * `/` é a landing: pública, sem chamada de API, e a primeira coisa que quem recebe o link vê. As
 * telas de entrada e cadastro são públicas pelo mesmo motivo, e **precisam ser rotas de verdade**
 * desde a #82: o link de confirmação que chega por e-mail volta para uma URL do app, e URL só
 * existe se houver rota. Todo o resto vive sob `AppLayout` — inclusive a rota de "página não
 * encontrada", que fica dentro dos portões de propósito: URL desconhecida não é motivo para abrir
 * o app.
 *
 * A ordem dos portões é `AuthGate` → `DisclaimerGate` (#24): entrar e só então aceitar o aviso. O
 * aceite é por conta (`disclaimer_acceptance.user_id`), então não faz sentido pedi-lo a quem ainda
 * não entrou.
 */
export function App() {
  return (
    <DemoModeProvider>
      <Routes>
        <Route path="/" element={<LandingPage />} />

        {/* Fronteira: públicas, fora dos portões. `AuthGate` manda para cá quem não tem sessão. */}
        <Route path="/entrar" element={<SignInPage />} />
        <Route path="/cadastrar" element={<SignUpPage />} />
        <Route path="/confirmar-email" element={<ConfirmEmailPage />} />
        <Route path="/senha/esquecida" element={<ForgotPasswordPage />} />
        <Route path="/senha/redefinir/:token" element={<ResetPasswordPage />} />

        <Route element={<AppLayout />}>
          <Route path="/hoje" element={<HomePage />} />
          <Route path="/arvore" element={<TreePage />} />
          <Route path="/no/:code" element={<NodePage />} />
          <Route path="/progresso" element={<ProgressPage />} />
          <Route path="/conta" element={<AccountPage />} />
          <Route path="/feedback" element={<FeedbackPage />} />
          <Route path="*" element={<p className="empty">Página não encontrada.</p>} />
        </Route>
      </Routes>
    </DemoModeProvider>
  );
}

/** O provider fica por fora dos portões, mas são eles que abrem o app (D31 + docs/06 + #24). */
function AppLayout() {
  return (
    <AuthGate>
      <DisclaimerGate>
        <AppChrome />
      </DisclaimerGate>
    </AuthGate>
  );
}

function AppChrome() {
  const { account, reload } = useAccount();
  const demo = account.demoExpiresAt != null;

  // Marcado aqui dentro, e não no clique do botão da landing: o que interessa é ter entrado no app
  // de verdade — quem parou no aviso e desistiu não entrou, e quem chegou direto em `/arvore` por
  // link salvo entrou. É essa marca que faz `/` virar atalho para a agenda em vez de apresentação.
  //
  // Demonstração não marca (#62). Quem só experimentou ainda **não tem conta**, e é justamente a
  // apresentação que existe para convencê-lo a pedir uma: esconder a landing da raiz de quem está
  // decidindo inverte a intenção da página.
  useEffect(() => {
    if (!demo) markAppVisited();
  }, [demo]);

  return (
    <div className="app">
      {/* Duas faixas, dois avisos opostos, e a ordem diz qual manda: a da conta de demonstração
          (#62) fala sobre QUEM você é aqui, e a do modo de inspeção (D31) sobre o que a árvore
          está mostrando. Ver as duas ao mesmo tempo é possível e não é contradição. */}
      <DemoAccountBanner />
      <DemoModeBanner />

      <header className="app__header">
        <div className="app__brand">
          <span className="app__logo" aria-hidden="true">
            🥋
          </span>
          <div>
            <h1>FightOssStreak</h1>
            <p className="app__tagline">Revisar o que você viu no tatame</p>
          </div>
        </div>
        <nav className="app__nav">
          <NavLink to="/hoje">Hoje</NavLink>
          <NavLink to="/arvore">Árvore</NavLink>
          <NavLink to="/progresso">Progresso</NavLink>
        </nav>
        <div className="app__account">
          <Link to="/conta" className="app__account-name">
            {account.displayName}
          </Link>
          <SignOutButton onSignedOut={reload} />
        </div>
      </header>

      <main className="app__main">
        <Outlet />
      </main>

      <footer className="app__footer">
        <p>
          O FightOssStreak <strong>não ensina jiu-jitsu</strong> e não substitui instrução
          presencial com professor qualificado. Pratique somente em academia, com supervisão.
        </p>
        <p>
          {/* `Link`, e não `<a href>`: âncora comum descarta o app carregado e recarrega tudo só
              para mostrar a apresentação. O `?ver` é o que impede a landing de devolver quem já
              entrou direto para a agenda. */}
          <Link to="/?ver=apresentacao">Sobre o projeto</Link> ·{' '}
          <Link to="/feedback">Feedback</Link>
        </p>
      </footer>
    </div>
  );
}
