/**
 * Requisitos do próximo grau como checklist pessoal (#196, D78).
 *
 * A lista vem **de fora**: é o que a academia de quem usa entregou, colado pela própria pessoa.
 * Nada aqui traz lista nem requisito de academia nenhuma: material de academia costuma ser
 * protegido, e o repositório é público (D11).
 *
 * Regras puras espelhadas no backend (D17): `parseRequirements` é
 * `GraduationRequirementService.parse` e `requirementStatus` é `statusOf`, com os mesmos casos de
 * teste dos dois lados.
 */

import { searchKey, type SearchableNode, type SearchablePosition } from './posicoes.ts';

export const MAX_REQUIREMENT_LENGTH = 200;

/**
 * Marcador no começo da linha: `•`, `-`, `*`, numeração (`1.`, `2)`, `(3)`) e letra (`a)`, `B.`).
 * Número sem pontuação depois não é marcador ("100kg..." é texto), nem número decimal ("1.5 ...").
 */
const MARCADOR = /^(?:[-*•·–—▪◦►]+\s*|\(?\d{1,3}[.)](?!\d)\s*|\(\d{1,3}\)\s*|[A-Za-z][.)]\s+)/;

/**
 * O texto colado vira requisitos: um por linha, sem linha vazia, sem marcador e com espaço
 * normalizado. Linha acima do teto lança com o número dela, e não é cortada: cortar mudaria o
 * requisito sem a pessoa ver. É a mesma regra do servidor, e serve à tela para mostrar a prévia.
 */
export function parseRequirements(text: string): string[] {
  const itens: string[] = [];
  text.split(/\r?\n|\r/).forEach((bruta, indice) => {
    let linha = bruta.trim();
    let anterior: string;
    do {
      anterior = linha;
      linha = linha.replace(MARCADOR, '').trim();
    } while (linha !== anterior && linha !== '');
    linha = linha.replace(/\s+/g, ' ');
    if (linha === '') return;
    if (linha.length > MAX_REQUIREMENT_LENGTH) {
      throw new Error(
        `A linha ${indice + 1} passa de ${MAX_REQUIREMENT_LENGTH} caracteres: um requisito por linha.`,
      );
    }
    itens.push(linha);
  });
  return itens;
}

export type RequirementStatus = 'PENDENTE' | 'EM_ANDAMENTO' | 'CONCLUIDO';

/**
 * O status de um requisito, derivado — nunca gravado.
 *
 * Lê o progresso **gravado** de cada nó ligado, e não o status exibido: um nó drillado pelo diário
 * mas ainda travado na árvore foi treinado mesmo assim. Sem nó, vale a marcação à mão; com nó, a
 * marcação não conta.
 *
 * @param stored status gravado de cada nó ligado (`null`/`undefined` = nunca tocado)
 */
export function requirementStatus(
  stored: ReadonlyArray<string | null | undefined>,
  manualDone: boolean,
): RequirementStatus {
  if (stored.length === 0) return manualDone ? 'CONCLUIDO' : 'PENDENTE';
  if (stored.every((s) => s === 'COMPLETED')) return 'CONCLUIDO';
  if (stored.some((s) => s === 'COMPLETED' || s === 'IN_PROGRESS')) return 'EM_ANDAMENTO';
  return 'PENDENTE';
}

/** Chave de busca sem pontuação: "kimura:" e "kimura" são a mesma palavra. */
function palavrasDe(texto: string): string {
  return searchKey(texto)
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim();
}

/** Palavras que não ajudam a achar técnica: aparecem em quase todo requisito. */
const PALAVRAS_VAZIAS = new Set([
  'a',
  'as',
  'o',
  'os',
  'de',
  'da',
  'das',
  'do',
  'dos',
  'e',
  'em',
  'na',
  'nas',
  'no',
  'nos',
  'com',
  'sem',
  'para',
  'por',
  'um',
  'uma',
  'ao',
  'aos',
  'saida',
  'saidas',
  'tecnica',
  'tecnicas',
  'posicao',
  'execucao',
  'defesa',
  'ataque',
]);

/**
 * Sugestões de nó para um requisito: os nós cujos termos (código, título, apelidos e a posição de
 * onde partem) mais compartilham palavras com o texto do requisito.
 *
 * É sugestão, e só: a ligação é gravada depois que a pessoa confirma. Por isso a regra é generosa
 * — basta uma palavra significativa em comum —, e é a ordem que separa o provável do possível.
 * Requisito sem técnica correspondente (comportamento, tempo de faixa) não sugere nada.
 */
export function suggestTechniques<N extends SearchableNode>(
  nodes: readonly N[],
  text: string,
  positions: readonly SearchablePosition[] = [],
  limit = 3,
): N[] {
  const palavras = [...new Set(palavrasDe(text).split(' '))].filter(
    (p) => p.length >= 3 && !PALAVRAS_VAZIAS.has(p),
  );
  if (palavras.length === 0) return [];

  const porCodigo = new Map(positions.map((p) => [p.code ?? '', p]));
  const pontuados: Array<{ node: N; pontos: number; indice: number }> = [];
  nodes.forEach((node, indice) => {
    const titulo = palavrasDe(node.title ?? '');
    const apelidos = (node.aliases ?? []).map(palavrasDe);
    const posicao = (node.positionCodes ?? []).flatMap((codigo) => {
      const p = porCodigo.get(codigo);
      return p ? [p.name ?? '', ...(p.aliases ?? [])].map(palavrasDe) : [];
    });
    const termos = ` ${[titulo, ...apelidos].join(' | ')} `;
    const termosDaPosicao = ` ${posicao.join(' | ')} `;
    let pontos = 0;
    for (const palavra of palavras) {
      // Palavra inteira no título ou apelido vale mais que pedaço dela, e a posição vale menos:
      // "kimura" no título diz mais que "100kg" na posição de onde a técnica parte.
      if (termos.includes(` ${palavra} `)) pontos += 3;
      else if (termos.includes(palavra)) pontos += 1;
      if (termosDaPosicao.includes(palavra)) pontos += 1;
    }
    if (pontos > 0) pontuados.push({ node, pontos, indice });
  });
  return pontuados
    .sort((a, b) => b.pontos - a.pontos || a.indice - b.indice)
    .slice(0, limit)
    .map(({ node }) => node);
}
