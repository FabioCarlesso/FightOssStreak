import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/**
 * Lint do lado TypeScript do monorepo — `web/`, `shared/*` e os scripts de `scripts/`.
 *
 * O projeto é solo e a ruleset de `main` exige zero aprovações humanas (D18): o CI *é* a revisão.
 * Por isso o lint roda como passo do job `web`, antes dos testes, e não como job separado — job
 * novo só vira portão entrando na ruleset, e aí precisaria rodar sem filtro de path (D19).
 *
 * Formatação é assunto do Prettier, não daqui: `eslint-config-prettier` fecha a lista desligando
 * as regras de estilo do ESLint. Se ele deixar de ser o último item, as duas ferramentas voltam a
 * discordar em loop.
 */
export default tseslint.config(
  {
    // `shared/types/generated/` é GERADO a partir do OpenAPI (regra 2 do CLAUDE.md). Apontar
    // problema em arquivo que ninguém pode corrigir à mão só produz ruído.
    ignores: [
      '**/node_modules/**',
      '**/dist/**',
      'backend/target/**',
      'shared/types/generated/**',
      'web/node_modules/.vite/**',
    ],
  },

  js.configs.recommended,

  // Checagem com tipos em todo TypeScript do repo. `projectService` acha sozinho o tsconfig mais
  // próximo de cada arquivo — o que importa aqui porque `web/`, `shared/domain`, `shared/api-client`
  // e `shared/types` têm tsconfigs distintos, e um `project` fixo deixaria alguns de fora.
  {
    files: ['**/*.{ts,tsx}'],
    extends: [tseslint.configs.recommendedTypeChecked],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      // Prefixo `_` é a forma de dizer "existe por contrato, não é usado aqui".
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' },
      ],
      // `describe`/`it`/`test` do runner nativo devolvem promessa por construção, e o próprio
      // runner a aguarda. Sem esta exceção, todo teste de `shared/domain` acusaria promessa solta.
      '@typescript-eslint/no-floating-promises': [
        'error',
        {
          allowForKnownSafeCalls: [
            { from: 'package', package: 'node:test', name: ['describe', 'it', 'test'] },
          ],
        },
      ],
    },
  },

  // React só existe no `web/`. `shared/` é deliberadamente agnóstico de UI para ser reaproveitado
  // inteiro no React Native (docs/03-estrutura-projeto.md).
  {
    files: ['web/src/**/*.{ts,tsx}'],
    extends: [reactHooks.configs['recommended-latest']],
    languageOptions: {
      globals: globals.browser,
    },
    rules: {
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
    plugins: {
      'react-refresh': reactRefresh,
    },
  },

  // Scripts de manutenção: JavaScript puro rodando no Node, fora de qualquer tsconfig.
  {
    files: ['scripts/**/*.mjs', 'eslint.config.mjs', '**/*.config.{js,mjs}'],
    languageOptions: {
      globals: globals.node,
    },
  },

  // Vite e Vitest rodam no Node, não no navegador.
  {
    files: ['web/vite.config.ts', 'web/vitest.setup.ts'],
    languageOptions: {
      globals: globals.node,
    },
  },

  prettier,
);
