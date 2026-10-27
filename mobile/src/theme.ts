import { StyleSheet } from 'react-native';

import { cores as tokens, espacoPx, raio, textoPx } from '@fos/tokens';

/**
 * Cores e medidas do app, lidas de `shared/tokens` (D72) — a mesma fonte que gera o `:root` do web.
 * Aqui não entra valor literal de cor: tom novo nasce no pacote, para o app não voltar a parecer
 * dois. Os nomes em português são a API que as telas já usam; o mapa para os tokens é este.
 */
export const cores = {
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

/** Tamanhos em px, derivados da escala em rem do pacote. */
export const texto = textoPx;
export const espaco = espacoPx;
export { raio };

export const estilos = StyleSheet.create({
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
});
