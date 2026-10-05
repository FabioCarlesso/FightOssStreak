import { StyleSheet } from 'react-native';

/**
 * Cores do app, as mesmas do web (tema escuro). Ficam num lugar só para que tela nova não invente
 * tom próprio e o app pareça dois.
 */
export const cores = {
  fundo: '#0e0f13',
  cartao: '#181a20',
  borda: '#2a2c33',
  texto: '#f2f2f2',
  textoFraco: '#9a9ba3',
  dica: '#6b6d75',
  destaque: '#e8743b',
  aviso: '#f2a65a',
  erro: '#f07167',
  ok: '#4fb477',
  perigo: '#c0392b',
  gelo: '#7cc4e8',
  calor: ['#22252c', '#1f4d36', '#2f7a50', '#4fb477'] as const,
};

export const estilos = StyleSheet.create({
  tela: { flexGrow: 1, backgroundColor: cores.fundo, padding: 16, gap: 12 },
  cartao: {
    backgroundColor: cores.cartao,
    borderColor: cores.borda,
    borderWidth: 1,
    borderRadius: 12,
    padding: 16,
    gap: 8,
  },
  titulo: { color: cores.texto, fontSize: 20, fontWeight: '700' },
  subtitulo: { color: cores.texto, fontSize: 16, fontWeight: '600' },
  corpo: { color: cores.texto, fontSize: 15, lineHeight: 21 },
  dica: { color: cores.textoFraco, fontSize: 13, lineHeight: 18 },
  erro: { color: cores.erro, fontSize: 14 },
  codigo: { color: cores.destaque, fontWeight: '700' },
});
