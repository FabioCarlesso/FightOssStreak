#!/usr/bin/env node
/**
 * Cataloga um vídeo do YouTube em um nó do currículo, com verificação.
 *
 *   node scripts/catalogar-video.mjs M1.2 https://www.youtube.com/watch?v=XXXXXXXXXXX
 *   node scripts/catalogar-video.mjs M1.2 <url> --start 42
 *   node scripts/catalogar-video.mjs M1.2 <url> --dry-run
 *
 * Complementares (D32) — acrescentam à lista `extraVideos` em vez de trocar o canônico:
 *
 *   node scripts/catalogar-video.mjs M1.3 --extra <url1> <url2> <url3>
 *   node scripts/catalogar-video.mjs M1.3 --extra <url> --note "o giro que o professor insistiu"
 *
 * Aceitar várias urls de uma vez não é conveniência: complementar entra em lote por natureza (são
 * clipes de uma mesma aula), e trinta e sete invocações uma a uma é o atrito que faz a curadoria
 * parar no quinto item.
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

/**
 * Teto de complementares por nó. Segunda cópia de `CurriculumValidator.MAX_EXTRA_VIDEOS`, e
 * inevitável pelo mesmo motivo do regex de id: uma em Java, outra em JavaScript. Mudou uma, mude a
 * outra. Recusar aqui é o que dá mensagem útil na hora, em vez de falha no `mvnw test` depois.
 */
const MAX_EXTRA_VIDEOS = 4;

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

  return text.slice(0, valueStart) + serializeVideo(video, 6) + text.slice(valueEnd);
}

/** Um bloco `{ ... }` de vídeo, com a chave fechando na coluna de `indent`. */
function serializeVideo(video, indent) {
  const outer = ' '.repeat(indent);
  const inner = ' '.repeat(indent + 2);
  const lines = Object.entries(video).map(
    ([key, value]) => `${inner}${JSON.stringify(key)}: ${JSON.stringify(value)}`,
  );
  return `{\n${lines.join(',\n')}\n${outer}}`;
}

/**
 * Fim do objeto do nó que começa em `codeIndex` — na prática, onde começa o nó seguinte.
 *
 * Existe porque `extraVideos` é campo **opcional**: procurá-lo sem limite superior encontraria o
 * `extraVideos` de um nó posterior e acrescentaria o clipe no nó errado. O canônico não tinha esse
 * risco porque `"video"` existe em todos os nós, então a primeira ocorrência é sempre a certa.
 */
function endOfNode(text, codeIndex) {
  const next = text.indexOf('"code": ', codeIndex + 1);
  return next === -1 ? text.length : next;
}

/**
 * Acrescenta complementares ao nó, preservando o arquivo byte a byte fora do trecho alterado.
 *
 * Cria a chave `extraVideos` logo depois de `video` quando ela ainda não existe — que é o caso de
 * todos os 46 nós hoje. Pôr o campo ali, e não no fim do nó, mantém junto o que é da mesma
 * natureza: primeiro o vídeo que ensina, depois os que lembram.
 */
export function appendExtraVideos(text, nodeCode, videos) {
  const codeMarker = `"code": ${JSON.stringify(nodeCode)}`;
  const codeIndex = text.indexOf(codeMarker);
  if (codeIndex === -1) {
    throw new Error(`Não achei ${codeMarker} no arquivo`);
  }
  const limit = endOfNode(text, codeIndex);

  const blocks = videos.map((video) => `${' '.repeat(8)}${serializeVideo(video, 8)}`);

  const extraKey = '"extraVideos":';
  const extraIndex = text.indexOf(extraKey, codeIndex);

  if (extraIndex !== -1 && extraIndex < limit) {
    const open = text.indexOf('[', extraIndex);
    const close = text.indexOf(']', open);
    if (open === -1 || close === -1) {
      throw new Error(`Nó ${nodeCode}: campo "extraVideos" em formato inesperado`);
    }
    const existing = text.slice(open + 1, close).trim();
    const joined = existing
      ? `${existing.replace(/,$/, '')},\n${blocks.join(',\n')}`
      : blocks.join(',\n');
    return `${text.slice(0, open)}[\n${joined}\n${' '.repeat(6)}]${text.slice(close + 1)}`;
  }

  const videoKey = '"video":';
  const keyIndex = text.indexOf(videoKey, codeIndex);
  if (keyIndex === -1 || keyIndex >= limit) {
    throw new Error(`Nó ${nodeCode} não tem campo "video"`);
  }
  const videoEnd = endOfValue(text, keyIndex + videoKey.length + 1);
  const inserted = `,\n${' '.repeat(6)}"extraVideos": [\n${blocks.join(',\n')}\n${' '.repeat(6)}]`;
  return text.slice(0, videoEnd) + inserted + text.slice(videoEnd);
}

/** Consulta o YouTube e devolve o que dá para gravar sobre um vídeo, ou lança explicando. */
async function descrever(urlOrId) {
  const videoId = parseVideoId(urlOrId);
  const oembed = await fetchOEmbed(videoId);
  const embed = await checkEmbeddable(videoId);

  // O oEmbed às vezes devolve nome de canal com espaço nas pontas, e o crédito aparece na tela
  // colado a outros textos ("— canal X · assistir"). Aparar não é digitar: o valor continua vindo
  // da fonte.
  const title = oembed.title?.trim();
  const channel = oembed.author_name?.trim();

  if (embed.known && embed.embeddable === false) {
    throw new Error(
      `O autor de "${title}" desativou a incorporação. A política D7 proíbe embutir ` +
        'esse vídeo — escolha outro.',
    );
  }
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
  return { videoId, title, channel, embed };
}

