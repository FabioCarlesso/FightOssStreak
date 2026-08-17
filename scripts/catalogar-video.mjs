#!/usr/bin/env node
/**
 * Cataloga um vídeo do YouTube em um nó do currículo, com verificação.
 *
 *   node scripts/catalogar-video.mjs M1.2 https://www.youtube.com/watch?v=XXXXXXXXXXX
 *   node scripts/catalogar-video.mjs M1.2 <url> --start 42
 *   node scripts/catalogar-video.mjs M1.2 <url> --dry-run
 *
 * Por que existe: preencher `video` à mão no JSON é fácil de errar e fácil de burlar — dá para
 * escrever um id que não existe, ou omitir o canal, que a política D7 exige creditar. Este script
 * consulta o oEmbed do YouTube para obter **título e canal reais** e recusa o que não conferir.
 *
 * O que ele verifica:
 *   - o vídeo existe e é público            (oEmbed responde 200)
 *   - o nome do canal, para o crédito        (author_name do oEmbed)
 *   - o vídeo permite ser incorporado        (playableInEmbed na página do watch)
 *
 * O que ele NÃO verifica, e só um humano pode: se o vídeo ensina de fato o que o nó descreve,
 * no nível certo e sem erro técnico. Assista antes de rodar isto.
 *
 * A conversa com o YouTube vive em `lib/youtube.mjs`, compartilhada com `verificar-videos.mjs` —
 * o que este script confere ao gravar é o mesmo que a verificação periódica reconfere depois.
 *
 * Requer acesso de rede ao youtube.com.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { checkEmbeddable, fetchOEmbed, parseVideoId } from './lib/youtube.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const curriculumDir = resolve(root, 'backend/src/main/resources/curriculum');

function loadModuleFor(nodeCode) {
  const moduleCode = nodeCode.split('.')[0];
  if (!/^M\d+$/.test(moduleCode ?? '')) {
    throw new Error(`Código de nó fora do formato M{n}.{n}: ${nodeCode}`);
  }
  const file = resolve(curriculumDir, `${moduleCode.toLowerCase()}.json`);
  const text = readFileSync(file, 'utf8');
  const data = JSON.parse(text);
  const node = data.nodes.find((candidate) => candidate.code === nodeCode);
  if (!node) {
    throw new Error(`Nó ${nodeCode} não existe em ${file}`);
  }
  return { file, text, node };
}

/** Fim do valor JSON que começa em `start` (objeto balanceado ou literal `null`). */
function endOfValue(text, start) {
  if (text.startsWith('null', start)) return start + 4;
  if (text[start] !== '{') {
    throw new Error(`Valor de "video" em formato inesperado na posição ${start}`);
  }
  let depth = 0;
  let inString = false;
  for (let i = start; i < text.length; i += 1) {
    const char = text[i];
    if (inString) {
      if (char === '\\') i += 1;
      else if (char === '"') inString = false;
      continue;
    }
    if (char === '"') inString = true;
    else if (char === '{') depth += 1;
    else if (char === '}') {
      depth -= 1;
      if (depth === 0) return i + 1;
    }
  }
  throw new Error('Objeto "video" sem fechamento');
}

/**
 * Substitui só o valor de `video` do nó, preservando o resto do arquivo byte a byte.
 *
 * Re-serializar o JSON inteiro reformataria as alternativas do quiz — que são escritas em uma
 * linha cada de propósito — e transformaria "troquei um vídeo" num diff de centenas de linhas,
 * justamente o que D11 quer evitar.
 */
export function replaceVideoBlock(text, nodeCode, video) {
  const codeMarker = `"code": ${JSON.stringify(nodeCode)}`;
  const codeIndex = text.indexOf(codeMarker);
  if (codeIndex === -1) {
    throw new Error(`Não achei ${codeMarker} no arquivo`);
  }

  const videoKey = '"video":';
  const keyIndex = text.indexOf(videoKey, codeIndex);
  if (keyIndex === -1) {
    throw new Error(`Nó ${nodeCode} não tem campo "video"`);
  }

  const valueStart = keyIndex + videoKey.length + 1; // pula o espaço após os dois-pontos
  const valueEnd = endOfValue(text, valueStart);

  const indent = ' '.repeat(6);
  const inner = ' '.repeat(8);
  const lines = Object.entries(video).map(
    ([key, value]) => `${inner}${JSON.stringify(key)}: ${JSON.stringify(value)}`,
  );
  const serialized = `{\n${lines.join(',\n')}\n${indent}}`;

  return text.slice(0, valueStart) + serialized + text.slice(valueEnd);
}

