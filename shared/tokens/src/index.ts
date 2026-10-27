/**
 * Tokens visuais do FOS — a fonte única de cor, tipografia, espaçamento e raio para o web e o app
 * (D72, #183). É o mesmo problema que `shared/domain` resolveu para regra de negócio: duas cópias à
 * mão se afastam em silêncio, e o app passa a parecer dois.
 *
 * - O web não importa este arquivo: `npm run gen:tokens` escreve o `:root` de `web/src/tokens.css`
 *   a partir dele, e um teste em `scripts/` reprova o CI se o CSS estiver desatualizado.
 * - O app (`mobile/src/theme.ts`) importa direto: cor como string, tamanho em px.
 * - `fonte` e as sombras compostas (`sombra`) são só do web: o React Native usa a fonte do sistema
 *   (não há família a escolher sem empacotar arquivo de fonte) e não entende `box-shadow` em
 *   string — sombra no app é `shadowColor`/`elevation`, e para isso as cores `shadow*` bastam.
 *
 * TypeScript puro, sem `react` nem `react-native`, e só com sintaxe que o Node apaga sozinho (o
 * gerador importa este arquivo sem compilar). Mudar um valor aqui muda os dois apps.
 *
 * Há dois temas (D74, #187): `cores` é o escuro, o padrão, e `coresClaras` traz só o que muda no
 * claro. O que não acompanha o tema por natureza — faixas, moldura do celular e sombras — está em
 * `CORES_FIXAS`, e o tipo de `coresClaras` obriga toda outra chave de `cores` a ter valor claro: cor
 * nova não entra sem a decisão de como fica no outro tema.
 *
 * As chaves são camelCase; o nome da variável CSS sai de `nomeCss` (`surface2` → `--surface-2`).
 */

/**
 * Cores do tema escuro — o padrão quando o sistema não diz nada (D74). Os nomes de papel são os do
 * web (#182); os ajustes de contraste, da #181.
 */
export const cores = {
  bg: '#0f1115',
  surface: '#171a21',
  surface2: '#1e222b',
  border: '#2a2f3a',
  /** Borda de controle (chip, campo, opção): a decorativa não passava 3:1 contra o fundo (#181). */
  borderControl: '#687284',
  text: '#e8eaf0',
  muted: '#9aa3b2',
  accent: '#e2703a',
  accentHover: '#ef7f47',
  accentSoft: 'rgba(226, 112, 58, 0.15)',
  /** Anel de foco (#186): hoje igual ao `accent`, mas token próprio para foco e marca mudarem
   * separados sem mexer na regra do CSS. */
  focus: '#e2703a',
  /** Texto sobre `accent` (#181): branco sobre o laranja não passava AA. */
  onAccent: '#150a03',
  ok: '#4caf7d',
  okSoft: 'rgba(76, 175, 125, 0.15)',
  warn: '#e46b66',
  warnSoft: 'rgba(228, 107, 102, 0.12)',
  mutedSoft: 'rgba(154, 163, 178, 0.15)',
  caution: '#f2a65a',
  danger: '#c0392b',
  fundoCampo: '#0f1218',
  frozen: '#6aa9e0',
  heat1: 'rgba(76, 175, 125, 0.35)',
  heat2: 'rgba(76, 175, 125, 0.65)',
  beltBranca: '#f2f2f2',
  beltPreta: '#1a1a1a',
  beltAzul: '#2f5fb3',
  beltRoxa: '#6b3fa0',
  phoneFrame: '#22262f',
  scrim: 'rgba(15, 17, 21, 0.72)',
  shadowFaint: 'rgb(0 0 0 / 15%)',
  shadow: 'rgb(0 0 0 / 35%)',
  shadowStrong: 'rgb(0 0 0 / 45%)',
  shadowHeavy: 'rgb(0 0 0 / 70%)',
} as const;

/**
 * Famílias de fonte, só para o web (#186): o React Native usa a fonte do sistema, que é o que a
 * pilha `fontSans` pede ao navegador de qualquer forma.
 */
export const fonte = {
  fontSans: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
  fontMono: 'ui-monospace, SFMono-Regular, Menlo, monospace',
} as const;

/**
 * Sombras compostas do web (#186): deslocamento e desfoque junto com a cor. `device` é a moldura do
 * celular da landing, `print` é o print solto. A cor entra como `var()` da cor de sombra, e não
 * copiada, para acompanhar quem redefinir a cor. Por isso o valor só faz sentido em CSS — o app
 * não usa `box-shadow` (ver o cabeçalho).
 */
export const sombra = {
  shadowDevice: `0 18px 40px var(--${nomeCss('shadowStrong')})`,
  shadowPrint: `0 12px 30px var(--${nomeCss('shadow')})`,
} as const;
export type ChaveDeCor = keyof typeof cores;

/**
 * Cores iguais nos dois temas. Faixa é a cor da faixa de verdade; a moldura é a de um celular, em
 * volta de print do tema escuro; e as sombras escurecem o que está atrás (miniatura de vídeo, a
 * moldura) — nenhuma delas é papel de interface.
 */
export const CORES_FIXAS = [
  'beltBranca',
  'beltPreta',
  'beltAzul',
  'beltRoxa',
  'phoneFrame',
  'shadowFaint',
  'shadow',
  'shadowStrong',
  'shadowHeavy',
] as const satisfies readonly ChaveDeCor[];

