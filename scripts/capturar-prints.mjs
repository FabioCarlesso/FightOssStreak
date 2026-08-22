#!/usr/bin/env node
/**
 * Recaptura os prints que a landing exibe, a partir do app rodando de verdade.
 *
 *   node scripts/capturar-prints.mjs --semear
 *
 * Por que existe: a landing (`web/src/pages/LandingPage.tsx`) mostra quatro telas do app, e print é
 * a parte de uma página que apodrece primeiro — mudou o layout da árvore, o print virou mentira, e
 * ninguém percebe porque a imagem continua bonita. Sem um caminho reproduzível para refazer os oito
 * arquivos, a resposta honesta a "mexi na tela, e agora?" seria abrir o app e recortar tela a tela.
 *
 * Como funciona: sobe o Chrome em headless, fala CDP por WebSocket (nativo no Node 22, sem
 * Playwright nem Puppeteer no `package.json` — D29 já recusou navegador de verdade nos testes, e um
 * script de captura não é motivo para trazê-lo pela porta dos fundos) e grava WebP direto do
 * `Page.captureScreenshot`, que aceita o formato e dispensa conversor externo.
 *
 * `--semear` popula o app com progresso de exemplo antes de capturar, porque o print precisa mostrar
 * o app com uso: banco vazio rende uma agenda escrita "nada vencido hoje", que é exatamente o
 * contrário do que a seção afirma. A semeadura só fala com a API pública, sem SQL — o que ela usa é
 * o `drilledOn` do `DrillRequest`, que aceita data passada.
 *
 * Pré-requisitos: backend e web no ar (`npm run dev:backend` e `npm run dev:web`), `google-chrome`
 * no PATH e — desde que o app exige login (#24) — uma sessão já obtida em `FOS_PRINT_COOKIE`. O
 * passo a passo completo está em `docs/10-prints-da-landing.md`.
 *
 * Códigos de saída:
 *   0  todos os prints gravados dentro do teto de tamanho
 *   1  algum print estourou o teto ou uma tela não chegou ao estado esperado
 *   2  não deu para rodar (Chrome ausente, app fora do ar)
 */
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { mkdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const curriculumDir = resolve(root, 'backend/src/main/resources/curriculum');
const saidaPadrao = resolve(root, 'web/public/prints');

/** Teto por arquivo. Serve à landing, não à vaidade: a página inteira carrega antes do primeiro rolar. */
const TETO_BYTES = 150 * 1024;

/**
 * As quatro telas, na ordem em que a landing as apresenta.
 *
 * `esperar` é o seletor que prova que a tela chegou ao estado que a seção afirma — esperar só pelo
 * `load` fotografaria o "Carregando…" em máquina lenta. `.due-list__item` é o mais estrito de
 * propósito: se a semeadura falhar, o print da agenda sai vazio e o script precisa falhar junto.
 */
const TELAS = [
  // A árvore é enquadrada pelo primeiro nó travado, e não pelo topo da página: o topo mostra só nós
  // concluídos, e a seção que exibe este print afirma justamente que o nó seguinte fica fechado.
  {
    nome: 'arvore',
    rota: '/arvore',
    esperar: '.node-row--completed',
    rolarAte: '.node-row--locked',
    alinhar: 'end',
    ajuste: 80,
  },
  // O único enquadrado de forma diferente em cada largura, e por necessidade. A seção que exibe
  // este print promete conceito + vídeo com o canal creditado (D7), e em 1280px isso cabe a partir
  // do topo. Em 390px não cabe mais: desde que a anotação fixada entrou no card do conceito (#45),
  // o que sobrava de espaço acabou, e a página vista do topo termina antes do player. Ancorar no
  // vídeo resolve o celular e estragaria o desktop, onde o player tem 442px de altura e empurraria
  // conceito e título para fora — daí o override em vez de um enquadramento só.
  {
    nome: 'no',
    rota: '/no/M1.3',
    esperar: '.node__concept',
    porFormato: {
      // `end` + `ajuste` passam um pouco do fim do `<figure>`, que é onde vive o crédito ao canal.
      mobile: { rolarAte: '.video', alinhar: 'end', ajuste: 40 },
    },
  },
  // O ajuste sobe o enquadramento para o formulário não ficar colado na borda de baixo. Abaixo
  // dele a página segue com o histórico de anotações (#45), que entra no print de brinde: é o
  // destino do que se escreve no campo fotografado aqui.
  { nome: 'drill', rota: '/no/M1.3', esperar: '.drill', rolarAte: '.drill', ajuste: -160 },
  { nome: 'hoje', rota: '/hoje', esperar: '.due-list__item' },
];

/**
 * Desktop em 1x e celular em 2x.
 *
 * O celular é o alvo real de uso, e print de 1280px de largura não se lê em tela de 390px — daí os
 * dois tamanhos. O 2x do celular cabe no teto porque 390×844 é uma área pequena; o desktop em 2x
 * passaria de 2560px de largura, que é maior do que a landing chega a exibir.
 */
const FORMATOS = [
  { sufixo: 'desktop', width: 1280, height: 800, deviceScaleFactor: 1, mobile: false },
  { sufixo: 'mobile', width: 390, height: 844, deviceScaleFactor: 2, mobile: true },
];

/** Imagem de prévia de link. JPEG, e não WebP: é o formato que todo rastreador aceita sem susto. */
const OG = {
  nome: 'og',
  rota: '/?ver=apresentacao',
  esperar: '.landing__hero',
  width: 1200,
  height: 630,
  deviceScaleFactor: 1,
  mobile: false,
  formato: 'jpeg',
  qualidade: 88,
};

const QUALIDADE_WEBP = 82;

/**
 * Progresso de exemplo.
 *
 * Desenhado para que cada print mostre o que a seção dele afirma: oito nós concluídos deixam a
 * árvore com concluído, disponível e travado na mesma tela; os drills em dias consecutivos rendem
 * streak de verdade; e os intervalos do SRS foram escolhidos para vencer quatro nós hoje, com
 * atrasos diferentes, que é o que faz a agenda parecer uma agenda.
 */
const NOS_A_CONCLUIR = ['M0.1', 'M0.2', 'M0.3', 'M0.4', 'M0.5', 'M1.1', 'M1.2', 'M1.3'];

const DRILLS = [
  { no: 'M0.1', diasAtras: 7, recall: 'OK' },
  { no: 'M0.2', diasAtras: 6, recall: 'OK' },
  { no: 'M0.3', diasAtras: 5, recall: 'HARD', nota: 'Ainda erro o tempo do quadril na saída.' },
  { no: 'M0.4', diasAtras: 4, recall: 'OK' },
  { no: 'M0.5', diasAtras: 3, recall: 'OK' },
  { no: 'M1.1', diasAtras: 3, recall: 'EASY' },
  { no: 'M1.2', diasAtras: 2, recall: 'OK' },
  { no: 'M0.1', diasAtras: 2, recall: 'EASY' },
  { no: 'M1.3', diasAtras: 1, recall: 'OK', nota: 'Cotovelo colado antes de girar — funcionou.' },
  { no: 'M0.2', diasAtras: 1, recall: 'EASY' },
  { no: 'M0.3', diasAtras: 0, recall: 'OK' },
];

/**
 * Anotação fixada de exemplo (#45).
 *
 * Só em M1.3, que é o nó fotografado. O print precisa mostrar o bloco preenchido e não o convite
 * "Anotar": o estado vazio é honesto no app, mas fotografado vira uma tela que não exercita o
 * recurso — do mesmo jeito que uma agenda escrita "nada vencido hoje" não serve para ilustrar a
 * agenda.
 */
const NOTAS_FIXADAS = [
  {
    no: 'M1.3',
    nota: 'O professor insiste: joelho passa antes do pé, senão o quadril não acompanha.',
  },
];

/** Um quiz refeito depois de aprovado — é o que acende a quarta métrica de docs/05. */
const QUIZ_REFEITO = 'M0.2';

const dormir = (ms) => new Promise((ok) => setTimeout(ok, ms));

/**
 * Sessão já obtida, passada de fora por `FOS_PRINT_COOKIE`.
 *
 * Desde a #24 o app exige login (D36/D37), e este script não tem como se autenticar sozinho: ele
 * semeia por `fetch` e sobe um Chrome com perfil novo, e os dois levavam 401. O caminho que
 * `docs/10-prints-da-landing.md` já prescrevia é este — **receber** uma sessão de verdade, obtida
 * por quem opera, em vez de um modo que desliga o portão para tirar foto, que seria porta dos
 * fundos permanente para economizar oito imagens.
 *
 * O valor é o cabeçalho `Cookie` inteiro, como o navegador o manda:
 * `FOS_PRINT_COOKIE='JSESSIONID=...; XSRF-TOKEN=...'`. Sem a variável nada muda — o script segue
 * como antes, e falha em 401 se o app exigir sessão.
 */
const cookieSessao = process.env.FOS_PRINT_COOKIE?.trim() || null;

/** Um valor específico de dentro do `Cookie` — o CSRF precisa ir também como cabeçalho próprio. */
function valorDoCookie(nome) {
  const achado = (cookieSessao ?? '')
    .split(';')
    .map((parte) => parte.trim())
    .find((parte) => parte.startsWith(`${nome}=`));
  return achado ? achado.slice(nome.length + 1) : null;
}

function argumento(nome, padrao) {
  const prefixo = `--${nome}=`;
  const encontrado = process.argv.find((arg) => arg.startsWith(prefixo));
  return encontrado ? encontrado.slice(prefixo.length) : padrao;
}

// ---------------------------------------------------------------------------
// Semeadura pela API pública
// ---------------------------------------------------------------------------

/**
 * Data local, e não `toISOString()`.
 *
 * O backend recusa drill em data futura e decide "hoje" pelo fuso do servidor (`TZ`, que o deploy
 * fixa em America/Sao_Paulo — ver README). `toISOString()` devolve UTC: entre 21h e a meia-noite
 * de Brasília ele já está no dia seguinte, e a semeadura morria em 400 dizendo "data futura" —
 * falha que só aparecia à noite, que é o pior tipo de intermitência para depurar.
 */
function hojeMenos(dias) {
  const data = new Date();
  data.setDate(data.getDate() - dias);
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}

async function pedir(api, caminho, init) {
  // `withHttpOnlyFalse()` no backend: o token de CSRF viaja no cookie e é cobrado no cabeçalho.
  const xsrf = valorDoCookie('XSRF-TOKEN');
  const resposta = await fetch(`${api}${caminho}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(cookieSessao && { Cookie: cookieSessao }),
      ...(xsrf && { 'X-XSRF-TOKEN': decodeURIComponent(xsrf) }),
      ...(init?.headers ?? {}),
    },
  });
  if (!resposta.ok) {
    // O corpo junto porque só o status é opaco: 400 pode ser payload inválido e 401 pode ser
    // `FOS_PRINT_COOKIE` ausente ou vencido, e a diferença é o que diz o que fazer a seguir.
    const corpo = await resposta.text().catch(() => '');
    throw new Error(
      `${init?.method ?? 'GET'} ${caminho} devolveu ${resposta.status}${corpo ? ` — ${corpo.slice(0, 300)}` : ''}`,
    );
  }
  return resposta.json();
}

/**
 * Gabarito lido do currículo versionado.
 *
 * A API serve as alternativas embaralhadas e sem dizer qual é a correta (D16) — é o comportamento
 * certo. Quem sabe a resposta é o dado de onde ela veio, e é de lá que o script a lê: no JSON a
 * correta é sempre a primeira, e o casamento com o que a API serviu é por texto.
 */
async function lerGabarito() {
  const indice = JSON.parse(await readFile(join(curriculumDir, 'modules.json'), 'utf8'));
  const gabarito = new Map();
  for (const { file } of indice.modules) {
    const modulo = JSON.parse(await readFile(join(curriculumDir, file), 'utf8'));
    for (const no of modulo.nodes ?? []) {
      const respostas = new Map();
      for (const pergunta of no.quiz ?? []) {
        const correta = pergunta.options.find((opcao) => opcao.correct);
        if (correta) respostas.set(pergunta.prompt, correta.text);
      }
      if (respostas.size > 0) gabarito.set(no.code, respostas);
    }
  }
  return gabarito;
}

async function responderQuiz(api, codigo, gabarito) {
  const detalhe = await pedir(api, `/api/nodes/${codigo}`);
  const respostas = gabarito.get(codigo);
  if (!respostas || (detalhe.quiz ?? []).length === 0) {
    throw new Error(`${codigo} não tem quiz para responder`);
  }

  const answers = detalhe.quiz.map((pergunta) => {
    const textoCorreto = respostas.get(pergunta.prompt);
    const opcao = pergunta.options.find((o) => o.label === textoCorreto);
    if (!opcao) throw new Error(`gabarito de ${codigo} não bate com a API: "${pergunta.prompt}"`);
    return { questionId: pergunta.id, optionId: opcao.id };
  });

  const resultado = await pedir(api, `/api/nodes/${codigo}/quiz`, {
    method: 'POST',
    body: JSON.stringify({ answers }),
  });
  if (!resultado.passed) throw new Error(`quiz de ${codigo} não passou: ${resultado.score}`);
  return resultado;
}

async function semear(api) {
  if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:|\/|$)/.test(api)) {
    throw new Error(`--semear só roda contra API local; recebi ${api}`);
  }

  // Semear duas vezes no mesmo banco empilharia drills e mudaria a agenda — o `dev` usa H2 em
  // memória, então repetir a captura depois de reiniciar o backend é o caminho normal.
  const arvore = await pedir(api, '/api/curriculum/tree');
  if (arvore.summary.completedNodes >= NOS_A_CONCLUIR.length) {
    console.log('já semeado: pulando (reinicie o backend para começar do zero)');
    return;
  }

  const disclaimer = await pedir(api, '/api/disclaimer');
  if (!disclaimer.accepted) {
    await pedir(api, '/api/disclaimer/accept', {
      method: 'POST',
      body: JSON.stringify({ version: disclaimer.currentVersion }),
    });
  }

  const gabarito = await lerGabarito();
  for (const codigo of NOS_A_CONCLUIR) {
    await responderQuiz(api, codigo, gabarito);
  }
  await responderQuiz(api, QUIZ_REFEITO, gabarito);

  // Em ordem cronológica: o SRS reagenda a partir da data do drill, então fora de ordem o intervalo
  // sai de um estado que ainda não existia.
  for (const drill of DRILLS) {
    await pedir(api, `/api/nodes/${drill.no}/drill`, {
      method: 'POST',
      body: JSON.stringify({
        recall: drill.recall,
        note: drill.nota ?? null,
        drilledOn: hojeMenos(drill.diasAtras),
      }),
    });
  }

  for (const { no, nota } of NOTAS_FIXADAS) {
    await pedir(api, `/api/nodes/${no}/note`, {
      method: 'PUT',
      body: JSON.stringify({ note: nota }),
    });
  }

  const agenda = await pedir(api, '/api/reviews/today');
  console.log(
    `semeado: ${NOS_A_CONCLUIR.length} nós concluídos, ${DRILLS.length} drills, ` +
      `${NOTAS_FIXADAS.length} anotação fixada, ${agenda.dueCount} na agenda de hoje`,
  );
  if (agenda.dueCount === 0) {
    throw new Error('a agenda ficou vazia — o print de "revise hoje" sairia sem conteúdo');
  }
}

// ---------------------------------------------------------------------------
// Chrome headless por CDP
// ---------------------------------------------------------------------------

async function abrirChrome() {
  const perfil = join(tmpdir(), `fos-prints-${process.pid}`);
  const chrome = spawn(
    'google-chrome',
    [
      '--headless=new',
      '--disable-gpu',
      '--hide-scrollbars',
      '--no-first-run',
      '--no-default-browser-check',
      '--remote-debugging-port=0',
      `--user-data-dir=${perfil}`,
      'about:blank',
    ],
    { stdio: 'ignore' },
  );
  chrome.on('error', () => {});

  const arquivoPorta = join(perfil, 'DevToolsActivePort');
  for (let tentativa = 0; tentativa < 100; tentativa++) {
    if (existsSync(arquivoPorta)) {
      const [porta] = (await readFile(arquivoPorta, 'utf8')).split('\n');
      const versao = await fetch(`http://127.0.0.1:${porta}/json/version`).then((r) => r.json());
      return { chrome, perfil, wsUrl: versao.webSocketDebuggerUrl };
    }
    await dormir(100);
  }
  chrome.kill();
  throw new Error('Chrome não abriu a porta de depuração');
}

