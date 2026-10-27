# Design system: como usar os tokens

Este guia diz **qual token usar** ao fazer uma tela nova, no web ou no app. O **porquê** de existirem
tokens está na [D72](decisoes/D72-tokens-visuais-compartilhados.md), e a organização do CSS por área
está na [D73](decisoes/D73-css-por-area.md).

## Fonte da verdade

Cor, foco, tipografia, espaçamento, raio e sombra moram em
[`shared/tokens/src/index.ts`](../shared/tokens/src/index.ts). É lá que se descobre quais tokens
existem e quanto valem. Este guia não lista os tokens de propósito: uma lista aqui seria uma terceira
cópia, e ela se desatualizaria sem ninguém perceber.

- **Web:** `web/src/tokens.css` é **gerado** do pacote por `npm run gen:tokens`. Não edite à mão: o
  `npm test` falha se o arquivo não bater com o pacote. No CSS, use sempre `var(--nome)`.
- **App:** `mobile/src/theme.ts` importa o pacote e expõe os valores com os nomes em português que as
  telas usam. Tom novo nasce no pacote, nunca como literal na tela.
- **Referência externa** (por exemplo, um design system publicado à parte) é cópia. Ela segue o
  pacote, e não o contrário.

Precisa de um valor que não existe? Acrescente o token em `shared/tokens`, rode `npm run gen:tokens`
e só então use. Os únicos literais aceitos são as exceções que o próprio CSS comenta (branco puro,
`inherit`, o contorno `inset` do dia perdoado) e o espaçamento intermediário que a D72 deixou fora da
escala.

## Superfícies

São três níveis, do fundo para a frente: `bg` é a página, `surface` é o cartão, e `surface-2` é o
que fica dentro do cartão (campo, chip, célula vazia do heatmap). Não pule nível nem inverta a ordem:
um cartão dentro de outro cartão sobe para `surface-2`, e não volta para `bg`.

## Bordas

- `border` é **decoração**: separa cartões e seções e não precisa ser vista para a tela funcionar.
- `border-control` é **controle**: campo, chip, opção do quiz, qualquer coisa em que se clica ou se
  digita. Ela passa 3:1 contra o fundo, e a decorativa não passa (#181). Se o contorno é o que mostra
  onde clicar, ele é `border-control`.

## Destaque

Texto ou ícone sobre fundo `accent` usa `on-accent`, nunca branco: branco sobre o laranja não passa AA
(#181). O `accent-soft` é fundo de item selecionado, e o texto sobre ele continua em `accent` ou
`text`.

## Foco

Todo controle mostra o mesmo anel de foco: 2px em `--focus`, afastado 2px. A regra fica num lugar só,
em `web/src/styles/base.css`, e já cobre botão, link, `summary`, `input`, `textarea`, `select` e
`[tabindex]`. Componente novo herda o anel sem fazer nada.

- **Não dê `outline: none` a um campo.** A classe do componente vence a regra global por
  especificidade, e o campo fica sem anel.
- Quando o controle de verdade fica escondido (rádio do quiz, checkbox do chip), o anel vai no rótulo
  que o envolve, usando `:has(input:focus-visible)` ou `:focus-within`, sempre com `--focus`.
- Não use `accent` para foco. O `--focus` existe para que a cor do anel e a cor da marca possam mudar
  separadamente.

## Status

Estado (em dia, atrasado, perdoado, erro, ok) **nunca é só cor**. Ele sempre vem com uma palavra ou um
glifo junto: quem não distingue as cores, ou lê a tela em escala de cinza, precisa saber o estado do
mesmo jeito. As cores de status (`ok`, `warn`, `caution`, `danger`, `frozen` e as versões `-soft`)
reforçam a palavra, mas não a substituem.

## Tamanhos

**Não crie tamanho novo: use a escala.** Fonte vem de `--text-*`, espaçamento de `--space-*`, raio de
`--radius-*`. Se nenhum passo serve, quase sempre o vizinho serve. Se de fato faltar um passo, ele
entra no pacote, para web e app mudarem juntos. No app, os mesmos tamanhos chegam em px.

A família de fonte (`--font-sans`, `--font-mono`) e a sombra composta (`--shadow-*` com deslocamento)
são só do web. O React Native usa a fonte do sistema e faz sombra de outro jeito, e o cabeçalho do
pacote explica por quê.

## Temas

Hoje só existe o tema escuro. O `:root` gerado declara `color-scheme: dark`.