/** As chaves que mudam com o tema. */
export type ChaveTematica = Exclude<ChaveDeCor, (typeof CORES_FIXAS)[number]>;

/**
 * Cores do tema claro (D74, #187). Partiu da paleta proposta na issue e passa na mesma régua do
 * escuro — conferida por `contraste.test.ts`, que reprova o `npm test` abaixo do mínimo.
 *
 * Para acrescentar um token de cor que muda com o tema: a chave em `cores` (escuro) e a mesma aqui
 * (o tipo cobra), e o par no `contraste.test.ts` se for texto, borda de controle ou foco.
 */
export const coresClaras = {
  bg: '#f5f6f8',
  surface: '#ffffff',
  surface2: '#eef0f3',
  border: '#d6dae1',
  borderControl: '#7a8395',
  text: '#161a21',
  muted: '#576071',
  accent: '#a04516',
  accentHover: '#8a3a12',
  accentSoft: 'rgba(160, 69, 22, 0.12)',
  focus: '#a04516',
  onAccent: '#ffffff',
  ok: '#1f6e47',
  okSoft: 'rgba(31, 110, 71, 0.12)',
  warn: '#b02f2b',
  warnSoft: 'rgba(176, 47, 43, 0.12)',
  mutedSoft: 'rgba(87, 96, 113, 0.12)',
  caution: '#8a5a00',
  danger: '#a8281b',
  fundoCampo: '#ffffff',
  frozen: '#2a6aa8',
  heat1: 'rgba(31, 110, 71, 0.3)',
  heat2: 'rgba(31, 110, 71, 0.6)',
  scrim: 'rgba(22, 26, 33, 0.45)',
} as const satisfies Record<ChaveTematica, string>;

export type NomeDoTema = 'escuro' | 'claro';

/** Um conjunto completo de cores, com as mesmas chaves nos dois temas. */
export type Cores = { readonly [K in ChaveDeCor]: string };

/** Os dois conjuntos completos, para quem lê cor como valor (o app). */
export const temas: { readonly [T in NomeDoTema]: Cores } = {
  escuro: cores,
  claro: { ...cores, ...coresClaras },
};

/** Escala tipográfica, em rem. O `clamp()` dos títulos da landing fica no CSS. */
export const texto = {
  text2xs: 0.7,
  textXs: 0.8,
  textSm: 0.85,
  textMd: 0.9,
  textBase: 0.95,
  textLg: 1.05,
  textXl: 1.35,
  text2xl: 1.8,
  text3xl: 2.4,
} as const;

/** Espaçamento, em rem. */
export const espaco = {
  space1: 0.25,
  space2: 0.5,
  space3: 0.75,
  space4: 1,
  space5: 1.5,
  space6: 2.5,
} as const;

/** Raio, em px — já é a unidade dos dois lados. */
export const raio = {
  radiusXs: 2,
  radiusSm: 6,
  radiusControl: 8,
  radiusMd: 10,
  radius: 12,
  radiusLg: 28,
  radiusPill: 999,
} as const;

/** Base do rem: a do navegador, que o web não muda. */
export const REM_EM_PX = 16;

/** rem → px inteiro, para o React Native (que não conhece rem). */
export function px(rem: number): number {
  return Math.round(rem * REM_EM_PX);
}

function emPx<T extends Record<string, number>>(escala: T): { readonly [K in keyof T]: number } {
  return Object.fromEntries(Object.entries(escala).map(([k, v]) => [k, px(v)])) as {
    [K in keyof T]: number;
  };
}

/** A escala tipográfica em px, para o app. */
export const textoPx = emPx(texto);

/** O espaçamento em px, para o app. */
export const espacoPx = emPx(espaco);

/** `surface2` → `surface-2`, `text2xs` → `text-2xs`, `beltBranca` → `belt-branca`. */
export function nomeCss(chave: string): string {
  return chave
    .replace(/([a-z])([A-Z0-9])/g, '$1-$2')
    .replace(/([0-9])([A-Z])/g, '$1-$2')
    .toLowerCase();
}

/** Todas as variáveis do `:root` do web — o tema escuro —, na ordem em que aparecem no CSS gerado. */
export function variaveisCss(): Array<[nome: string, valor: string]> {
  const comUnidade = (escala: Record<string, number>, unidade: string) =>
    Object.entries(escala).map(([k, v]): [string, string] => [`--${nomeCss(k)}`, `${v}${unidade}`]);
  return [
    ...Object.entries(cores).map(([k, v]): [string, string] => [`--${nomeCss(k)}`, v]),
    ...Object.entries({ ...fonte, ...sombra }).map(([k, v]): [string, string] => [
      `--${nomeCss(k)}`,
      v,
    ]),
    ...comUnidade(texto, 'rem'),
    ...comUnidade(espaco, 'rem'),
    ...comUnidade(raio, 'px'),
  ];
}

/**
 * As variáveis que o tema claro sobrescreve, na ordem do CSS gerado. O resto (escala, raio e as
 * `CORES_FIXAS`) vem do `:root` e não se repete.
 */
export function variaveisDoTemaClaro(): Array<[nome: string, valor: string]> {
  return Object.entries(coresClaras).map(([k, v]): [string, string] => [`--${nomeCss(k)}`, v]);
}
