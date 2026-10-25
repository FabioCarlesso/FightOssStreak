import { FONTE_DO_SISTEMA, type ImagemDoMarco } from './imagemDoMarco.ts';

/**
 * Desenha a imagem composta em `imagemDoMarco.ts` num canvas e entrega o PNG como download (#106).
 *
 * Tudo acontece na página: o canvas é desenhado direto com a API 2D — e não a partir de um SVG
 * carregado como `<img>` —, então nada passa pelo `img-src` da CSP (D65), não há risco de canvas
 * "contaminado" e nenhuma requisição sai. O download é um `<a download>` apontando para um `blob:`
 * local, que a CSP não rege. Sem dependência nova.
 *
 * Rejeita quando o navegador não oferece canvas 2D ou não gera o PNG; quem chama mostra o aviso.
 */
export async function baixarImagemDoMarco(imagem: ImagemDoMarco): Promise<void> {
  const canvas = document.createElement('canvas');
  canvas.width = imagem.largura;
  canvas.height = imagem.altura;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('canvas 2D indisponível');

  ctx.fillStyle = imagem.fundo;
  ctx.fillRect(0, 0, imagem.largura, imagem.altura);
  ctx.textAlign = 'center';
  ctx.textBaseline = 'alphabetic';
  for (const el of imagem.elementos) {
    ctx.fillStyle = el.cor;
    if (el.tipo === 'retangulo') {
      ctx.fillRect(el.x, el.y, el.largura, el.altura);
    } else {
      ctx.font = `${el.peso} ${el.tamanho}px ${FONTE_DO_SISTEMA}`;
      ctx.fillText(el.texto, el.x, el.y);
    }
  }

  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
  if (!blob) throw new Error('PNG não gerado');

  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = imagem.nomeDoArquivo;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Revogar logo após o clique cancela o download em alguns navegadores; o atraso é o que o
  // FileSaver.js adota pelo mesmo motivo.
  setTimeout(() => URL.revokeObjectURL(url), 40_000);
}
