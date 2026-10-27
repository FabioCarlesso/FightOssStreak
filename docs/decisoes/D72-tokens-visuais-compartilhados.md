# D72 — Os tokens visuais moram em `shared/tokens`, e o `:root` do web é gerado deles

## Justificativa

O `mobile/src/theme.ts` dizia usar "as mesmas cores do web", e nenhuma batia: o fundo, o cartão, a
borda, o texto, o destaque, o ok e o erro tinham tons próprios, escolhidos à mão quando o app nasceu
(D67). O raio e os tamanhos de fonte também eram repetidos à mão. É o problema que `shared/domain` já
resolveu para regra de negócio (D17): duas cópias que se afastam em silêncio, e o app passa a
parecer dois (#183).

### Um pacote de TypeScript puro, e CSS gerado

`shared/tokens` (`@fos/tokens`) guarda cor, escala tipográfica, espaçamento e raio, no padrão de
`shared/domain`: TypeScript cru, sem dependência de `react` nem de `react-native` — o pacote vale
para os dois apps e não pode puxar o React de nenhum deles para a raiz.

- **O app importa direto.** `mobile/src/theme.ts` mapeia os tokens nos nomes em português que as
  telas já usavam (`cores.fundo`, `cores.destaque`…), e por isso nenhuma tela mudou de API. Ali não
  há cor literal; o único literal de cor no app é o `#fff` do texto do botão de perigo, a exceção
  que o contrato da #182 permite (branco e preto puros), com comentário.
- **O web recebe CSS.** `npm run gen:tokens` (`scripts/gen-tokens.mjs`) escreve
  `web/src/tokens.css`, um `:root` com todas as variáveis e o `color-scheme: dark`, importado antes do
  `styles.css`. O arquivo é versionado, diz no cabeçalho que é gerado, e
  `scripts/gen-tokens.test.mjs` reprova o `npm test` se ele não bater com o pacote — o mesmo freio
  do `gen:types`, sem passo novo no CI. A saída já sai no formato do Prettier, então o arquivo não
  precisa de exceção no `.prettierignore`.
- **Tamanho em rem no pacote, px no app.** A escala tipográfica e o espaçamento são definidos em
  rem, a unidade do web; o pacote exporta também `textoPx`/`espacoPx`, com `px = round(rem × 16)`,
  para o React Native, que não conhece rem. Raio já é px nos dois lados.

O gerador importa o `.ts` do pacote sem compilar: o Node 22 do CI e da imagem apaga os tipos sozinho.
Por isso o pacote só usa sintaxe apagável (nada de `enum` nem `namespace`).

### Valores canônicos

Onde web e mobile divergiam, **vale o do web** — já era o que a landing, os prints e a imagem do marco
mostravam, e a #181/#182 ajustaram o contraste em cima dele. A tabela completa (nomes e valores) é o
próprio `shared/tokens/src/index.ts`. As escolhas que mudam o app:

| Papel | Mobile antes | Agora (token) |
|---|---|---|
| fundo | `#0e0f13` | `bg` `#0f1115` |
| cartão | `#181a20` | `surface` `#171a21` |
| borda | `#2a2c33` | `border` `#2a2f3a` (chip e campo passam a `border-control` `#687284`, #181) |
| texto | `#f2f2f2` | `text` `#e8eaf0` |
| texto fraco | `#9a9ba3` | `muted` `#9aa3b2` |
| placeholder | `#6b6d75` | `muted` — o cinza antigo não passava AA |
| destaque | `#e8743b` | `accent` `#e2703a` |
| texto sobre o destaque | `#fff` / fundo | `on-accent` `#150a03` (#181) |
| ok | `#4fb477` | `ok` `#4caf7d` |
| erro | `#f07167` | `warn` `#e46b66` (o web também deixou `#d9534f`, #181) |
| aviso | `#f2a65a` | `caution`, mesmo valor — passa a existir no web |
| gelo | `#7cc4e8` | `frozen` `#6aa9e0`, o anel do dia perdoado no web |
| heatmap | quatro verdes opacos | `surface-2`, `heat-1`, `heat-2`, `ok` — a escada do web |

A escala tipográfica do app arredonda para a do web: título 20 → 22 px (`text-xl`), subtítulo
16 → 17 (`text-lg`), pílula 12 → 13 (`text-xs`). São diferenças de um ou dois pixels, aceitas em troca
de uma escala só. Espaçamento intermediário que não é passo da escala (6, 10 px no app; 0.6rem no web)
fica literal, porque arredondar mudaria o layout.

### Fora de escopo

Componentes compartilhados entre web e mobile e tema claro (#183). O `:root` à mão do
`web/src/styles.css` sai na integração com a #182; até lá ele é carregado depois do `tokens.css` e
ainda prevalece onde os dois declaram a mesma variável.

## Revisar quando

- Entrar tema claro: o pacote passa a ter dois conjuntos de cor, e o gerador, dois blocos.
- Web e app precisarem de componente compartilhado (React Native Web, por exemplo): aí a forma
  "CSS gerado + objeto TS" pode dar lugar a um só consumidor.
- O Node do CI ou da imagem sair do 22 para uma versão sem remoção de tipos nativa: o gerador
  passaria a precisar de `--experimental-strip-types` ou de um passo de build.
