#!/usr/bin/env node
/**
 * Confere que todo link interno da documentação aponta para algo que existe.
 *
 *   node scripts/verificar-links-docs.mjs
 *
 * Por que existe (#135): a documentação está sendo reorganizada em arquivos curtos por assunto, e
 * o repositório cita `docs/NN-*.md` em código Java, TypeScript, migrations, Dockerfile, workflows e
 * no CLAUDE.md. Renomear um arquivo de `docs/` sem atualizar uma dessas menções não quebra build nem
 * teste nenhum — só deixa quem chega pelo comentário procurando um arquivo que sumiu. Âncora é o
 * mesmo problema em menor escala: mudar o título de uma seção quebra todo `arquivo.md#secao` que
 * apontava para ela.
 *
 * Duas checagens:
 *   1. Links relativos dentro de arquivos Markdown — `[texto](caminho#ancora)` e `[ref]: caminho` —
 *      resolvidos a partir do próprio arquivo. Link só de âncora (`#secao`) é checado contra o
 *      próprio arquivo.
 *   2. Menções a `docs/….md` em qualquer arquivo versionado, resolvidas a partir da raiz. É o que
 *      pega o comentário de código e o CLAUDE.md, que citam o caminho sem ser link.
 *
 * Roda no job `web` antes de qualquer instalação, como a guarda da ruleset: só usa o Node do runner
 * e o `git ls-files` do checkout. Não vira job próprio pelo mesmo motivo daquela (D19).
 *
 * Códigos de saída:
 *   0  todo link e menção resolvem
 *   1  há link ou menção quebrada
 *   2  não deu para listar ou ler os arquivos
 */
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

/**
 * Arquivos fora da checagem. Gerado não se corrige à mão (regra 2 do CLAUDE.md), e o currículo é
 * dado editorial (D11). O próprio script e o teste dele citam caminhos inexistentes de propósito.
 */
const IGNORADOS = [
  /^shared\/types\/generated\//,
  /^backend\/openapi\.json$/,
  /^package-lock\.json$/,
  /^scripts\/verificar-links-docs(\.test)?\.mjs$/,
];

const EXTENSOES_DE_TEXTO =
  /\.(md|mjs|js|ts|tsx|java|sql|yml|yaml|json|sh|css|html|conf|template|properties|txt)$|(^|\/)(Dockerfile|\.prettierignore|\.editorconfig|\.gitignore)$/;

/**
 * Âncora que o GitHub gera para um título: minúsculas, pontuação removida, cada espaço vira hífen.
 * Letras acentuadas ficam — `## Onde documentar cada mudança` vira `#onde-documentar-cada-mudança`.
 */
export function ancoraDoTitulo(titulo) {
  return titulo
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1') // link vira só o texto
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\p{M}\s_-]/gu, '')
    .replace(/ /g, '-');
}

/** Linhas fora de bloco de código cercado, com o número original de cada uma. */
function linhasForaDeCodigo(texto) {
  const resultado = [];
  let cerca = null;
  texto.split('\n').forEach((linha, i) => {
    const abertura = linha.match(/^\s*(`{3,}|~{3,})/);
    if (abertura) {
      if (cerca === null) cerca = abertura[1][0];
      else if (abertura[1][0] === cerca) cerca = null;
      return;
    }
    if (cerca === null) resultado.push({ linha, numero: i + 1 });
  });
  return resultado;
}

/**
 * Âncoras que um Markdown oferece: títulos (com o sufixo `-1`, `-2`… que o GitHub dá a títulos
 * repetidos) e `id`/`name` de HTML embutido.
 */
export function ancorasDoMarkdown(texto) {
  const ancoras = new Set();
  const contagem = new Map();
  for (const { linha } of linhasForaDeCodigo(texto)) {
    const titulo = linha.match(/^#{1,6}\s+(.+?)\s*#*\s*$/);
    if (titulo) {
      const base = ancoraDoTitulo(titulo[1]);
      const vezes = contagem.get(base) ?? 0;
      contagem.set(base, vezes + 1);
      ancoras.add(vezes === 0 ? base : `${base}-${vezes}`);
    }
    for (const html of linha.matchAll(/<[a-z][^>]*\s(?:id|name)="([^"]+)"/gi)) {
      ancoras.add(html[1]);
    }
  }
  return ancoras;
}

/** Links relativos de um Markdown, ignorando código cercado e trecho entre crases. */
export function linksDoMarkdown(texto) {
  const links = [];
  for (const { linha, numero } of linhasForaDeCodigo(texto)) {
    const semCodigo = linha.replace(/`[^`]*`/g, '');
    const alvos = [
      ...[...semCodigo.matchAll(/\]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)/g)].map((m) => m[1]),
      ...[...semCodigo.matchAll(/^\s*\[[^\]]+\]:\s*<?(\S+?)>?(?:\s|$)/g)].map((m) => m[1]),
    ];
    for (const alvo of alvos) {
      if (/^[a-z][a-z0-9+.-]*:/i.test(alvo)) continue; // http:, mailto:…
      links.push({ alvo, numero });
    }
  }
  return links;
}

