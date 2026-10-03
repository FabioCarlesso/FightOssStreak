# D35 — Print do nó passa a ser enquadrado de forma diferente em cada largura

## Justificativa

O bloco da anotação fixada acrescentou ~90px ao card do conceito, e em 390×844 isso estourou: a
página do nó vista do topo passou a terminar **antes** do player, e o print de celular deixou de
sustentar o que `docs/desenvolvimento/prints-da-landing.md` exige dele (conceito + vídeo + crédito ao canal, D7). Não
dá para resolver com um enquadramento só: ancorar no `.video` conserta o celular e estraga o
desktop, onde o player tem 442px de altura e empurraria título e conceito para fora do quadro. Daí
`porFormato` em `capturar-prints.mjs` — o celular ancora no vídeo, o desktop segue do topo. Efeito
colateral bom: no celular o crédito passou a vir do `<figcaption>` do próprio app, e não do overlay
do YouTube, que era quem sustentava a regra antes. Junto, a semeadura passou a fixar uma anotação em
M1.3: print de estado vazio mostraria o convite "Anotar" em vez do recurso, pelo mesmo motivo que já
fazia a agenda ser semeada

## Revisar quando

Se uma terceira tela precisar de override — aí o sinal é que o par de formatos ficou apertado demais
para a página, e a pergunta passa a ser sobre o layout, não sobre a captura

---

[Índice das decisões](../README.md)
