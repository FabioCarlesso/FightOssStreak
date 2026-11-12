/**
 * Mapa de posições e busca de técnica (#194, D76).
 *
 * A busca vive aqui, e não numa tela, para o app mobile reaproveitar a mesma régua: digitar
 * "kimura", "side control" ou "meia guarda" tem que achar a mesma técnica no web e no celular.
 *
 * Nada aqui traz posição nem apelido: os dois vêm do currículo versionado (D11), pela árvore.
 */

/** `GUARDA | CONTROLE_POR_CIMA | COSTAS | TRANSICAO | EM_PE`, na ordem da tela. */
export type PositionFamily = 'GUARDA' | 'CONTROLE_POR_CIMA' | 'COSTAS' | 'TRANSICAO' | 'EM_PE';

/** De que lado o nome da posição é dito. */
export type PositionPerspective = 'BAIXO' | 'CIMA' | 'NEUTRA';

export const POSITION_FAMILY_LABELS: ReadonlyArray<{ value: PositionFamily; label: string }> = [
  { value: 'GUARDA', label: 'Guardas' },
  { value: 'CONTROLE_POR_CIMA', label: 'Controle por cima' },
  { value: 'COSTAS', label: 'Costas' },
  { value: 'TRANSICAO', label: 'Transição' },
  { value: 'EM_PE', label: 'Em pé' },
];

export const POSITION_PERSPECTIVE_LABELS: Readonly<Record<PositionPerspective, string>> = {
  BAIXO: 'nome dito por quem está por baixo',
  CIMA: 'nome dito por quem está por cima',
  NEUTRA: 'sem lado',
};

export interface SearchablePosition {
  readonly code?: string;
  readonly name?: string;
  readonly aliases?: readonly string[];
  readonly family?: string;
}

export interface SearchableNode {
  readonly code?: string;
  readonly title?: string;
  readonly positionCodes?: readonly string[];
  readonly aliases?: readonly string[];
}

/**
 * A chave com que termos se comparam: sem acento, sem caixa, hífen valendo espaço e sem espaço
 * sobrando. "Meia-guarda", "meia guarda" e "MEIA GUARDA" são o mesmo termo.
 *
 * Espelhada em `CurriculumValidator.searchKey` (D17), que usa a mesma chave para recusar apelido
 * que levaria a duas posições.
 */
export function searchKey(term: string): string {
  return term
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[-_]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Quão bem o nó casa com a busca: menor é melhor, `null` é não casa.
 *
 * Todas as palavras digitadas precisam aparecer em algum termo do nó (código, título, apelidos e
 * os nomes e apelidos das posições dele). A ordem favorece o que a pessoa provavelmente quis:
 * código, depois título que começa com o digitado, apelido, título que contém, e por último a
 * posição — "meia guarda" acha as técnicas da meia-guarda, mas atrás de um título que diga isso.
 */
function rank(
  node: SearchableNode,
  query: string,
  positionsByCode: ReadonlyMap<string, SearchablePosition>,
): number | null {
  const code = searchKey(node.code ?? '');
  const title = searchKey(node.title ?? '');
  const aliases = (node.aliases ?? []).map(searchKey);
  const positionTerms = (node.positionCodes ?? []).flatMap((positionCode) => {
    const position = positionsByCode.get(positionCode);
    return position ? [position.name ?? '', ...(position.aliases ?? [])].map(searchKey) : [];
  });

  const haystack = [code, title, ...aliases, ...positionTerms].join(' | ');
  const words = query.split(' ');
  if (!words.every((word) => haystack.includes(word))) return null;

  if (code === query || code.startsWith(query)) return 0;
  if (title.startsWith(query)) return 1;
  if (aliases.some((alias) => alias === query || alias.startsWith(query))) return 2;
  if (title.includes(query) || aliases.some((alias) => alias.includes(query))) return 3;
  return 4;
}

export interface TechniqueSearch {
  /** O que foi digitado. Vazio com posição escolhida lista as técnicas da posição. */
  readonly query?: string;
  /** Código de posição: só técnicas que partem dela. */
  readonly position?: string | null;
}

/**
 * Busca de técnica do diário: por código, título e apelido do nó, com filtro por posição.
 *
 * Sem texto e sem posição devolve nada — listar os nós todos é o `<select>` que esta busca
 * substitui. Empate fica na ordem em que os nós vieram, que é a ordem da árvore.
 */
export function searchTechniques<N extends SearchableNode>(
  nodes: readonly N[],
  search: TechniqueSearch,
  positions: readonly SearchablePosition[] = [],
): N[] {
  const query = searchKey(search.query ?? '');
  const position = search.position || null;
  if (!query && !position) return [];

  const positionsByCode = new Map(positions.map((p) => [p.code ?? '', p]));
  const scored: Array<{ node: N; score: number; index: number }> = [];
  nodes.forEach((node, index) => {
    if (position && !(node.positionCodes ?? []).includes(position)) return;
    const score = query ? rank(node, query, positionsByCode) : 0;
    if (score !== null) scored.push({ node, score, index });
  });
  return scored.sort((a, b) => a.score - b.score || a.index - b.index).map(({ node }) => node);
}

/** Os nós que partem de uma posição, na ordem da árvore. */
export function nodesFromPosition<N extends SearchableNode>(
  nodes: readonly N[],
  positionCode: string,
): N[] {
  return nodes.filter((node) => (node.positionCodes ?? []).includes(positionCode));
}

/**
 * As posições agrupadas por família, na ordem de `POSITION_FAMILY_LABELS`, e dentro da família na
 * ordem do currículo. Família sem posição não aparece.
 */
export function groupPositionsByFamily<P extends SearchablePosition>(
  positions: readonly P[],
): Array<{ family: PositionFamily; label: string; positions: P[] }> {
  return POSITION_FAMILY_LABELS.map(({ value, label }) => ({
    family: value,
    label,
    positions: positions.filter((position) => position.family === value),
  })).filter((group) => group.positions.length > 0);
}
