# Design system

Guia de uso dos tokens visuais do web e do app. Os valores moram em
[`shared/tokens/src/index.ts`](../shared/tokens/src/index.ts) (D72) e não se repetem aqui.

## Temas

Existem dois temas, **escuro** e **claro** (D74). O escuro é o de referência: é o que vale quando o
sistema não diz qual modo usa.

- **Como é escolhido.** Por padrão, o tema segue o sistema — `prefers-color-scheme` no web,
  `useColorScheme` no app. Na tela *Conta*, o seletor **Sistema / Escuro / Claro** sobrescreve o
  sistema. A troca vale na hora, sem recarregar, e a escolha fica **só no aparelho** (`localStorage`
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