async function conectar(wsUrl) {
  const ws = new WebSocket(wsUrl);
  await new Promise((ok, erro) => {
    ws.addEventListener('open', ok, { once: true });
    ws.addEventListener('error', () => erro(new Error('falha ao conectar no Chrome')), {
      once: true,
    });
  });

  let proximoId = 1;
  const pendentes = new Map();

  ws.addEventListener('message', (evento) => {
    const msg = JSON.parse(evento.data);
    const aguardando = pendentes.get(msg.id);
    if (!aguardando) return;
    pendentes.delete(msg.id);
    if (msg.error) aguardando.erro(new Error(msg.error.message));
    else aguardando.ok(msg.result);
  });

  return {
    enviar(metodo, params = {}, sessionId) {
      const id = proximoId++;
      return new Promise((ok, erro) => {
        pendentes.set(id, { ok, erro });
        ws.send(JSON.stringify({ id, method: metodo, params, ...(sessionId && { sessionId }) }));
      });
    },
    fechar: () => ws.close(),
  };
}

async function avaliar(cdp, sessao, expressao) {
  const { result } = await cdp.enviar(
    'Runtime.evaluate',
    { expression: expressao, returnByValue: true, awaitPromise: true },
    sessao,
  );
  return result.value;
}

/**
 * Espera o seletor aparecer, e não o `load` da página.
 *
 * A tela chega em duas etapas — o HTML e depois a resposta da API — e o `load` acontece entre as
 * duas. Fotografar ali renderia "Carregando…" em toda máquina mais lenta que a de quem escreveu
 * isto, e o print continuaria parecendo válido.
 */
