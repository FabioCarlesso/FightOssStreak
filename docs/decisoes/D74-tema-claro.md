# D74 — O tema claro segue o sistema, com escolha manual guardada no aparelho; web e app juntos

## Justificativa

Web e app só tinham o tema escuro, e o claro ficou fora das #181 e #183. A #187 pediu para decidir
**se** e **como** ativá-lo. Decisão do dono do produto:

- **Segue o sistema por padrão** (`prefers-color-scheme` no web, `useColorScheme` no app). Quando o
  sistema não diz nada, vale o escuro, que continua sendo o tema de referência.
- **A tela Conta tem um seletor Sistema / Escuro / Claro**, no web e no app, que sobrescreve o
  sistema. Trocar não recarrega nada.
- **A escolha fica no aparelho**, não na conta: `localStorage` (`fos.tema`) no web e
  `expo-secure-store` (mesma chave) no app — o armazenamento que o token e o lembrete (#142) já usam,
  sem dependência nova. Nada vai para a API, o banco ou o OpenAPI. Escolher na conta exigiria coluna,
  campo e `CorsMetodosTest` para uma preferência que é do aparelho: o mesmo celular de dia no claro,
  o notebook à noite no escuro. **A coleta de uso (D50) não registra o tema**; passar a registrar exige
  reescrever `docs/privacidade/coleta-de-uso.md` antes.
- **O mobile entra junto**, para o app não afirmar uma aparência e o site outra.

### Como está feito

- **`shared/tokens`.** `cores` continua sendo o escuro (a API que o app e a imagem do marco usam não
  mudou). `coresClaras` traz só o que muda; `CORES_FIXAS` lista o que não acompanha o tema; e o tipo
  de `coresClaras` obriga toda chave de `cores` fora de `CORES_FIXAS` a ter valor claro — cor nova não
  entra sem decidir como fica no outro tema. `temas.escuro`/`temas.claro` são os conjuntos completos.
- **Contraste conferido por teste.** `shared/tokens/src/contraste.test.ts` calcula a razão WCAG dos
  pares da régua da #181 nos dois temas e reprova o `npm test` abaixo do mínimo: texto (`text`,
  `muted`, `accent`, `warn`, `ok`, `caution`) ≥ 4,5:1 em `bg`, `surface` e `surface-2`; placeholder e
  texto ≥ 4,5:1 no campo; `border-control` ≥ 3:1 em `bg`, `surface`, `surface-2` e `fundo-campo`;
  anel de foco (`focus`, da #186, com valor próprio em cada tema) e anel do dia perdoado (`frozen`) ≥ 3:1; `on-accent` ≥ 4,5:1 sobre `accent`
  e `accent-hover`; o branco do botão de perigo ≥ 4,5:1; e as pílulas sobre o próprio fundo suave, incluindo `warn` sobre `warn-soft` — que no escuro dava
  4,49:1 e foi corrigido na integração com a #186 baixando o `warn-soft` escuro para 12% (4,69:1).
  A paleta proposta na issue passou sem ajuste; os tons que ela não trazia (`accent-hover`, os
  `*-soft`, `caution`, `fundo-campo`, `heat-*`) foram escolhidos aqui e também passam. O pior caso
  do claro é `frozen` em `surface-2` (4,94:1, e o mínimo dele é 3:1) e `border-control` em
  `surface-2` (3,34:1).
- **Web.** O gerador escreve o escuro no `:root` e o claro duas vezes: em
  `@media (prefers-color-scheme: light) { :root:not([data-theme='dark']) }` e em
  `[data-theme='light']`, cada um com seu `color-scheme`. A escolha põe `data-theme` no `<html>` (ou o
  tira, em Sistema). Para não piscar escuro antes de virar claro, `web/public/tema.js` aplica o
  atributo **antes do bundle**, carregado de forma síncrona no `<head>`. Arquivo à parte, e não
  script inline, porque a CSP é `script-src 'self'` — **a CSP não foi afrouxada**. O nginx serve o
  arquivo com `no-cache`, como o HTML, porque o nome não tem hash. O `index.html` também leva
  `<meta name="darkreader-lock">`: extensão de modo escuro (Dark Reader) repintava por cima dos
  tokens e o tema saía misturado — o claro com fundo escuro e botão no laranja do claro. Foi pego
  na revisão, num Chrome com a extensão ligada.
- **App.** `theme.ts` mapeia os dois conjuntos (`paletas`); `state/tema.tsx` tem o `TemaProvider`
  (na raiz, acima do login), o `useTema()` (cores, estilos comuns e a escolha) e o `criarEstilos()`,
  que cria o `StyleSheet` de um componente uma vez por tema. `cores` e `estilos` estáticos deixaram de
  ser exportados: import estático congelaria o escuro, e o typecheck agora pega quem esquecer. A
  `StatusBar` acompanha o tema, e o `app.json` passou a `userInterfaceStyle: automatic`.

### O que não acompanha o tema

- **Faixas** (`belt-*`): são a cor da faixa de verdade.
- **Moldura do celular** (`phone-frame`) e **prints da landing**: os prints são do tema escuro, e a
  moldura é a de um aparelho em volta deles.
- **Sombras** (`shadow-*`): escurecem o que está atrás — miniatura de vídeo, a moldura.
- **Imagem do marco** (`imagemDoMarco.ts`): continua no escuro. É uma imagem para compartilhar fora do
  app, e sair diferente conforme o tema de quem gerou não acrescenta nada.
- **Vídeo**: o `#000` do fundo do player e o `#fff` sobre miniatura continuam as exceções da D72.

### A landing segue o tema

A landing usa os mesmos tokens das telas do app, e os pares dela estão entre os que o teste confere.
Por isso ela acompanha o sistema (e a escolha, se houver) como o resto. Os prints continuam escuros
dentro da moldura; refazê-los no claro está fora da #187.

## Revisar quando

- Alguém pedir que a escolha acompanhe a conta entre aparelhos: aí vira dado de conta (coluna, API,
  `DELETE /api/me`), e a decisão de guardar no aparelho cai.
- Entrar tema de alto contraste: o pacote passa de "escuro + diferenças do claro" para N conjuntos, e
  o gerador e o teste de contraste passam a iterar sobre eles.
- A CSP passar a admitir script inline por hash: o `tema.js` pode voltar para o `index.html`.

---

[Índice das decisões](README.md)
