/// <reference types="vitest/config" />
// A diretiva acima precisa vir antes de qualquer import: é ela que ensina o `tsc -b` do build a
// reconhecer a chave `test` deste arquivo. Sem ela o build quebra, e só o build — o Vitest lê a
// configuração em runtime e não reclamaria.
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Front e API sob a mesma origem em dev: evita CORS e faz o cliente usar caminhos
      // relativos, iguais aos de produção.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    // jsdom porque o que se testa aqui é comportamento de componente — o que a tela mostra e
    // o que o clique dispara. Navegador de verdade (Playwright) é desproporcional neste estágio.
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    // Sem `globals: true`: os testes de shared/domain importam `describe`/`it` explicitamente
    // de `node:test`, e manter os dois lados no mesmo hábito evita que um arquivo passe a
    // depender de configuração invisível.
    globals: false,
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
