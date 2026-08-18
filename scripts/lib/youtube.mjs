/**
 * Consulta ao YouTube: o que dá para saber sobre um vídeo sem assistir a ele.
 *
 * Existe como módulo próprio porque dois scripts precisam exatamente da mesma pergunta —
 * `catalogar-video.mjs`, na hora de gravar um vídeo em um nó, e `verificar-videos.mjs`, para
 * reconferir periodicamente os que já estão gravados. Duplicar isso significaria que uma cópia
 * envelheceria em silêncio: o YouTube muda o formato da página, um script passa a errar e o
 * outro não.
 *
 * Requer acesso de rede ao youtube.com. Todas as funções aceitam `fetchImpl` para que o teste
 * não dependa de rede.
 */

/**
 * Erro de "este vídeo não existe mais para nós": removido, privado ou id inválido.
 *
 * Tem tipo próprio porque a distinção importa a quem chama: vídeo indisponível é problema de
 * curadoria (alguém precisa escolher outro), enquanto uma falha de rede ou um 500 do YouTube é
 * problema do momento — e reportar os dois como a mesma coisa produziria aviso falso toda vez
 * que a rede tossisse.
 */
export class VideoIndisponivelError extends Error {
  constructor(message) {
    super(message);
    this.name = 'VideoIndisponivelError';
  }
}

/**
 * Extrai o id de 11 caracteres das formas de URL que o YouTube usa.
 *
 * O formato do id também é validado no backend, em `CurriculumValidator.YOUTUBE_ID_REGEX`. São as
 * duas únicas cópias, e são inevitáveis: uma em JavaScript, outra em Java. Mudou uma, mude a outra.
 */
export function parseVideoId(input) {
  if (/^[\w-]{11}$/.test(input)) return input;

  let url;
  try {
    url = new URL(input);
  } catch {
    throw new Error(`URL inválida: ${input}`);
  }

  const host = url.hostname.replace(/^www\./, '');
  let candidate = null;

  if (host === 'youtu.be') {
    candidate = url.pathname.slice(1);
  } else if (
    host === 'youtube.com' ||
    host === 'm.youtube.com' ||
    host === 'youtube-nocookie.com'
  ) {
    if (url.pathname === '/watch') {
      candidate = url.searchParams.get('v');
    } else if (url.pathname.startsWith('/shorts/') || url.pathname.startsWith('/embed/')) {
      candidate = url.pathname.split('/')[2];
    }
  }

  if (!candidate || !/^[\w-]{11}$/.test(candidate)) {
    throw new Error(`Não consegui extrair um id de vídeo de: ${input}`);
  }
  return candidate;
}

/** Consulta o oEmbed: confirma existência e devolve título e canal reais. */
export async function fetchOEmbed(videoId, { fetchImpl = fetch } = {}) {
  const endpoint =
    'https://www.youtube.com/oembed?format=json&url=' +
    encodeURIComponent(`https://www.youtube.com/watch?v=${videoId}`);

  const response = await fetchImpl(endpoint);
  if (response.status === 404 || response.status === 401) {
    throw new VideoIndisponivelError(
      `Vídeo ${videoId} não está acessível (HTTP ${response.status}). ` +
        'Pode não existir, ser privado ou ter sido removido.',
    );
  }
  if (!response.ok) {
    throw new Error(`oEmbed respondeu HTTP ${response.status} para ${videoId}`);
  }
  return response.json();
}

/**
 * Orientação a partir do primeiro formato de vídeo declarado na página do watch.
 *
 * O primeiro par `"width":W,"height":H` do HTML é a maior resolução do próprio vídeo — conferido
 * contra os canônicos já catalogados (3840x2160) e contra os clipes de celular (720x1280), que é a
 * separação que interessa. Quadrado conta como horizontal: o frame 16:9 acomoda um vídeo 1:1 sem
 * espremer, o 9:16 não.
 *
 * Devolve `null` quando não deu para saber, e quem chama trata isso como HORIZONTAL — que é o que a
 * esmagadora maioria dos vídeos do YouTube é.
 */
function parseOrientation(html) {
  const match = html.match(/"width":(\d+),"height":(\d+)/);
  if (!match) return null;
  return Number(match[2]) > Number(match[1]) ? 'VERTICAL' : 'HORIZONTAL';
}

/**
 * Confere se o autor permite incorporação. A política D7 proíbe embutir vídeo marcado como
 * não-incorporável — respeitar isso é obrigação, não detalhe.
 *
 * Devolve a orientação junto porque ela sai do mesmo HTML: pedir a página duas vezes para ler dois
 * campos dela seria desperdício, e quem só quer saber de incorporação ignora o campo.
 */
export async function checkEmbeddable(videoId, { fetchImpl = fetch } = {}) {
  const response = await fetchImpl(`https://www.youtube.com/watch?v=${videoId}`, {
    headers: { 'accept-language': 'pt-BR,pt;q=0.9,en;q=0.8' },
  });
  if (!response.ok) return { known: false, embeddable: null, orientation: null };

  const html = await response.text();
  const orientation = parseOrientation(html);
  const match = html.match(/"playableInEmbed":(true|false)/);
  if (!match) return { known: false, embeddable: null, orientation };
  return { known: true, embeddable: match[1] === 'true', orientation };
}

/**
 * Estado de um vídeo já catalogado, sem lançar exceção: quem verifica dezenas de ids em sequência
 * precisa continuar depois do primeiro problema, e precisa saber *que tipo* de problema foi.
 *
 * `status` é um de:
 *   - `ok`             — público e (até onde deu para saber) incorporável
 *   - `indisponivel`   — removido, privado ou inexistente: exige nova curadoria
 *   - `nao-incorporavel` — existe, mas o autor desativou o embed: proibido usar (D7)
 *   - `indeterminado`  — rede ou YouTube falharam; não é conclusão sobre o vídeo
 */
export async function verificarDisponibilidade(videoId, { fetchImpl = fetch } = {}) {
  let oembed;
  try {
    oembed = await fetchOEmbed(videoId, { fetchImpl });
  } catch (error) {
    if (error instanceof VideoIndisponivelError) {
      return { status: 'indisponivel', motivo: error.message };
    }
    return { status: 'indeterminado', motivo: error.message };
  }

  const title = oembed.title?.trim() ?? null;
  const channel = oembed.author_name?.trim() ?? null;

  // Falha aqui não diz nada sobre o vídeo — o oEmbed já respondeu que ele existe. Tratar como
  // "não deu para saber" evita transformar instabilidade de rede em acusação de violação de D7.
  let embed = { known: false, embeddable: null, orientation: null };
  try {
    embed = await checkEmbeddable(videoId, { fetchImpl });
  } catch {
    embed = { known: false, embeddable: null, orientation: null };
  }

  if (embed.known && embed.embeddable === false) {
    return {
      status: 'nao-incorporavel',
      motivo: 'o autor desativou a incorporação (a política D7 proíbe embutir este vídeo)',
      title,
      channel,
    };
  }

  return { status: 'ok', title, channel, incorporacaoConfirmada: embed.known };
}
