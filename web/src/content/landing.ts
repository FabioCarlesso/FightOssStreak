/**
 * Conteúdo da landing pública.
 *
 * Fica separado do componente por dois motivos. O primeiro é que copy se revisa lendo texto, não
 * lendo JSX. O segundo é que os números aqui são afirmações sobre o currículo versionado, e
 * `landing.test.ts` os confere contra `backend/src/main/resources/curriculum/` — texto de venda que
 * envelhece sozinho vira mentira, e esta página existe justamente para ser honesta sobre o estado
 * do projeto.
 */

export const REPO_URL = 'https://github.com/FabioCarlesso/FightOssStreak';
export const DOCS_URL = `${REPO_URL}/tree/main/docs`;
export const DISCLAIMER_DOC_URL = `${REPO_URL}/blob/main/docs/06-disclaimer-responsabilidade.md`;

/** Rota de entrada do app. A landing é a única tela pública; daqui para dentro passa pelo aceite. */
export const APP_PATH = '/hoje';

/**
 * Acesso demonstrativo (#62): o degrau que faltava antes do portão.
 *
 * Print não mostra o que o app faz — o produto é o loop de revisão, e isso não cabe em imagem.
 * Este botão abre o app carregado, numa conta temporária onde dá para gravar de verdade.
 *
 * O texto diz as três coisas que a pessoa precisa saber antes de clicar, e nessa ordem: é
 * temporária, os dados são de exemplo, e some sozinha. As duas horas são o `VALIDADE` do
 * `DemoAccessService` — mexeu lá, mexa aqui.
 */
export const DEMO = {
  cta: 'Ver o app funcionando',
  ctaCarregando: 'Abrindo…',
  nota:
    'A demonstração abre uma conta temporária com progresso de exemplo: dá para responder o' +
    ' quiz, registrar drill e ver a revisão ser reagendada, gravando de verdade. Ela não pede' +
    ' e-mail nenhum e some sozinha em duas horas.',
} as const;

/**
 * Números citados na copy. Todos verificáveis no currículo — ver o teste ao lado.
 *
 * `videoModules` é a parte incômoda e fica à mostra de propósito: dizer "43 vídeos" sem dizer que
 * eles cobrem 8 dos 9 módulos seria escolher o número que favorece — M8 segue sem vídeo, e o item
 * de roadmap continua `done: false` por isso. `quizModules` já cobre o currículo inteiro desde a
 * #59 (D44) — banco de 8 perguntas por nó, com rotação a cada revisão.
 */
export const NUMEROS = {
  nodes: 46,
  modules: 9,
  quizQuestions: 368,
  quizNodes: 46,
  quizModules: 'M0 a M8',
  canonicalVideos: 43,
  videoModules: 'M0 a M7',
  extraClips: 7,
} as const;

/** Dimensões de captura, iguais para todos os prints (ver scripts/capturar-prints.mjs). */
export const PRINT_DESKTOP = { width: 1280, height: 800 } as const;
export const PRINT_MOBILE = { width: 780, height: 1688 } as const;

export interface Print {
  /** 1280×800. É a origem padrão do `<picture>`, e a que o `<img>` de fallback carrega. */
  readonly desktop: string;
  /** 390×844 em 2x. Serve telas estreitas: print de desktop em celular não se lê. */
  readonly mobile: string;
  readonly alt: string;
}

/**
 * Os quatro prints, nomeados: o hero reaproveita o da agenda, e caminho repetido em dois lugares é
 * caminho que um dia diverge.
 */
export const PRINT_ARVORE: Print = {
  desktop: '/prints/arvore-desktop.webp',
  mobile: '/prints/arvore-mobile.webp',
  alt: 'Árvore do currículo nos módulos M0 e M1: nós concluídos marcados com visto verde, um nó disponível e o seguinte travado com o aviso de quais pré-requisitos faltam.',
};

export const PRINT_NO: Print = {
  desktop: '/prints/no-desktop.webp',
  mobile: '/prints/no-mobile.webp',
  alt: 'Tela de um nó do currículo, com o conceito escrito, a anotação pessoal sobre a técnica, o vídeo do YouTube incorporado e o crédito ao canal.',
};

export const PRINT_DRILL: Print = {
  desktop: '/prints/drill-desktop.webp',
  mobile: '/prints/drill-mobile.webp',
  alt: 'Formulário de registro de drill com as quatro opções de recall e a previsão de quando o nó volta para revisão, seguido do histórico de anotações do nó.',
};

export const PRINT_HOJE: Print = {
  desktop: '/prints/hoje-desktop.webp',
  mobile: '/prints/hoje-mobile.webp',
  alt: 'Tela inicial com o streak em dias e a agenda "Revise hoje", listando os nós vencidos e há quantos dias cada um está atrasado.',
};

export interface Step {
  readonly title: string;
  readonly text: string;
  readonly print: Print;
}

/**
 * Como funciona, em quatro passos — a espinha da página.
 *
 * Cada título é uma frase declarativa, não um rótulo: "Uma árvore, não uma playlist" já entrega o
 * argumento para quem só passa o olho, e "Currículo" não entregaria nada.
 */
