# D21 — Primeira leva de vídeos (M0 e M1) catalogada com verificação automática, mas sem ninguém ter assistido aos 11 do começo ao fim

## Justificativa

Todos os 11 nós passaram por `catalogar-video.mjs`, então título e canal vêm do oEmbed do YouTube e
a incorporação foi confirmada vídeo a vídeo — nenhum campo foi digitado à mão. O encaixe pedagógico
foi julgado por título, descrição, capítulos, duração, reputação do canal e **thumbnail** (que é o
que dá para checar sem assistir: foi ela que reprovou quatro escolhas no-gi e uma fora de tema).
Isso cobre as regras verificáveis de `docs/conteudo/videos.md` — Gi (D6), instrucional, canal identificável, curto vence
longo — mas **não** cobre a única que importa de verdade: se o vídeo ensina o conceito do nó sem
erro técnico. Catalogar com essa ressalva declarada é melhor que deixar 11 nós sem referência
visual, porque o nó sem vídeo já quebra o ciclo de revisão hoje

## Revisar quando

**Ao assistir aos 11.** Os de menor confiança são M0.4 (o vídeo é sobre base/postura, mas não se
sabe se percorre as três bases do nó), M1.1 (concentra-se em meia-guarda, e o nó é sobre
sobrevivência em geral), M1.4 (cobre os erros da upa, não o elbow escape que o nó trata como par) e
M1.6 (não confirmado que ensina pescoço antes de ganchos — a regra de descarte do nó). Nenhum
`startSeconds` foi definido: saber onde o trecho útil começa exige assistir

---

[Índice das decisões](../README.md)
