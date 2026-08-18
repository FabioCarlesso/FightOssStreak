import { useEffect } from 'react';
import { NavLink, Outlet, Route, Routes } from 'react-router-dom';
import { DemoModeBanner } from './components/DemoModeBanner.tsx';
import { DisclaimerGate } from './components/DisclaimerGate.tsx';
import { HomePage } from './pages/HomePage.tsx';
import { LandingPage } from './pages/LandingPage.tsx';
import { NodePage } from './pages/NodePage.tsx';
import { ProgressPage } from './pages/ProgressPage.tsx';
import { TreePage } from './pages/TreePage.tsx';
import { DemoModeProvider } from './state/DemoModeProvider.tsx';
import { markAppVisited } from './state/appVisit.ts';

/**
 * Duas superfícies, e a fronteira entre elas é o portão de aceite.
 *
 * `/` é a landing: pública, sem chamada de API, e a primeira coisa que quem recebe o link vê. Todo
 * o resto vive sob `AppLayout`, que é onde o `DisclaimerGate` continua sendo o primeiro a decidir —
 * inclusive a rota de "página não encontrada", que fica dentro do portão de propósito: URL
 * desconhecida não é motivo para abrir o app sem aceite.
 */
export function App() {
  return (
    <DemoModeProvider>
      <Routes>
        <Route path="/" element={<LandingPage />} />

        <Route element={<AppLayout />}>
          <Route path="/hoje" element={<HomePage />} />
          <Route path="/arvore" element={<TreePage />} />
          <Route path="/no/:code" element={<NodePage />} />
          <Route path="/progresso" element={<ProgressPage />} />
          <Route path="*" element={<p className="empty">Página não encontrada.</p>} />
        </Route>
      </Routes>
    </DemoModeProvider>
  );
}

/** O provider fica por fora do portão, mas o portão continua abrindo o app (D31 + docs/06). */
function AppLayout() {
  return (
    <DisclaimerGate>
      <AppChrome />
    </DisclaimerGate>
  );
}

function AppChrome() {
  // Marcado aqui dentro, e não no clique do botão da landing: o que interessa é ter entrado no app
  // de verdade — quem parou no aviso e desistiu não entrou, e quem chegou direto em `/arvore` por
  // link salvo entrou. É essa marca que faz `/` virar atalho para a agenda em vez de apresentação.
  useEffect(() => markAppVisited(), []);

  return (
    <div className="app">
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
          <a href="/?ver=apresentacao">Sobre o projeto</a>
        </p>
      </footer>
    </div>
  );
}