export const STEPS: readonly Step[] = [
  {
    title: 'Uma árvore, não uma playlist',
    text: `Os ${NUMEROS.nodes} nós vêm na ordem em que o tatame cobra: sobrevivência antes de ataque, fuga de montada antes de armlock. Um nó só abre quando os pré-requisitos dele fecham — então a próxima coisa a estudar nunca é uma decisão sua.`,
    print: PRINT_ARVORE,
  },
  {
    title: 'Cada nó cobra o conceito',
    text: 'Conceito escrito, vídeo de referência creditado ao canal e um quiz corrigido no servidor. O quiz não é prova: o que retém é a explicação que aparece depois de cada resposta, inclusive quando você acerta.',
    print: PRINT_NO,
  },
  {
    title: 'Registrar o treino leva um clique',
    text: 'Marcou "treinei isso hoje" e disse como foi o recall — de "não lembrei" a "saiu fácil". É esse clique que alimenta o streak e que decide quando a técnica volta.',
    print: PRINT_DRILL,
  },
  {
    title: 'E o app diz o que revisar hoje',
    text: 'A repetição espaçada devolve cada nó pouco antes de ele ser esquecido. Você abre o app e já tem a lista do que levar para o treino, do mais atrasado para o menos.',
    print: PRINT_HOJE,
  },
];

export interface Feature {
  /** O que resolve. Vem primeiro de propósito — o mecanismo sozinho não convence ninguém. */
  readonly title: string;
  readonly text: string;
}

export const FEATURES: readonly Feature[] = [
  {
    title: 'Você sempre sabe o que estudar depois',
    text: `Currículo de ${NUMEROS.nodes} nós em ${NUMEROS.modules} módulos, com desbloqueio progressivo e o motivo do bloqueio à vista.`,
  },
  {
    title: 'O conceito fica, não só a sequência',
    text: `${NUMEROS.quizQuestions} perguntas conceituais em ${NUMEROS.quizNodes} nós, corrigidas no servidor e com explicação em cada alternativa.`,
  },
  {
    title: 'A técnica volta antes de você esquecer',
    text: 'Repetição espaçada (SM-2 adaptado) reagenda cada nó a partir do seu próprio recall.',
  },
  {
    title: 'Dá para ver se o hábito sobreviveu',
    text: 'Streak, recorde e dias ativos nos últimos 30 — ao lado da meta, não soltos.',
  },
  {
    title: 'O vídeo é do canal que ensinou',
    text: `${NUMEROS.canonicalVideos} nós com vídeo de referência por embed do YouTube, sempre com crédito ao canal.`,
  },
  {
    title: 'E os clipes são do seu tatame',
    text: `${NUMEROS.extraClips} clipes curtos da própria academia: o canônico ensina, o clipe lembra do dia em que aquilo caiu na aula.`,
  },
  {
    title: 'Inspecionar tudo sem sujar o progresso',
    text: 'O modo demonstração percorre os nós travados para revisão de conteúdo, e não grava nada.',
  },
  {
    title: 'As metas do projeto ficam à mostra',
    text: 'A tela de progresso mede os quatro critérios de sucesso do MVP sobre o uso real, com meta declarada.',
  },
];

/**
 * A objeção antecipada vira seção.
 *
 * Isto não é modéstia: a D1 é o que define o produto, e uma landing que deixasse "app de jiu-jitsu"
 * no ar estaria vendendo outra coisa. De quebra, é a aterrissagem natural do aviso logo abaixo.
 */
export const NAO_E: readonly Feature[] = [
  {
    title: 'Não ensina jiu-jitsu',
    text: 'Habilidade motora se constrói drilando e rolando, com professor corrigindo. Nenhum quiz faz isso — e este aqui nem tenta.',
  },
  {
    title: 'Não é diário de treino',
    text: 'O registro existe para alimentar a revisão. Se a meta fosse anotar o que você fez, bastava um caderno.',
  },
  {
    title: 'Não substitui a academia',
    text: 'É camada de revisão em cima da aula presencial. Sem a aula, não sobra nada para revisar.',
  },
  {
    title: 'Não tem conta, anúncio nem venda',
    text: 'Projeto pessoal, código aberto sob licença MIT. Não há cadastro, cobrança nem rastreamento.',
  },
];

export interface StatusItem {
  readonly label: string;
  readonly done: boolean;
}

/** Estado real, incluindo o que falta. Ver a tabela do README, que é a mesma verdade. */
export const STATUS: readonly StatusItem[] = [
  { label: `Currículo completo: ${NUMEROS.nodes} nós em ${NUMEROS.modules} módulos`, done: true },
  { label: 'Web ponta a ponta: árvore, quiz, drill, streak e agenda de revisão', done: true },
  {
    label: `Banco de quiz completo: ${NUMEROS.quizQuestions} perguntas em ${NUMEROS.quizModules}, ${NUMEROS.quizNodes} de ${NUMEROS.nodes} nós`,
    done: true,
  },
  {
    label: `Vídeo catalogado em ${NUMEROS.videoModules} — ${NUMEROS.canonicalVideos} de ${NUMEROS.nodes} nós`,
    done: false,
  },
  { label: 'App mobile: não começou, e só entra depois do MVP validado', done: false },
];
