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

> ℹ️ **A recaptura ficou bloqueada da #24 até a #58, e agora está destravada** — pelo caminho que
> a versão anterior deste documento já prescrevia, não por um atalho. O app exige login (D36/D37) e
> o script não tem como se autenticar sozinho: ele semeia pela API com `fetch` e sobe um Chrome com
> perfil novo, e os dois levavam `401`.
>
> **O que resolveu:** o script passou a **receber** uma sessão já obtida, em `FOS_PRINT_COOKIE` —
> aplicada no `fetch` da semeadura (mais o cabeçalho `X-XSRF-TOKEN`, que o
> `CookieCsrfTokenRepository.withHttpOnlyFalse()` cobra) e no Chrome via `Network.setCookie` do CDP.
> Continua valendo o que já estava escrito aqui: é caminho de operador, com login de verdade, e
> **não** vale criar um modo que desliga o portão para capturar tela — seria porta dos fundos
> permanente para economizar oito imagens. Sem a variável o script roda como antes e falha em `401`.
>
> **Defasagem que sobra:** a #58 recapturou só `no-desktop` e `no-mobile`, porque foi o conceito do
> nó que mudou. Os outros seis arquivos seguem com o cabeçalho anterior à #24 — sem o nome da conta
> e o botão *Sair*. Continua sendo defasagem pequena e conhecida, e agora tem conserto de um comando:
> `node scripts/capturar-prints.mjs --semear` refaz todos.

## Como refazer

Precisa de `google-chrome` no PATH — o script fala CDP com ele por WebSocket, sem Playwright nem
Puppeteer no `package.json`.

```bash
# terminal 1 — API em :8080 (perfil dev, H2 em memória)
npm run dev:backend

# terminal 2 — web em :5173
npm run dev:web

# terminal 3 — a sessão vem de fora; ver "Como obter a sessão" abaixo
FOS_PRINT_COOKIE='JSESSIONID=...; XSRF-TOKEN=...' node scripts/capturar-prints.mjs --semear
```

**A porta importa**: o CORS do backend libera `localhost:5173` e nada mais (`SecurityConfig`), e o
proxy do Vite repassa o `Origin` do navegador. Se a 5173 estiver ocupada o Vite sobe na 5174 e a
semeadura morre em `Invalid CORS request` — libere a porta em vez de mudar a base.

### Como obter a sessão

O `FOS_PRINT_COOKIE` é o cabeçalho `Cookie` de uma sessão de verdade — os dois valores que
importam são o `JSESSIONID` e o `XSRF-TOKEN`. O jeito honesto de consegui-lo é **entrar no app pelo
navegador** (provedor externo ou link de e-mail, conforme o ambiente) e copiar o `Cookie` do
DevTools → Network → qualquer requisição para `/api`.

Em ambiente local sem provedor nem envio de e-mail configurados, dá para emitir um link de entrada
direto no banco, porque `login_token` guarda o **SHA-256** do token e o resto do fluxo é o normal
do app — nenhuma porta dos fundos no código:

```bash
docker compose up -d db   # e suba o backend no perfil `postgres`

# a conta semeada pela V2 já nasce APROVADA (V6); falta uma identidade de e-mail para ela
docker exec fos-db psql -U fos -d fos -c "INSERT INTO user_identity
  (user_id, provider, provider_subject, email, email_verified, display_name, created_at, last_login_at)
  VALUES (1,'email','voce@local.test','voce@local.test',TRUE,'Autor',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);"

T="local-$(date +%s)"; H=$(printf '%s' "$T" | sha256sum | cut -d' ' -f1)
docker exec fos-db psql -U fos -d fos -c "INSERT INTO login_token
  (user_id, token_hash, created_at, expires_at)
  VALUES (1,'$H',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '2 hours');"

curl -s -c j.txt -b j.txt -o /dev/null "http://localhost:8080/api/login/email/$T"
export FOS_PRINT_COOKIE="$(awk 'NF==7 {printf "%s=%s; ", $6, $7}' j.txt | sed 's/; $//')"
```

> ⚠️ **Não use a conta de demonstração (D39) para capturar.** Ela funciona e é tentadora, mas põe a
> faixa "Você está numa demonstração" no topo de todas as telas — o print sai com um aviso que o
> app real não mostra. Foi exatamente o erro cometido na primeira tentativa de recaptura da #58.

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
- refaz um quiz já aprovado, que é o que acende a quarta métrica de `05-mvp-web-plano.md`;
- fixa uma anotação em M1.3, o nó fotografado, para o print do nó mostrar o bloco preenchido em vez
  do convite "Anotar" — estado vazio é honesto no app e inútil em print.

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
| `no-*` | conceito escrito, a anotação pessoal, vídeo incorporado e **o crédito ao canal** visível (D7) |
| `drill-*` | as quatro opções de recall e a previsão de quando o nó volta |
| `hoje-*` | streak e a agenda com mais de um item, com atrasos diferentes |
| `og.jpg` | o hero da landing em 1200×630 |

Por isso o script espera por seletor (`.node-row--locked`, `.due-list__item`) em vez de esperar pelo
`load`: se a tela não chegou ao estado que a seção afirma, é melhor falhar do que gravar um print
que parece válido.

### Enquadramento diferente por largura

`no` é a única tela com `porFormato` no script, e por necessidade. Em 1280×800 a página do nó cabe a
partir do topo até o vídeo; em 390×844 não cabe mais desde que a anotação fixada entrou no card do
conceito (#45) — vista do topo, a tela do celular termina antes do player, e o print deixaria de
sustentar a linha da tabela acima. Então o celular é ancorado no `.video`, e o desktop segue do
topo: ancorar os dois no vídeo empurraria título e conceito para fora em 1280px, onde o player tem
442px de altura.

Se outra tela precisar do mesmo, o campo aceita qualquer chave de enquadramento
(`rolarAte`, `alinhar`, `ajuste`) por sufixo de formato.

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
