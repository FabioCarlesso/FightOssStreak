#!/usr/bin/env node
/**
 * Confere os cabeçalhos de segurança que o nginx do `web` emite (#75, FOS-05).
 *
 *   node scripts/verificar-cabecalhos.mjs http://localhost:8081          # stack do Compose
 *   node scripts/verificar-cabecalhos.mjs http://localhost:8081 --api    # também /api/, com backend
 *
 * Por que existe: o `nginx.conf.template` só é exercitado com o container de pé, e a regra que mais
 * fácil quebra ali é silenciosa — um `add_header` acrescentado a um `location` apaga, só naquele
 * caminho, todos os cabeçalhos do `server`. Nada falha: a página abre, o teste passa, e o HTML sai
 * sem CSP. O job `web` roda este script contra a imagem recém-construída.
 *
 * Sem `--api` o backend não precisa existir: o CI sobe só o container do `web`. Com `--api` confere
 * também que cada cabeçalho aparece uma vez só em `/api/` — o Spring Security manda os mesmos, e é
 * o `proxy_hide_header` que impede a duplicata.
 *
 * Códigos de saída:
 *   0  todos os caminhos conferem
 *   1  algum cabeçalho faltou, sobrou ou veio com valor errado
 *   2  não deu para falar com o servidor
 */
import { fileURLToPath } from 'node:url';

/** O que todo caminho servido pelo `server` do app precisa trazer. */
export const OBRIGATORIOS = {
  'content-security-policy': (v) =>
    v.includes("frame-ancestors 'none'") && v.includes("default-src 'self'"),
  'x-frame-options': (v) => v === 'DENY',
  'x-content-type-options': (v) => v === 'nosniff',
  'referrer-policy': (v) => v === 'strict-origin-when-cross-origin',
  'strict-transport-security': (v) => v.startsWith('max-age='),
};

/**
 * Os problemas de uma resposta, em texto. Lista vazia é resposta certa.
 *
 * `headers` é a lista de pares `[nome, valor]`. O `fetch` do Node junta cabeçalho repetido num
 * valor só ("DENY, DENY"), e a duplicata que se procura em `/api/` aparece então como valor
 * inesperado — por isso a comparação é exata, e não por `includes`.
 */
export function problemas(caminho, headers, { cacheControl } = {}) {
  const achados = [];
  const valores = (nome) => headers.filter(([n]) => n.toLowerCase() === nome).map(([, v]) => v);

  for (const [nome, confere] of Object.entries(OBRIGATORIOS)) {
    const vistos = valores(nome);
    if (vistos.length === 0) achados.push(`${caminho}: falta ${nome}`);
    else if (vistos.length > 1) achados.push(`${caminho}: ${nome} repetido ${vistos.length} vezes`);
    else if (!confere(vistos[0])) achados.push(`${caminho}: ${nome} inesperado: ${vistos[0]}`);
  }

  for (const server of valores('server')) {
    if (/\d/.test(server)) achados.push(`${caminho}: Server expõe versão: ${server}`);
  }

  if (cacheControl !== undefined) {
    const vistos = valores('cache-control');
    if (vistos.join(', ') !== cacheControl) {
      achados.push(`${caminho}: Cache-Control "${vistos.join(', ')}", esperado "${cacheControl}"`);
    }
  }
  return achados;
}

/** O primeiro bundle que o index.html referencia — o nome muda a cada build. */
export function primeiroBundle(html) {
  return html.match(/\/assets\/[^"']+\.js/)?.[0] ?? null;
}

async function cabecalhos(base, caminho) {
  const resposta = await fetch(new URL(caminho, base), { redirect: 'manual' });
  return { status: resposta.status, headers: [...resposta.headers], corpo: await resposta.text() };
}

async function main(argv) {
  const base = argv.find((a) => !a.startsWith('--'));
  if (!base) {
    console.error('uso: node scripts/verificar-cabecalhos.mjs <url-base> [--api]');
    return 2;
  }

  const achados = [];
  let raiz;
  try {
    raiz = await cabecalhos(base, '/');
  } catch (causa) {
    console.error(`sem resposta de ${base}: ${causa.message}`);
    return 2;
  }

  const bundle = primeiroBundle(raiz.corpo);
  if (!bundle) achados.push('/: index.html não referencia bundle nenhum em /assets/');

  // Os três tratamentos de cache que o `map` do template distingue, mais o 404 de bundle — que
  // precisa continuar 404 e trazer os cabeçalhos, porque o `always` existe por ele.
  const casos = [
    ['/', { cacheControl: 'no-cache' }],
    ['/index.html', { cacheControl: 'no-cache' }],
    ['/hoje', { cacheControl: 'no-cache' }],
    ...(bundle ? [[bundle, { cacheControl: 'public, max-age=31536000, immutable' }]] : []),
    ['/assets/nao-existe.js', { status: 404 }],
  ];
  if (argv.includes('--api')) casos.push(['/api/auth/providers', {}]);

  for (const [caminho, { status = 200, ...opcoes }] of casos) {
    const resposta = caminho === '/' ? raiz : await cabecalhos(base, caminho);
    if (resposta.status !== status) {
      achados.push(`${caminho}: status ${resposta.status}, esperado ${status}`);
    }
    achados.push(...problemas(caminho, resposta.headers, opcoes));
  }

  if (achados.length > 0) {
    console.error(achados.map((a) => `✗ ${a}`).join('\n'));
    return 1;
  }
  console.log(`✓ cabeçalhos de segurança conferem em ${casos.length} caminhos de ${base}`);
  return 0;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  process.exit(await main(process.argv.slice(2)));
}
