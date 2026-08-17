/**
 * Setup dos testes de UI.
 *
 * Vive dentro de `src/` de propósito: é o que faz a augmentação de tipos dos matchers do
 * jest-dom entrar no `tsconfig.app.json`, então `npm run typecheck` também cobre os testes.
 * Nada da aplicação o importa, então ele não entra no bundle.
 */
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// O jsdom é o mesmo entre arquivos de teste do mesmo worker: sem isto, a árvore renderizada por
// um teste continuaria no documento e as buscas do teste seguinte encontrariam dois resultados.
afterEach(() => {
  cleanup();
});
