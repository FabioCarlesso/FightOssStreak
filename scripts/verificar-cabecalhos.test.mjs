import { test } from 'node:test';
import assert from 'node:assert/strict';
import { primeiroBundle, problemas } from './verificar-cabecalhos.mjs';

const CERTOS = [
  ['content-security-policy', "default-src 'self'; frame-ancestors 'none'"],
  ['x-frame-options', 'DENY'],
  ['x-content-type-options', 'nosniff'],
  ['referrer-policy', 'strict-origin-when-cross-origin'],
  ['strict-transport-security', 'max-age=31536000; includeSubDomains'],
  ['server', 'nginx'],
];

test('resposta com o conjunto inteiro não tem problema', () => {
  assert.deepEqual(problemas('/', CERTOS), []);
});

test('location com add_header próprio: o que some do server é apontado', () => {
  const soCache = [['cache-control', 'no-cache']];
  const achados = problemas('/index.html', soCache);
  assert.equal(achados.length, 5);
  assert.ok(achados.includes('/index.html: falta content-security-policy'));
});

test('cabeçalho que o backend e o nginx mandam juntos é apontado', () => {
  // Como o fetch do Node entrega a duplicata, já juntada.
  const juntado = CERTOS.map(([n, v]) => (n === 'x-frame-options' ? [n, 'DENY, DENY'] : [n, v]));
  assert.deepEqual(problemas('/api/me', juntado), [
    '/api/me: x-frame-options inesperado: DENY, DENY',
  ]);
  // E como chegaria num cliente que preserva as linhas.
  const repetido = [...CERTOS, ['x-content-type-options', 'nosniff']];
  assert.deepEqual(problemas('/api/me', repetido), [
    '/api/me: x-content-type-options repetido 2 vezes',
  ]);
});

test('CSP sem frame-ancestors não passa', () => {
  const semFrame = CERTOS.map(([n, v]) =>
    n === 'content-security-policy' ? [n, "default-src 'self'"] : [n, v],
  );
  assert.equal(problemas('/', semFrame).length, 1);
});

test('versão do nginx no Server é apontada', () => {
  const comVersao = CERTOS.map(([n, v]) => (n === 'server' ? [n, 'nginx/1.27.3'] : [n, v]));
  assert.deepEqual(problemas('/', comVersao), ['/: Server expõe versão: nginx/1.27.3']);
});

test('Cache-Control é conferido só quando o caso pede', () => {
  const comCache = [...CERTOS, ['cache-control', 'no-cache']];
  assert.deepEqual(problemas('/', comCache, { cacheControl: 'no-cache' }), []);
  assert.deepEqual(
    problemas('/x.js', comCache, { cacheControl: 'public, max-age=31536000, immutable' }),
    ['/x.js: Cache-Control "no-cache", esperado "public, max-age=31536000, immutable"'],
  );
  assert.deepEqual(problemas('/', CERTOS, { cacheControl: 'no-cache' }), [
    '/: Cache-Control "", esperado "no-cache"',
  ]);
});

test('o bundle sai do index.html gerado pelo Vite', () => {
  const html = '<script type="module" crossorigin src="/assets/index-C4t6YdIN.js"></script>';
  assert.equal(primeiroBundle(html), '/assets/index-C4t6YdIN.js');
  assert.equal(primeiroBundle('<html></html>'), null);
});
