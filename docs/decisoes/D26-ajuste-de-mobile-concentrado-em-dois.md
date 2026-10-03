# D26 — Ajuste de mobile concentrado em dois breakpoints; alvo de toque decidido por `pointer: coarse`, não por largura

## Justificativa

`web/src/styles.css` não tinha nenhuma `@media`, apesar de o celular ser o alvo principal de uso
desde que o app foi publicado para acesso pelo telefone. A árvore era o sintoma: `.node-row__inner`
era flex sem `flex-wrap`, o título tinha base 0 e as chips não encolhiam abaixo do próprio
`min-content` — o título quebrava uma palavra por linha e as chips vazavam para fora da tela.
Corrigir componente a componente espalharia remendo, então os ajustes de largura vivem em um bloco
`max-width: 640px` (mais um `380px` para os aparelhos mais estreitos): a próxima tela tem um lugar
óbvio para ir.

**Alvo de toque ficou em bloco separado, por `pointer: coarse`**, porque é propriedade do
dispositivo e não da janela — celular em paisagem tem ~740px e continua sendo dedo, então a regra
por largura erraria justamente esse caso, e alargar por largura mexeria no desktop. O layout de
desktop foi conferido pixel a pixel contra o anterior nas quatro rotas

## Revisar quando

Se entrar framework de UI ou CSS-in-JS no web (hoje não há), ou se a contagem de breakpoints passar
de dois — aí o sinal é que o layout pede container queries, não mais uma `@media`

---

[Índice das decisões](../README.md)