async function esperarSeletor(cdp, sessao, seletor, limiteMs = 20000) {
  const inicio = Date.now();
  for (;;) {
    if (await avaliar(cdp, sessao, `!!document.querySelector(${JSON.stringify(seletor)})`)) return;
    if (Date.now() - inicio > limiteMs) {
      throw new Error(`"${seletor}" não apareceu em ${limiteMs}ms`);
    }
    await dormir(150);
  }
}

/** Põe a sessão recebida dentro do Chrome, que sobe com perfil limpo e sem cookie nenhum. */
async function aplicarSessao(cdp, sessionId, base) {
  if (!cookieSessao) return;
  await cdp.enviar('Network.enable', {}, sessionId);
  for (const parte of cookieSessao.split(';')) {
    const igual = parte.indexOf('=');
    if (igual < 1) continue;
    const name = parte.slice(0, igual).trim();
    const value = parte.slice(igual + 1).trim();
    await cdp.enviar('Network.setCookie', { url: base, name, value }, sessionId);
  }
}

/**
 * `porFormato` permite que uma tela seja enquadrada de um jeito no desktop e de outro no celular.
 * Existe porque 1280×800 e 390×844 não cabem a mesma quantidade de página, e forçar um
 * enquadramento só significaria quebrar a promessa da seção em uma das duas larguras.
 */