async function main() {
  const [nodeCode, urlOrId, ...rest] = process.argv.slice(2);
  if (!nodeCode || !urlOrId) {
    console.error(
      'uso: node scripts/catalogar-video.mjs <NODE_CODE> <url-ou-id> [--start N] [--dry-run]',
    );
    process.exit(2);
  }

  const dryRun = rest.includes('--dry-run');
  const startIndex = rest.indexOf('--start');
  const startSeconds = startIndex >= 0 ? Number(rest[startIndex + 1]) : null;
  if (startIndex >= 0 && (!Number.isInteger(startSeconds) || startSeconds < 0)) {
    throw new Error('--start exige um inteiro não negativo (segundos)');
  }

  const videoId = parseVideoId(urlOrId);
  const { file, text, node } = loadModuleFor(nodeCode);

  const oembed = await fetchOEmbed(videoId);
  const embed = await checkEmbeddable(videoId);

  if (embed.known && embed.embeddable === false) {
    throw new Error(
      `O autor de "${oembed.title}" desativou a incorporação. A política D7 proíbe embutir ` +
        'esse vídeo — escolha outro.',
    );
  }

  // O oEmbed às vezes devolve nome de canal com espaço nas pontas, e o crédito aparece na tela
  // colado a outros textos ("— canal X · assistir"). Aparar não é digitar: o valor continua vindo
  // da fonte.
  const title = oembed.title?.trim();
  const channel = oembed.author_name?.trim();

  if (!channel) {
    throw new Error(
      `O oEmbed não devolveu o canal de ${videoId}. Sem crédito ao canal não dá para catalogar (D7).`,
    );
  }

  // Sem esta checagem, `title` indefinido viraria o literal `undefined` no JSON e o erro só
  // apareceria como "Unexpected token 'u'" na releitura do arquivo — mensagem que não ajuda
  // ninguém. O validador do backend também recusa vídeo sem título.
  if (!title) {
    throw new Error(
      `O oEmbed não devolveu o título de ${videoId}. Sem título não dá para catalogar.`,
    );
  }

  const video = {
    youtubeId: videoId,
    title,
    channel,
    ...(startSeconds !== null ? { startSeconds } : {}),
  };

  const updated = replaceVideoBlock(text, nodeCode, video);

  // Cinto e suspensório: o arquivo resultante precisa continuar sendo JSON válido e conter
  // exatamente o vídeo que acabamos de verificar.
  const reparsed = JSON.parse(updated);
  const reparsedNode = reparsed.nodes.find((candidate) => candidate.code === nodeCode);
  if (reparsedNode?.video?.youtubeId !== videoId) {
    throw new Error('A edição do JSON não produziu o resultado esperado — nada foi gravado.');
  }

  console.log(`nó      : ${nodeCode} — ${node.title}`);
  console.log(`vídeo   : ${title}`);
  console.log(`canal   : ${channel}`);
  console.log(
    `embed   : ${embed.known ? (embed.embeddable ? 'permitido' : 'BLOQUEADO') : 'não determinado'}`,
  );

  if (dryRun) {
    console.log('\n--dry-run: nada foi gravado.');
    return;
  }

  writeFileSync(file, updated);
  console.log(`\ngravado em ${file}`);
  console.log('Agora rode `cd backend && ./mvnw test` para validar a integridade do currículo.');
}

// Só executa quando chamado direto, para que replaceVideoBlock possa ser importado em teste.
if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  main().catch((error) => {
    console.error(`erro: ${error.message}`);
    process.exit(1);
  });
}