function rotuloEmbed(embed) {
  if (!embed.known) return 'não determinado';
  return embed.embeddable ? 'permitido' : 'BLOQUEADO';
}

/** Grava o vídeo canônico do nó, substituindo o que estiver lá. */
async function catalogarCanonico({ nodeCode, url, startSeconds, dryRun }) {
  const { file, text, node } = loadModuleFor(nodeCode);
  const { videoId, title, channel, embed } = await descrever(url);

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
  console.log(`embed   : ${rotuloEmbed(embed)}`);

  return { file, updated, dryRun };
}

/**
 * Acrescenta complementares ao nó (D32).
 *
 * As recusas acontecem **antes** de qualquer escrita, e todas as urls são consultadas primeiro: um
 * lote que estoura o teto no terceiro clipe não pode deixar dois gravados e o resto de fora.
 */
async function catalogarComplementares({ nodeCode, urls, note, dryRun }) {
  const { file, text, node } = loadModuleFor(nodeCode);
  const jaTem = (node.extraVideos ?? []).map((extra) => extra.youtubeId);

  if (jaTem.length + urls.length > MAX_EXTRA_VIDEOS) {
    throw new Error(
      `Nó ${nodeCode} já tem ${jaTem.length} complementar(es); mais ${urls.length} passaria do ` +
        `teto de ${MAX_EXTRA_VIDEOS} (D32). O teto existe para a tira de clipes não virar catálogo.`,
    );
  }

  const videos = [];
  for (const url of urls) {
    const { videoId, title, channel, embed } = await descrever(url);

    if (videoId === node.video?.youtubeId) {
      throw new Error(`${videoId} já é o vídeo canônico de ${nodeCode} — nada foi gravado.`);
    }
    if (jaTem.includes(videoId) || videos.some((v) => v.youtubeId === videoId)) {
      throw new Error(`${videoId} já é complementar de ${nodeCode} — nada foi gravado.`);
    }

    videos.push({
      youtubeId: videoId,
      title,
      channel,
      // HORIZONTAL é o default do schema; gravar o campo só quando ele diz algo mantém o diff
      // pequeno e o JSON legível.
      ...(embed.orientation === 'VERTICAL' ? { orientation: 'VERTICAL' } : {}),
      ...(note ? { note } : {}),
    });
    console.log(
      `+ ${videoId}  ${embed.orientation === 'VERTICAL' ? '9:16' : '16:9'}  ${rotuloEmbed(embed)}  ${title}`,
    );
    console.log(`  canal: ${channel}`);
  }

  const updated = appendExtraVideos(text, nodeCode, videos);

  const reparsed = JSON.parse(updated);
  const reparsedNode = reparsed.nodes.find((candidate) => candidate.code === nodeCode);
  const gravados = (reparsedNode?.extraVideos ?? []).map((extra) => extra.youtubeId);
  const esperados = [...jaTem, ...videos.map((v) => v.youtubeId)];
  if (gravados.join(',') !== esperados.join(',')) {
    throw new Error('A edição do JSON não produziu o resultado esperado — nada foi gravado.');
  }

  console.log(`\nnó      : ${nodeCode} — ${node.title}`);
  console.log(`clipes  : ${gravados.length} de ${MAX_EXTRA_VIDEOS}`);

  return { file, updated, dryRun };
}

async function main() {
  const args = process.argv.slice(2);
  const flags = new Set(args.filter((arg) => arg.startsWith('--')));
  const positionais = args.filter((arg, i) => {
    if (arg.startsWith('--')) return false;
    const anterior = args[i - 1];
    return anterior !== '--start' && anterior !== '--note';
  });

  const [nodeCode, ...urls] = positionais;
  if (!nodeCode || urls.length === 0) {
    console.error(
      'uso: node scripts/catalogar-video.mjs <NODE_CODE> <url-ou-id> [--start N] [--dry-run]\n' +
        '     node scripts/catalogar-video.mjs <NODE_CODE> --extra <url> [<url>...] [--note TEXTO]',
    );
    process.exit(2);
  }

  const dryRun = flags.has('--dry-run');
  const extra = flags.has('--extra');

  const startIndex = args.indexOf('--start');
  const startSeconds = startIndex >= 0 ? Number(args[startIndex + 1]) : null;
  if (startIndex >= 0 && (!Number.isInteger(startSeconds) || startSeconds < 0)) {
    throw new Error('--start exige um inteiro não negativo (segundos)');
  }

  const noteIndex = args.indexOf('--note');
  const note = noteIndex >= 0 ? args[noteIndex + 1] : null;
  if (noteIndex >= 0 && !note) {
    throw new Error('--note exige um texto');
  }
  if (note && !extra) {
    throw new Error('--note só existe para complementar (--extra): o canônico não tem anotação');
  }
  // A anotação é a única coisa aqui escrita por quem cataloga, e ela descreve *um* clipe. Aplicar o
  // mesmo texto a um lote inteiro produziria três clipes com a mesma nota, que é pior que nenhuma.
  if (note && urls.length > 1) {
    throw new Error('--note vale para uma url por vez; rode o lote sem nota e depois uma a uma');
  }
  if (!extra && urls.length > 1) {
    throw new Error('o vídeo canônico é um só; para acrescentar complementares use --extra');
  }

  const { file, updated } = extra
    ? await catalogarComplementares({ nodeCode, urls, note, dryRun })
    : await catalogarCanonico({ nodeCode, url: urls[0], startSeconds, dryRun });

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
