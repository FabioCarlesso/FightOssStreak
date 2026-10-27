#!/usr/bin/env node
/**
 * Gera web/src/tokens.css — o `:root` do web — a partir de `shared/tokens` (D72, #183).
 *
 * O pacote é a fonte única de cor, tipografia, espaçamento e raio: o app importa direto, e o web
 * recebe este CSS. O arquivo gerado é versionado (o Vite não precisa rodar nada para servir) e
 * `gen-tokens.test.mjs` reprova o CI se ele não bater com o pacote — editar o CSS à mão é
 * desfeito pelo próximo `npm run gen:tokens`.
 *
 * O pacote é TypeScript e é importado sem compilar: o Node 22 (o do CI) apaga os tipos sozinho.
 * A saída já sai no formato do Prettier, para `lint` e `gen:tokens` não se desfazerem em loop.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { variaveisCss } from '../shared/tokens/src/index.ts';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
export const ARQUIVO_GERADO = resolve(root, 'web/src/tokens.css');

/** O conteúdo inteiro de `web/src/tokens.css`. */
export function gerarCss() {
  const linhas = variaveisCss().map(([nome, valor]) => `  ${nome}: ${valor};`);
  return [
    '/*',
    ' * GERADO por scripts/gen-tokens.mjs a partir de shared/tokens (D72) — não edite à mão.',
    ' * Para mudar um token, edite shared/tokens/src/index.ts e rode `npm run gen:tokens`.',
    ' */',
    ':root {',
    '  color-scheme: dark;',
    '',
    ...linhas,
    '}',
    '',
  ].join('\n');
}

export function estaAtualizado() {
  try {
    return readFileSync(ARQUIVO_GERADO, 'utf8') === gerarCss();
  } catch {
    return false;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  writeFileSync(ARQUIVO_GERADO, gerarCss());
  console.log(`Tokens escritos em ${ARQUIVO_GERADO}`);
}
