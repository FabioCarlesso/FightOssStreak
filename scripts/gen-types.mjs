#!/usr/bin/env node
/**
 * Gera shared/types/generated/api.ts a partir do spec OpenAPI do backend.
 *
 * Fonte do spec, em ordem de preferência:
 *   1. backend rodando  ->  http://localhost:8080/v3/api-docs
 *   2. spec versionado  ->  backend/openapi.json
 *
 * Por que existe (ver docs/01-stack-tecnica.md): sem geração automática,
 * shared/types diverge do backend em duas semanas. O CI roda este script e
 * falha se o resultado ficar diferente do que está commitado.
 */
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const specUrl = process.env.FOS_OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs';
const specFile = resolve(root, 'backend/openapi.json');
const outFile = resolve(root, 'shared/types/generated/api.ts');

async function resolveSpec() {
  try {
    const res = await fetch(specUrl, { signal: AbortSignal.timeout(3000) });
    if (res.ok) {
      const spec = await res.text();
      writeFileSync(specFile, `${JSON.stringify(JSON.parse(spec), null, 2)}\n`);
      return { source: specUrl, path: specFile };
    }
  } catch {
    // backend fora do ar — cai para o spec versionado
  }
  if (existsSync(specFile)) return { source: specFile, path: specFile };
  throw new Error(
    `Nenhum spec disponível. Suba o backend (npm run dev:backend) ou versione ${specFile}.`,
  );
}

const { source, path } = await resolveSpec();
console.log(`[gen:types] spec: ${source}`);

mkdirSync(dirname(outFile), { recursive: true });
const generated = execFileSync(
  process.execPath,
  [resolve(root, 'node_modules/openapi-typescript/bin/cli.js'), path],
  { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 },
);

const header = [
  '/* eslint-disable */',
  '/**',
  ' * GERADO AUTOMATICAMENTE — não editar à mão.',
  ' * Origem: OpenAPI do backend. Regerar com `npm run gen:types`.',
  ' */',
  '',
].join('\n');

const next = header + generated;
const previous = existsSync(outFile) ? readFileSync(outFile, 'utf8') : null;
writeFileSync(outFile, next);

if (process.env.CI === 'true' && previous !== null && previous !== next) {
  console.error(
    '[gen:types] shared/types está desatualizado — rode `npm run gen:types` e commite.',
  );
  process.exit(1);
}
console.log(`[gen:types] escrito em ${outFile}`);
