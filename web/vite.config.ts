/// <reference types="vitest/config" />
// A diretiva acima precisa vir antes de qualquer import: é ela que ensina o `tsc -b` do build a
// reconhecer a chave `test` deste arquivo. Sem ela o build quebra, e só o build — o Vitest lê a
// configuração em runtime e não reclamaria.
import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin } from 'vite';

export default defineConfig({
  plugins: [react(), metaAbsolutas()],
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

/**
 * Injeta as meta tags de compartilhamento que exigem URL absoluta.
 *
 * `og:image` e `og:url` só funcionam absolutas — e o domínio do app não pode morar no HTML, senão a
 * imagem passa a saber em que ambiente está (D22). Vem de `VITE_PUBLIC_URL` no build; sem a
 * variável, nenhuma tag é emitida. Um link sem imagem de prévia é bem melhor que um `<meta>`
 * apontando para um host errado.
 */
function metaAbsolutas(): Plugin {
  return {
    name: 'fos-meta-absolutas',
    transformIndexHtml(html) {
      const base = process.env.VITE_PUBLIC_URL?.trim().replace(/\/$/, '');
      if (!base) return html;

      const tags = [
        `<meta property="og:url" content="${base}/" />`,
        `<meta property="og:image" content="${base}/prints/og.jpg" />`,
        '<meta property="og:image:width" content="1200" />',
        '<meta property="og:image:height" content="630" />',
        `<meta name="twitter:image" content="${base}/prints/og.jpg" />`,
      ];
      return html.replace('</head>', `    ${tags.join('\n    ')}\n  </head>`);
    },
  };
}
