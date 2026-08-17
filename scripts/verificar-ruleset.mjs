#!/usr/bin/env node
/**
 * Confere que todo required check da ruleset de `main` corresponde a um job que existe.
 *
 *   node scripts/verificar-ruleset.mjs
 *
 * Por que existe: o contexto de um required check do GitHub Actions é o nome do job. Renomear um
 * job quebra a proteção de `main` em silêncio — o check exigido deixa de existir, nunca reporta, e
 * todo PR fica preso em "Expected — waiting for status to be reported". A regra está escrita no
 * CLAUDE.md e em `docs/09-regras-repositorio.md`, mas documentação não é portão: depende de alguém
 * lembrar no momento certo. Este script troca a lembrança por verificação.
 *
 * Roda como primeiro passo do job `web`, logo depois do checkout e antes de qualquer instalação de
 * dependência — por isso não usa pacote nenhum, só o Node que o runner já traz. Não vira job
 * próprio de propósito: job novo só é portão entrando na ruleset, e aí precisaria rodar sem filtro
 * de path (D19), que é exatamente o footgun que esta guarda existe para fechar.
 *
 * Códigos de saída:
 *   0  todo contexto da ruleset tem job correspondente
 *   1  há contexto órfão — a proteção de `main` está quebrada ou prestes a quebrar
 *   2  não deu para ler a ruleset ou os workflows
 */
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rulesetFile = resolve(root, '.github/rulesets/main.json');
const workflowsDir = resolve(root, '.github/workflows');

/** Contextos exigidos pela ruleset, na ordem em que aparecem no JSON. */
export function contextosExigidos(ruleset) {
  const regra = (ruleset.rules ?? []).find((r) => r.type === 'required_status_checks');
  const checks = regra?.parameters?.required_status_checks ?? [];
  return checks.map((check) => check.context).filter((contexto) => typeof contexto === 'string');
}

/**
 * Extrai de um workflow o contexto que cada job reporta.
 *
 * Leitura proposital de um recorte só do YAML, em vez de dependência de parser: o script precisa
 * rodar antes do `npm ci`. O recorte é o bloco `jobs:` de primeiro nível — as chaves logo abaixo
 * dele são os ids, e um `name:` dentro do job substitui o id no contexto do check. Hoje nenhum job
 * usa `name:`, e é por isso mesmo que o caso precisa estar tratado: a divergência só apareceria no
 * dia em que alguém adicionasse um.
 */
export function contextosDeJobs(yaml) {
  const linhas = yaml.split('\n');
  const inicio = linhas.findIndex((linha) => /^jobs:\s*(#.*)?$/.test(linha));
  if (inicio === -1) return [];

  // A indentação dos ids é a do primeiro job encontrado, não um 2 fixo: um workflow indentado com
  // 4 espaços seria lido como "nenhum job", e a guarda acusaria órfão onde não há.
  let recuoDoId = null;
  const jobs = [];

  for (const linha of linhas.slice(inicio + 1)) {
    if (linha.trim() === '' || /^\s*#/.test(linha)) continue;

    const recuo = linha.length - linha.trimStart().length;
    if (recuo === 0) break; // saiu do bloco `jobs:`

    if (recuoDoId === null) recuoDoId = recuo;

    if (recuo === recuoDoId) {
      const id = linha.trim().match(/^([A-Za-z0-9_.-]+):\s*(#.*)?$/)?.[1];
      if (id) jobs.push({ id, name: null });
      continue;
    }

    // `name:` do job — só o do nível imediatamente abaixo do id conta. Mais fundo é `name:` de
    // step, que não tem relação com o contexto do check.
    const job = jobs.at(-1);
    if (job && recuo === recuoDoId * 2) {
      const valor = linha.trim().match(/^name:\s*(.+?)\s*$/)?.[1];
      if (valor) job.name = valor.replace(/^['"]|['"]$/g, '');
    }
  }

  return jobs.map((job) => job.name ?? job.id);
}

/** Todos os contextos que os workflows do repositório são capazes de reportar. */
export function contextosDisponiveis(dir = workflowsDir) {
  const contextos = new Map();
  for (const arquivo of readdirSync(dir).filter((nome) => /\.ya?ml$/.test(nome))) {
    for (const contexto of contextosDeJobs(readFileSync(resolve(dir, arquivo), 'utf8'))) {
      if (!contextos.has(contexto)) contextos.set(contexto, arquivo);
    }
  }
  return contextos;
}

export function orfaos(exigidos, disponiveis) {
  return exigidos.filter((contexto) => !disponiveis.has(contexto));
}

/**
 * Relatório final. A utilidade desta guarda está inteiramente na mensagem: quem esbarrar nela
 * provavelmente acabou de renomear um job e não faz ideia de que isso derruba a proteção.
 */
export function formatarRelatorio(exigidos, disponiveis) {
  const semJob = orfaos(exigidos, disponiveis);
  const lista = [...disponiveis.entries()].map(([ctx, arq]) => `  ${ctx}  (${arq})`);

  if (exigidos.length === 0) {
    return [
      'A ruleset não exige status check nenhum — `main` está aceitando merge sem CI.',
      '',
      'Se isso é intencional, remova este passo. Se não, acrescente os contextos em',
      '.github/rulesets/main.json e rode ./scripts/apply-repo-rules.sh',
    ].join('\n');
  }

  if (semJob.length === 0) {
    return [
      `Ruleset e workflows batem: ${exigidos.length} contexto(s) exigido(s), todos com job.`,
      '',
      ...exigidos.map((ctx) => `  ${ctx}  (${disponiveis.get(ctx)})`),
    ].join('\n');
  }

  return [
    `${semJob.length} contexto(s) da ruleset sem job correspondente:`,
    '',
    ...semJob.map((ctx) => `  ${ctx}`),
    '',
    'Um required check que nenhum job reporta trava todo PR em "Expected — waiting for status',
    'to be reported", e a proteção de `main` deixa de valer sem aviso.',
    '',
    'Jobs que os workflows reportam hoje:',
    '',
    ...lista,
    '',
    'Corrija atualizando .github/rulesets/main.json com os nomes de job atuais e rodando',
    '  ./scripts/apply-repo-rules.sh',
    '',
    'Lembre que o contexto é o `name:` do job quando ele existe, e o id do job quando não.',
  ].join('\n');
}

function main() {
  if (process.argv.slice(2).some((arg) => arg === '--help' || arg === '-h')) {
    console.log('uso: node scripts/verificar-ruleset.mjs');
    console.log('Confere que cada required check da ruleset corresponde a um job existente.');
    return 0;
  }

  const ruleset = JSON.parse(readFileSync(rulesetFile, 'utf8'));
  const exigidos = contextosExigidos(ruleset);
  const disponiveis = contextosDisponiveis();
  const relatorio = formatarRelatorio(exigidos, disponiveis);

  if (orfaos(exigidos, disponiveis).length > 0) {
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
    console.error(`::error::não deu para verificar a ruleset — ${error.message}`);
    process.exit(2);
  }
}
