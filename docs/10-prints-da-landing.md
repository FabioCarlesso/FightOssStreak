# Prints da Landing

A landing pública (`/`) mostra quatro telas do app em oito arquivos — desktop e celular de cada uma
— mais a imagem de prévia de link. Todos vivem em `web/public/prints/` e são gerados por
`scripts/capturar-prints.mjs`.

Este documento existe por um motivo só: **print é a parte da página que apodrece primeiro**. Mudou o
layout da árvore e o print continua bonito, só que mentindo. Sem um caminho reproduzível para
refazê-los, a resposta honesta a "mexi na tela, e agora?" seria recortar tela a tela na mão, e é
isso que faz a recaptura nunca acontecer.

## Quando refazer

Sempre que um PR mexer na aparência de uma destas telas: árvore (`/arvore`), detalhe do nó
(`/no/:code`), formulário de drill ou tela inicial (`/hoje`). Vale para mudança de layout, de
espaçamento e de cor — não para texto de conceito ou pergunta de quiz, que os prints não mostram.

Refazer é barato (um comando), então na dúvida refaça.

## Como refazer

Precisa de `google-chrome` no PATH — o script fala CDP com ele por WebSocket, sem Playwright nem
Puppeteer no `package.json`.

```bash
# terminal 1 — API em :8080 (perfil dev, H2 em memória)
npm run dev:backend

# terminal 2 — web em :5173
npm run dev:web

# terminal 3
node scripts/capturar-prints.mjs --semear
```

Sai um relatório com o tamanho de cada arquivo. O script devolve 1 se algum passar de 150 KB — aí
baixe `QUALIDADE_WEBP` no topo dele e repita.

Iterar em um print só: `node scripts/capturar-prints.mjs --tela=drill` (nomes: `arvore`, `no`,
`drill`, `hoje`, `og`). Sem `--semear`, porque o banco já está semeado.

Outras bases: `--web=`, `--api=` e `--saida=`.

## O que `--semear` faz, e por que ele existe

Print de banco vazio mostra uma agenda escrita "nada vencido hoje" — o oposto do que a seção que
exibe esse print afirma. Então o script popula progresso de exemplo antes de fotografar:

- conclui oito nós (M0.1–M0.5 e M1.1–M1.3) respondendo o quiz de verdade;
- registra onze drills em **dias diferentes**, o que rende streak de 8 dias;
- deixa quatro nós vencidos hoje, com atrasos distintos, para a agenda parecer uma agenda;
- refaz um quiz já aprovado, que é o que acende a quarta métrica de `05-mvp-web-plano.md`.

Tudo pela API pública, sem SQL: o que permite isso é o campo `drilledOn` do `DrillRequest`, que
aceita data passada. As respostas certas do quiz saem do próprio currículo versionado — a API serve
as alternativas embaralhadas e sem gabarito (D16), como deve ser.

**Os prints são capturados sobre esses dados de exemplo**, não sobre uso real de ninguém. A anotação
de drill que aparece no print de `/no/M1.3` é do roteiro do script.

O perfil `dev` usa H2 em memória, então recomeçar do zero é reiniciar o backend. Semear duas vezes no
mesmo banco empilharia drills e mudaria a agenda; o script detecta e pula.

## O que cada print precisa mostrar

O enquadramento é editorial, não acidental — a landing afirma uma coisa ao lado de cada imagem, e a
imagem precisa sustentar a afirmação:

| Arquivo | Precisa mostrar |
|---|---|
| `arvore-*` | os três estados na mesma tela: concluído, disponível e travado com "Requer: …" — sem o travado, a frase "um nó só abre quando os pré-requisitos fecham" fica sem prova |
| `no-*` | conceito escrito, vídeo incorporado e **o crédito ao canal** visível (D7) |
| `drill-*` | as quatro opções de recall e a previsão de quando o nó volta |
| `hoje-*` | streak e a agenda com mais de um item, com atrasos diferentes |
| `og.jpg` | o hero da landing em 1200×630 |

Por isso o script espera por seletor (`.node-row--locked`, `.due-list__item`) em vez de esperar pelo
`load`: se a tela não chegou ao estado que a seção afirma, é melhor falhar do que gravar um print
que parece válido.

## Depois de recapturar

1. Abra os arquivos e confira contra a tabela acima.
2. Releia os `alt` em `web/src/content/landing.ts`: eles descrevem o que a imagem mostra, então
   mudança de enquadramento costuma pedir mudança de texto.
3. Se o número de nós, perguntas ou vídeos mudou, o teste `landing.test.ts` avisa — ele confere a
   copy contra o currículo.

## Formato e limites

- **WebP** para as telas, direto do `Page.captureScreenshot` (qualidade 82) — dispensa conversor
  externo.
- **JPEG** para a imagem de prévia: é o formato que todo rastreador de link aceita sem susto.
- Desktop em 1280×800 a 1x; celular em 390×844 a 2x (780 px de largura). Os dois porque print de
  desktop não se lê em tela de 390 px — a landing troca a origem por `<picture>`.
- Teto de **150 KB por arquivo**. A landing carrega inteira antes de a pessoa decidir se entra.

## Prévia de link (`og.jpg`)

A imagem é gerada a partir do hero da própria landing, então ela nunca fica fora de sintonia com a
página. As tags `og:url` e `og:image` exigem URL absoluta e são injetadas no build a partir de
`VITE_PUBLIC_URL` (ver `web/vite.config.ts`); sem a variável, nenhuma das duas é emitida. Cravar o
domínio no HTML poria configuração de ambiente dentro da imagem, que é o que a D22 evita.
