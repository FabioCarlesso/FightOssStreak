/**
 * Linha em branco no `concept` vira parágrafo novo na tela (issue #58); texto sem linha em
 * branco continua um `<p>` só, como sempre foi.
 *
 * Mora aqui, e não na `NodePage`, desde a #47: a sessão de revisão mostra o mesmo conceito, e as
 * duas telas precisam quebrar o texto do mesmo jeito.
 */
export function paragraphsOf(concept: string | undefined): string[] {
  return (concept ?? '')
    .split(/\n{2,}/)
    .map((paragraph) => paragraph.trim())
    .filter((paragraph) => paragraph.length > 0);
}
