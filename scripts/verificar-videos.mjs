#!/usr/bin/env node
/**
 * Reconfere se os vídeos já catalogados continuam disponíveis e incorporáveis.
 *
 *   node scripts/verificar-videos.mjs
 *
 * Por que existe: vídeo do YouTube sai do ar, vira privado ou tem a incorporação desativada depois
 * de catalogado — risco listado em `docs/07-decisoes.md`. Sem esta checagem, a descoberta é
 * acidental e acontece no pior momento possível: abrindo o nó depois do treino.
 *
 * O que ele faz: lê todos os `m*.json`, pega os nós com `video` preenchido e pergunta ao YouTube,
 * um a um, se cada id ainda está público e incorporável. Não escreve nada — a substituição de um
 * vídeo é curadoria humana (`docs/08-curadoria-videos.md`), não automação.
 *
 * Códigos de saída, porque quem chama (o workflow `videos.yml`) decide por eles:
 *   0  tudo disponível — inclusive o caso "nenhum vídeo catalogado ainda"
 *   1  há vídeo indisponível ou não-incorporável: alguém precisa escolher outro
 *   2  não deu para concluir (rede, YouTube fora) — não é acusação contra o currículo
 *
 * Requer acesso de rede ao youtube.com.
 */
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { verificarDisponibilidade } from './lib/youtube.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const curriculumDir = resolve(root, 'backend/src/main/resources/curriculum');

/**
 * Pausa entre consultas. São dezenas de ids, não milhares: serializar com um respiro já mantém o
 * ritmo longe de qualquer limite do YouTube, e o job semanal não tem pressa.
 */
const PAUSA_MS = 400;

/** Lê os módulos do currículo na ordem numérica (m0, m1, … m10), não na ordem alfabética. */
export function lerModulos(dir = curriculumDir) {
  return readdirSync(dir)
    .filter((nome) => /^m\d+\.json$/.test(nome))
    .sort((a, b) => Number(a.match(/\d+/)[0]) - Number(b.match(/\d+/)[0]))
    .map((nome) => ({ arquivo: nome, dados: JSON.parse(readFileSync(resolve(dir, nome), 'utf8')) }));
}

/**
 * Achata os módulos na lista do que há para verificar: só os nós com vídeo catalogado.
 *
 * `video: null` é estado normal e majoritário hoje (M2–M8 inteiros) — currículo sem vídeo nenhum
 * precisa produzir "nada a verificar", não erro.
 */
export function coletarVideos(modulos) {
  const videos = [];
  for (const { arquivo, dados } of modulos) {
    for (const node of dados.nodes ?? []) {
      if (!node.video?.youtubeId) continue;
      videos.push({
        code: node.code,
        titulo: node.title,
        youtubeId: node.video.youtubeId,
        arquivo,
      });
    }
  }
  return videos;
}

/** Só `indisponivel` e `nao-incorporavel` são problema de curadoria; `indeterminado` não é. */
function problemas(resultados) {
  return resultados.filter(
    ({ status }) => status === 'indisponivel' || status === 'nao-incorporavel',
  );
}

function indeterminados(resultados) {
  return resultados.filter(({ status }) => status === 'indeterminado');
}

export function codigoDeSaida(resultados) {
  if (problemas(resultados).length > 0) return 1;
  if (indeterminados(resultados).length > 0) return 2;
  return 0;
}

const ROTULO = {
  ok: 'ok',
  indisponivel: 'INDISPONÍVEL',
  'nao-incorporavel': 'NÃO INCORPORÁVEL',
  indeterminado: 'não verificado',
};

function linha({ code, youtubeId, status, motivo }) {
  const base = `  ${code.padEnd(6)} ${youtubeId}  ${ROTULO[status]}`;
  return motivo ? `${base} — ${motivo}` : base;
}

/** Relatório final, em texto puro: é o que vai para o log do job e para o corpo da issue. */
export function formatarRelatorio(resultados) {
  if (resultados.length === 0) {
    return 'Nenhum vídeo catalogado — nada a verificar.';
  }

  const quebrados = problemas(resultados);
  const naoVerificados = indeterminados(resultados);
  const disponiveis = resultados.length - quebrados.length - naoVerificados.length;
  const partes = [];

  if (quebrados.length === 0 && disponiveis === 0) {
    // Dizer "tudo disponível: 0 de 11" quando o YouTube não respondeu a nenhuma consulta seria
    // afirmar o que não se sabe. Sem nenhuma resposta, não há conclusão sobre o currículo.
    partes.push('Nenhum vídeo pôde ser verificado agora — nada foi concluído sobre o currículo.');
  } else if (quebrados.length === 0) {
    partes.push(
      `Tudo disponível: ${disponiveis} de ${resultados.length} ` +
        'vídeos catalogados estão públicos e incorporáveis.',
    );
  } else {
    partes.push(
      `${quebrados.length} de ${resultados.length} vídeos catalogados com problema:`,
      '',
      ...quebrados.map(linha),
      '',
      'A substituição é curadoria humana: escolha o novo vídeo pelos critérios do nó em',
      'docs/08-curadoria-videos.md e recatalogue com',
      '  node scripts/catalogar-video.mjs <NÓ> <url>',
    );
  }

  if (naoVerificados.length > 0) {
    partes.push(
      '',
      `${naoVerificados.length} não deu para verificar (rede ou YouTube), o que não é conclusão sobre o vídeo:`,
      '',
      ...naoVerificados.map(linha),
    );
  }

  return partes.join('\n');
}

const dormir = (ms) => new Promise((cumprir) => setTimeout(cumprir, ms));

async function main() {
  if (process.argv.slice(2).some((arg) => arg === '--help' || arg === '-h')) {
    console.log('uso: node scripts/verificar-videos.mjs');
    console.log('Verifica os vídeos já catalogados. Não altera arquivo nenhum.');
    return 0;
  }

  const videos = coletarVideos(lerModulos());
  if (videos.length === 0) {
    console.log(formatarRelatorio([]));
    return 0;
  }

  console.log(`Verificando ${videos.length} vídeos catalogados...\n`);

  const resultados = [];
  for (const [indice, video] of videos.entries()) {
    const resultado = { ...video, ...(await verificarDisponibilidade(video.youtubeId)) };
    resultados.push(resultado);
    console.log(linha(resultado));
    if (indice < videos.length - 1) await dormir(PAUSA_MS);
  }

  console.log(`\n${formatarRelatorio(resultados)}`);
  return codigoDeSaida(resultados);
}

// Só executa quando chamado direto, para que as funções acima possam ser importadas em teste.
if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  main()
    .then((codigo) => process.exit(codigo))
    .catch((error) => {
      console.error(`erro: ${error.message}`);
      process.exit(2);
    });
}