async function capturar(cdp, base, telaBase, formato, saida) {
  const tela = { ...telaBase, ...(telaBase.porFormato?.[formato.sufixo] ?? {}) };
  const { targetId } = await cdp.enviar('Target.createTarget', { url: 'about:blank' });
  const { sessionId } = await cdp.enviar('Target.attachToTarget', { targetId, flatten: true });

  try {
    await cdp.enviar('Page.enable', {}, sessionId);
    // Antes de navegar: sem os cookies no lugar, a primeira requisição já volta 401 e a tela
    // fotografada seria a de login.
    await aplicarSessao(cdp, sessionId, base);
    await cdp.enviar(
      'Emulation.setDeviceMetricsOverride',
      {
        width: formato.width,
        height: formato.height,
        deviceScaleFactor: formato.deviceScaleFactor,
        mobile: formato.mobile,
      },
      sessionId,
    );

    await cdp.enviar('Page.navigate', { url: `${base}${tela.rota}` }, sessionId);
    await esperarSeletor(cdp, sessionId, tela.esperar);

    if (tela.rolarAte) {
      const alinhar = tela.alinhar ?? 'start';
      const ajuste = tela.ajuste ?? 0;
      await avaliar(
        cdp,
        sessionId,
        `document.querySelector(${JSON.stringify(tela.rolarAte)}).scrollIntoView({ block: '${alinhar}' }); window.scrollBy(0, ${ajuste}); 'ok'`,
      );
    }

    // Respiro para o que o seletor não cobre: miniatura de clipe, iframe do YouTube e fontes.
    await avaliar(cdp, sessionId, 'document.fonts.ready.then(() => "ok")');
    await dormir(1500);

    const ehJpeg = tela.formato === 'jpeg';
    const { data } = await cdp.enviar(
      'Page.captureScreenshot',
      {
        format: ehJpeg ? 'jpeg' : 'webp',
        quality: tela.qualidade ?? QUALIDADE_WEBP,
        captureBeyondViewport: false,
      },
      sessionId,
    );

    const nome = formato.sufixo ? `${tela.nome}-${formato.sufixo}` : tela.nome;
    const arquivo = join(saida, `${nome}.${ehJpeg ? 'jpg' : 'webp'}`);
    await writeFile(arquivo, Buffer.from(data, 'base64'));
    return arquivo;
  } finally {
    await cdp.enviar('Target.closeTarget', { targetId });
  }
}