/**
 * Menções a `docs/….md` em qualquer texto. A barra antes de `docs/` fica de fora de propósito:
 * `https://github.com/outro/repo/blob/main/docs/x.md` é de outro repositório.
 */
export function mencoesADocs(texto) {
  const mencoes = [];
  texto.split('\n').forEach((linha, i) => {
    for (const m of linha.matchAll(/(?<![\w/.-])(docs\/[\w./-]*\.md)(#[\p{L}\p{N}_-]+)?/gu)) {
      mencoes.push({ alvo: m[1] + (m[2] ?? ''), numero: i + 1 });
    }
  });
  return mencoes;
}

/**
 * Resolve `alvo` a partir de `base` (diretório) e diz o que está errado, ou `null` se resolve.
 * `ancorasDe` lê as âncoras de um arquivo — injetado para o teste não depender do disco.
 */
export function problemaDoAlvo(alvo, base, { existe, ancorasDe, arquivoAtual }) {
  const [caminhoBruto, ancoraBruta] = alvo.split('#');
  const caminho = decodeURIComponent(caminhoBruto);
  const destino = caminho === '' ? arquivoAtual : resolve(base, caminho);

  if (!existe(destino)) return `arquivo inexistente: ${caminho}`;
  if (ancoraBruta === undefined || ancoraBruta === '' || !destino.endsWith('.md')) return null;

  const ancora = decodeURIComponent(ancoraBruta).toLowerCase();
  if (!ancorasDe(destino).has(ancora)) return `âncora inexistente: #${ancora}`;
  return null;
}

function arquivosVersionados() {
  return execFileSync('git', ['ls-files', '-z'], { cwd: root, encoding: 'utf8' })
    .split('\0')
    .filter((f) => f && EXTENSOES_DE_TEXTO.test(f) && !IGNORADOS.some((re) => re.test(f)));
}

export function verificar(arquivos = arquivosVersionados()) {
  const cacheDeAncoras = new Map();
  const ancorasDe = (arquivo) => {
    if (!cacheDeAncoras.has(arquivo)) {
      cacheDeAncoras.set(arquivo, ancorasDoMarkdown(readFileSync(arquivo, 'utf8')));
    }
    return cacheDeAncoras.get(arquivo);
  };
  const existe = (caminho) =>
    existsSync(caminho) && (statSync(caminho).isFile() || statSync(caminho).isDirectory());

  // Um link Markdown com alvo `docs/…` também é menção: a chave evita relatar a mesma quebra duas
  // vezes.
  const quebrados = new Map();
  const registrar = (q) => quebrados.set(`${q.arquivo}:${q.numero}:${q.alvo}`, q);
  for (const relativo of arquivos) {
    const arquivo = resolve(root, relativo);
    if (!existsSync(arquivo)) continue; // removido no working tree e ainda no índice
    const texto = readFileSync(arquivo, 'utf8');
    const contexto = { existe, ancorasDe, arquivoAtual: arquivo };

    if (relativo.endsWith('.md')) {
      for (const { alvo, numero } of linksDoMarkdown(texto)) {
        const problema = problemaDoAlvo(alvo, dirname(arquivo), contexto);
        if (problema) registrar({ arquivo: relativo, numero, alvo, problema });
      }
    }
    for (const { alvo, numero } of mencoesADocs(texto)) {
      const problema = problemaDoAlvo(alvo, root, contexto);
      if (problema) registrar({ arquivo: relativo, numero, alvo, problema });
    }
  }
  return [...quebrados.values()];
}

export function formatarRelatorio(quebrados) {
  if (quebrados.length === 0) return 'Links da documentação: todos resolvem.';
  return [
    `${quebrados.length} link(s) ou menção(ões) a documentação que não resolvem:`,
    '',
    ...quebrados.map((q) => `  ${q.arquivo}:${q.numero}  ${q.alvo}  — ${q.problema}`),
    '',
    'Arquivo de docs/ renomeado ou título de seção alterado? Atualize quem aponta para ele no',
    'mesmo PR. O índice de docs/README.md diz onde cada assunto mora agora.',
  ].join('\n');
}

function main() {
  if (process.argv.slice(2).some((arg) => arg === '--help' || arg === '-h')) {
    console.log('uso: node scripts/verificar-links-docs.mjs');
    console.log('Confere links relativos em Markdown e menções a docs/*.md no repositório.');
    return 0;
  }
  const quebrados = verificar();
  const relatorio = formatarRelatorio(quebrados);
  if (quebrados.length > 0) {
    console.error(`::error::${relatorio.split('\n')[0]}`);
    console.error(relatorio);
    return 1;
  }
  console.log(relatorio);
  return 0;
}

// Só executa quando chamado direto, para que as funções acima possam ser importadas em teste.
if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  try {
    process.exit(main());
  } catch (error) {
    console.error(`::error::não deu para verificar os links — ${error.message}`);
    process.exit(2);
  }
}
