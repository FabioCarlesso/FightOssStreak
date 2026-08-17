import { NavLink, Route, Routes } from 'react-router-dom';
import { DemoModeBanner } from './components/DemoModeBanner.tsx';
import { DisclaimerGate } from './components/DisclaimerGate.tsx';
import { HomePage } from './pages/HomePage.tsx';
import { NodePage } from './pages/NodePage.tsx';
import { ProgressPage } from './pages/ProgressPage.tsx';
import { TreePage } from './pages/TreePage.tsx';
import { DemoModeProvider } from './state/DemoModeProvider.tsx';

export function App() {
  return (
    // O provider fica por fora do portão, mas o portão continua sendo o primeiro a decidir: o modo
    // demonstração não abre o app sem o aceite do aviso de responsabilidade (docs/06).
    <DemoModeProvider>
      <DisclaimerGate>
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
              <NavLink to="/" end>
                Hoje
              </NavLink>
              <NavLink to="/arvore">Árvore</NavLink>
              <NavLink to="/progresso">Progresso</NavLink>
            </nav>
          </header>

          <main className="app__main">
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/arvore" element={<TreePage />} />
              <Route path="/no/:code" element={<NodePage />} />
              <Route path="/progresso" element={<ProgressPage />} />
              <Route path="*" element={<p className="empty">Página não encontrada.</p>} />
            </Routes>
          </main>

          <footer className="app__footer">
            <p>
              O FightOssStreak <strong>não ensina jiu-jitsu</strong> e não substitui instrução
              presencial com professor qualificado. Pratique somente em academia, com supervisão.
            </p>
          </footer>
        </div>
      </DisclaimerGate>
    </DemoModeProvider>
  );
}