// ---------------------------------------------------------------------------

async function main() {
  const base = argumento('web', 'http://localhost:5173').replace(/\/$/, '');
  const api = argumento('api', 'http://localhost:8080').replace(/\/$/, '');
  const saida = resolve(argumento('saida', saidaPadrao));

  if (process.argv.includes('--semear')) {
    await semear(api);
  }

  await mkdir(saida, { recursive: true });
  const { chrome, perfil, wsUrl } = await abrirChrome();
  const cdp = await conectar(wsUrl);

  const filtro = argumento('tela', null);
  const telas = filtro ? TELAS.filter((t) => t.nome === filtro) : TELAS;

  const gravados = [];
  try {
    for (const tela of telas) {
      for (const formato of FORMATOS) {
        gravados.push(await capturar(cdp, base, tela, formato, saida));
      }
    }
    if (!filtro || filtro === OG.nome) {
      gravados.push(await capturar(cdp, base, OG, { ...OG, sufixo: '' }, saida));
    }
  } finally {
    cdp.fechar();
    chrome.kill();
    // Esperar o processo morrer antes de apagar o perfil: o Chrome ainda grava enquanto encerra, e
    // remover a pasta debaixo dele devolve ENOTEMPTY. Se mesmo assim sobrar lixo, é lixo em /tmp —
    // não é motivo para perder os prints que já foram gravados.
    await Promise.race([new Promise((ok) => chrome.once('exit', ok)), dormir(3000)]);
    await rm(perfil, { recursive: true, force: true }).catch(() => {});
  }

  let estourou = false;
  for (const arquivo of gravados) {
    const { size } = await stat(arquivo);
    const acima = size > TETO_BYTES;
    estourou ||= acima;
    console.log(
      `${acima ? '✗' : '✓'} ${arquivo.replace(`${root}/`, '')} — ${(size / 1024).toFixed(0)} KB`,
    );
  }

  if (estourou) {
    console.error(`\nalgum print passou de ${TETO_BYTES / 1024} KB: baixe QUALIDADE_WEBP e repita`);
    return 1;
  }
  console.log(`\n${gravados.length} arquivos gravados em ${saida.replace(`${root}/`, '')}`);
  return 0;
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  main()
    .then((codigo) => process.exit(codigo))
    .catch((error) => {
      console.error(`erro: ${error.message}`);
      process.exit(2);
    });
}
