package dev.fos.model;

/**
 * Proporção do vídeo, para o player não ser montado no formato errado.
 *
 * <p>Existe porque os complementares nasceram de clipes de celular — 720×1280 — e o frame do vídeo
 * canônico é 16:9. Vertical em frame horizontal vira tarja preta dos dois lados com a imagem
 * espremida no meio. O valor é detectado pelo {@code catalogar-video.mjs} a partir do formato real
 * do vídeo, nunca digitado; {@link #HORIZONTAL} é o default de quando não deu para saber, que é o
 * caso da esmagadora maioria dos vídeos do YouTube.
 */
public enum VideoOrientation {
    HORIZONTAL,
    VERTICAL
}
