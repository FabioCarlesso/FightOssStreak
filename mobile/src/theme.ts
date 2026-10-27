import { StyleSheet } from 'react-native';

import { espacoPx, raio, temas, textoPx, type Cores as Tokens, type NomeDoTema } from '@fos/tokens';

export type { NomeDoTema };

/**
 * Cores e medidas do app, lidas de `shared/tokens` (D72) — a mesma fonte que gera o `:root` do web.
 * Aqui não entra valor literal de cor: tom novo nasce no pacote, para o app não voltar a parecer
 * dois. Os nomes em português são a API que as telas já usam; o mapa para os tokens é este.
 *
 * Há dois temas (D74, #187). Medida não muda com o tema e continua estática (`texto`, `espaco`,
 * `raio`); cor e estilo com cor vêm do tema em uso, pelo `useTema()` de `state/tema.tsx`. Por isso
 * não há mais `cores` nem `estilos` exportados daqui: um import estático congelaria o escuro.
 */
function mapear(tokens: Tokens) {
  return {
    fundo: tokens.bg,
    cartao: tokens.surface,
    borda: tokens.border,
    /** Borda de chip e campo: a decorativa some contra o fundo (#181). */
    bordaControle: tokens.borderControl,
    fundoCampo: tokens.fundoCampo,
    texto: tokens.text,
    textoFraco: tokens.muted,
    /** Placeholder. Era um cinza próprio, mais escuro, que não passava AA; agora é o `muted`. */
    dica: tokens.muted,
    destaque: tokens.accent,
    /** Texto sobre `destaque` (#181). */
    sobreDestaque: tokens.onAccent,
    aviso: tokens.caution,
    erro: tokens.warn,
    ok: tokens.ok,
    perigo: tokens.danger,
    gelo: tokens.frozen,
    /** Heatmap: vazio, níveis 1 e 2, e o máximo em `ok` — a mesma escada do web. */
    calor: [tokens.surface2, tokens.heat1, tokens.heat2, tokens.ok] as const,
  };
}

export type Cores = ReturnType<typeof mapear>;

/** As cores de cada tema, com os nomes das telas. */
export const paletas: { readonly [T in NomeDoTema]: Cores } = {
  escuro: mapear(temas.escuro),
  claro: mapear(temas.claro),
};

/** Tamanhos em px, derivados da escala em rem do pacote. */
export const texto = textoPx;
export const espaco = espacoPx;
export { raio };

/**
 * Estilos que dependem de cor. A fábrica roda uma vez por tema, na primeira vez que ele aparece,
 * e o resultado fica guardado: trocar de tema não recria `StyleSheet` a cada render.
 */
export function estilosPorTema<T>(fabrica: (cores: Cores) => T): (tema: NomeDoTema) => T {
  const guardados: Partial<Record<NomeDoTema, T>> = {};
  return (tema) => (guardados[tema] ??= fabrica(paletas[tema]));
}

/** Os estilos comuns a todas as telas (`useTema().estilos`). */
export const estilosBase = estilosPorTema((cores) =>
  StyleSheet.create({
    tela: {
      flexGrow: 1,
      backgroundColor: cores.fundo,
      padding: espaco.space4,
      gap: espaco.space3,
    },
    cartao: {
      backgroundColor: cores.cartao,
      borderColor: cores.borda,
      borderWidth: 1,
      borderRadius: raio.radius,
      padding: espaco.space4,
      gap: espaco.space2,
    },
    titulo: { color: cores.texto, fontSize: texto.textXl, fontWeight: '700' },
    subtitulo: { color: cores.texto, fontSize: texto.textLg, fontWeight: '600' },
    corpo: { color: cores.texto, fontSize: texto.textBase, lineHeight: 21 },
    dica: { color: cores.textoFraco, fontSize: texto.textXs, lineHeight: 18 },
    erro: { color: cores.erro, fontSize: texto.textSm },
    codigo: { color: cores.destaque, fontWeight: '700' },
  }),
);

export type Estilos = ReturnType<typeof estilosBase>;
