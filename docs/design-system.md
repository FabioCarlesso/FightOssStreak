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
  telas usam, por tema (ver *Temas*). Tom novo nasce no pacote, nunca como literal na tela.
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

Existem dois temas, **escuro** e **claro** (D74). O escuro é o de referência: é o que vale quando o
sistema não diz qual modo usa.

- **Como é escolhido.** Por padrão, o tema segue o sistema — `prefers-color-scheme` no web,
  `useColorScheme` no app. Na tela *Conta*, o seletor **Sistema / Escuro / Claro** sobrescreve o
  sistema, e o atalho ☀️/🌙 do cabeçalho troca entre claro e escuro com um clique (é escolha
  manual; voltar a seguir o sistema é na Conta). A troca vale na hora, sem recarregar, e a escolha fica **só no aparelho** (`localStorage`
  no web, `expo-secure-store` no app): não é dado de conta e a coleta de uso não a registra.
- **De onde vêm as cores.** `cores` é o escuro e `coresClaras` traz o que muda no claro, ambos em
  `shared/tokens/src/index.ts`. No web, o `tokens.css` gerado tem os dois (`npm run gen:tokens`), e
  o CSS só lê `var(--…)`. No app, cor e estilo com cor vêm do `useTema()` e do `criarEstilos()`
  (`mobile/src/state/tema.tsx`) — nunca de um import estático, que congelaria um tema.
- **Cor nova** entra em `cores` e em `coresClaras` (o tipo cobra), ou em `CORES_FIXAS` se não
  acompanha o tema. Se for texto, borda de controle ou foco, o par entra em
  `shared/tokens/src/contraste.test.ts`, que reprova o `npm test` abaixo de 4,5:1 (texto) ou 3:1
  (borda de controle e foco) em qualquer um dos dois temas.
- **O que não acompanha o tema:** as faixas (`belt-*`), a moldura do celular da landing, os prints
  da landing (são do tema escuro), as sombras e a imagem do marco (`imagemDoMarco.ts`), que sai
  sempre escura. A landing em si acompanha o tema.
- **Tela nova se confere nos dois temas.** No Chrome, DevTools → *Rendering* → *Emulate CSS
  prefers-color-scheme*, ou o seletor da tela *Conta*; no emulador, o modo escuro do sistema ou o
  mesmo seletor. Cor literal em tela nova é a forma mais fácil de quebrar um dos dois.
