import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App.tsx';
// tokens.css (gerado de shared/tokens, D72) vem primeiro: as regras leem as variáveis dele.
import './tokens.css';
// O CSS é dividido por área (D73), e a ordem abaixo é a da cascata: um arquivo sobrescreve os
// anteriores quando a especificidade empata. Arquivo de área nova entra no fim.
import './styles/base.css';
import './styles/layout.css';
import './styles/home.css';
import './styles/arvore.css';
import './styles/no.css';
import './styles/revisao.css';
import './styles/auth.css';
import './styles/landing.css';
import './styles/feedback.css';
import './styles/admin.css';
import './styles/diario.css';

const container = document.getElementById('root');
if (!container) {
  throw new Error('Elemento #root não encontrado');
}

createRoot(container).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
);
