/**
 * Composição da imagem do marco de streak (#106) — só a descrição, sem tocar em DOM nem canvas.
 *
 * Separada do desenho (`baixarImagemDoMarco.ts`) para que o conteúdo seja testável sem canvas: é
 * aqui que se garante o que a imagem diz e, principalmente, o que ela **não** diz. A função recebe
 * só dois números — a corrente e o marco — e não tem como receber e-mail, nome nem id de conta: a
 * ausência de dado sensível é garantida pela assinatura, e não por um filtro que alguém esqueceria.
 *
 * Sem fonte externa: o desenho usa a pilha de fontes do sistema, e nada é buscado fora da página
 * para compor a imagem (a mesma cautela da D50 com terceiros).
 */

/** Quadrada, porque é o formato que as redes aceitam sem cortar. */
export const LADO_DA_IMAGEM = 1080;

/** Pilha do sistema: nenhuma fonte é baixada para desenhar. */
export const FONTE_DO_SISTEMA =
  'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';

export type ElementoDaImagem =
  | {
      tipo: 'retangulo';
      x: number;
      y: number;
      largura: number;
      altura: number;
      cor: string;
    }
  | {
      tipo: 'texto';
      texto: string;
      x: number;
      y: number;
      tamanho: number;
      peso: 400 | 600 | 800;
      cor: string;
    };

export type ImagemDoMarco = {
  largura: number;
  altura: number;
  fundo: string;
  /** Desenhados na ordem; texto é sempre centralizado em `x`. */
  elementos: ElementoDaImagem[];
  nomeDoArquivo: string;
};

// As cores do tema escuro do web (`styles.css`), fixas: a imagem sai igual em qualquer tela.
const FUNDO = '#0f1115';
const TEXTO = '#e8eaf0';
const APAGADO = '#9aa3b2';
const DESTAQUE = '#e2703a';

export function comporImagemDoMarco(currentStreak: number, marco: number): ImagemDoMarco {
  const lado = LADO_DA_IMAGEM;
  const centro = lado / 2;
  const unidade = currentStreak === 1 ? 'dia seguido' : 'dias seguidos';

  return {
    largura: lado,
    altura: lado,
    fundo: FUNDO,
    elementos: [
      { tipo: 'retangulo', x: 0, y: 0, largura: lado, altura: 16, cor: DESTAQUE },
      {
        tipo: 'texto',
        texto: 'FightOssStreak',
        x: centro,
        y: 200,
        tamanho: 56,
        peso: 600,
        cor: TEXTO,
      },
      {
        tipo: 'texto',
        texto: String(currentStreak),
        x: centro,
        y: 560,
        tamanho: 300,
        peso: 800,
        cor: DESTAQUE,
      },
      { tipo: 'texto', texto: unidade, x: centro, y: 660, tamanho: 64, peso: 600, cor: TEXTO },
      {
        tipo: 'texto',
        texto: `Marco de ${marco} dias seguidos`,
        x: centro,
        y: 800,
        tamanho: 48,
        peso: 400,
        cor: APAGADO,
      },
      { tipo: 'texto', texto: 'FOS', x: centro, y: 980, tamanho: 36, peso: 600, cor: APAGADO },
    ],
    nomeDoArquivo: `fos-marco-${marco}-dias.png`,
  };
}
