# App mobile

Como rodar o app Android e iOS em desenvolvimento. O app vive em `mobile/`, em Expo + React Native
(D67), e reaproveita `@fos/domain`, `@fos/api-client` e `@fos/types` pelos workspaces do npm: a
regra pura não é reescrita no app (D17).

## O que o app tem (#141)

O MVP mobile cobre o ciclo de retenção, com as mesmas regras e números do web:

| Tela | O que faz |
|---|---|
| Login | E-mail e senha, trocados por um token guardado no `expo-secure-store` (D68). **Criar conta** e **Esqueci a senha** abrem o site (`EXPO_PUBLIC_WEB_URL`), como a D68 decidiu |
| Portões | Versão mínima (pede atualização), conta bloqueada (mostra o motivo, com sair e excluir) e aceite do aviso por versão — na mesma ordem e com o mesmo texto do web |
| Hoje | Streak, freeze, dias ativos, heatmap e a agenda do SRS |
| Árvore e Nó | Conceito, pré-requisitos, vídeo pelo player do YouTube com crédito ao canal (D7), quiz, drill avulso e o aviso curto |
| Diário | As sessões e os drills avulsos do mês; registrar sessão com técnicas da agenda |
| Conta | Lembrete de revisão (ligar, desligar e horário), sair (revoga o token) e excluir a conta (`DELETE /api/me`, exigência das lojas) |

**Nenhuma regra é reimplementada no app.** Streak, freeze e agenda vêm do backend. A grade do
heatmap, a prévia do intervalo do drill, a conversão do formulário de sessão, os rótulos e o texto do
aviso vêm de `@fos/domain` — o mesmo código que o web usa. Para isso, o que o web guardava em
`web/src/content/` e em `web/src/state/sessionFields.ts` passou para `shared/domain`, e o web
reexporta de lá.

**Ficam fora, por ora:** Google e Apple (depois da #143, que traz credencial e dev build), edição de
sessão, filtros do diário, anotação fixada e histórico de drills do nó, clipes complementares e o modo
demonstração. Admin, painel e feedback continuam só no web.

### Lembrete de revisão (#142)

Notificação **local**, pelo `expo-notifications`, sem servidor de push (D70). O plano sai de
`src/lembretes/plano.ts`, uma função pura: um aviso por dia, no horário escolhido, para cada um dos
próximos sete dias em que houver revisão vencida, a partir do `nextReviewOn` que a árvore traz. O
`LembretesProvider` (`src/state/lembretes.tsx`) refaz o plano ao abrir o app, ao voltar para ele e
após cada registro, e cancela tudo quando a conta sai.

- A permissão é pedida **depois do primeiro registro**, nunca na abertura.
- O texto diz só quantas técnicas venceram: nada de nome de técnica, streak, peso ou sensação.
- Ligado e horário ficam no aparelho (`expo-secure-store`, chave `fos.lembretes`); o padrão é 19:00.
- **A permissão se lê pelo `canAskAgain`, e não pelo `status`.** No Android 13+, antes de qualquer
  pedido, o `expo-notifications` responde `denied` com `canAskAgain` verdadeiro, e ler o `status`
  fazia o app nunca perguntar — pego na dev build, na revisão da #160. O app marca no aparelho
  (`fos.lembretes.pedido`) que já pediu, para não repetir o pedido no registro seguinte.
- **No Android o lembrete chega com até uma hora de atraso.** Sem a permissão de alarme exato — que a
  Play Store reserva a app de despertador e agenda —, o sistema agenda com janela de uma hora
  (`window=+1h` no `dumpsys alarm`). Para um lembrete diário de revisão, isso é aceito.
- Falha da API ou do nativo é lembrete perdido, nunca tela quebrada.
- **O `expo-notifications` nunca é importado no topo de um arquivo.** No Expo Go do Android, desde a
  SDK 53, o próprio import lança erro, e como o lembrete é carregado pelo layout `(app)`, o app inteiro
  deixava de abrir antes do login. Foi pego no emulador, na revisão da #160. O `criarNotificador`
  (`src/lembretes/notificador.ts`) carrega o módulo com `require` tardio, e só fora do Expo Go do
  Android; ali o lembrete fica indisponível e a tela *Conta* diz por quê. O Jest não pegava o defeito
  porque troca o módulo por um falso, e o `expo export` só monta o bundle: quem segura agora é o
  `src/lembretes/carga.test.ts`.

**Onde dá para testar o lembrete de verdade:** no Expo Go do iOS e em qualquer dev build. No Expo Go
do Android, não. No emulador sem conta Expo, a dev build sai local:

```bash
cd mobile
npx expo run:android   # gera android/ (fora do git) e instala no emulador
```

O `expo prebuild` pede o `android.package`, que ainda não está no `app.json` (#143): use um valor
local de teste e **não o commite**. Depois, registre um drill num nó (o pedido de permissão aparece),
deixe uma revisão vencida e adiante o relógio do aparelho até o horário configurado. No emulador,
`adb shell settings put global auto_time 0` e `adb shell cmd alarm set-time <epoch em ms>` movem o
relógio, e `adb shell dumpsys alarm | grep <application id>` mostra o que está agendado.

### Como o app se organiza

- `app/` — rotas do **Expo Router**, uma por arquivo, só ligando o endereço à tela. `app/(app)/_layout.tsx`
  põe os portões na frente de tudo; `(abas)` são Hoje, Árvore, Diário e Conta.
- `src/screens/` — as telas. `src/components/` — peças reaproveitadas (portões, quiz, drill, vídeo).
- `src/state/` — token, sessão, avisos de 401/403 e o cliente por contexto (`useApi`), que é o que
  permite aos testes trocar a API por uma falsa.
- **401 em chamada com token volta ao login e apaga o token; 403 `acesso_recusado` leva à tela de
  bloqueio.** Quem percebe é o `fetch` do app (`src/api/client.ts`), e nenhuma tela precisa saber.
- **As abas não desmontam** no Expo Router: Hoje, Árvore e Diário recarregam ao voltar o foco
  (`useAoVoltar`). Sem isso, o streak continuava em 0 depois de registrar um drill — foi pego no
  emulador.

## Pré-requisitos

- Node 22 e `npm install` na raiz. O mobile é um workspace como os outros.
- Para o **emulador Android**: Android Studio com um AVD criado. Use uma imagem *Google Play* se for
  testar o login nativo do Google.
- Para o **iPhone físico**: o app **Expo Go** da App Store, com o iPhone e o computador na mesma
  rede.
- Para a **dev build**: uma conta Expo (gratuita) e, no iOS, a conta Apple Developer da #143.

O Expo Go só abre projetos da SDK que ele suporta, e normalmente é a mais recente. Quando a SDK do
`mobile/package.json` ficar para trás, use a dev build ou atualize a SDK.

## Apontar o app para a API

```bash
cp mobile/.env.example mobile/.env    # fora do git
```

`EXPO_PUBLIC_API_URL` precisa apontar para o **backend em `:8080`**, e não para o nginx em `:8081`. O
nginx só atende `PUBLIC_HOST` e responde 444 a qualquer outro host (D62), e o aparelho chama por
`10.0.2.2` ou pelo IP da rede. O backend pode vir de `npm run dev:backend` ou do Compose, que publica
`:8080`.

| Aparelho | `EXPO_PUBLIC_API_URL` |
|---|---|
| Emulador Android | `http://10.0.2.2:8080` (o `localhost` da máquina que roda o emulador) |
| iPhone ou Android físico | `http://<IP da máquina na rede>:8080` |

O valor é embutido no bundle quando o Metro sobe, então mudá-lo exige reiniciar o `expo start`.

`EXPO_PUBLIC_WEB_URL` é o site, para criar conta e recuperar a senha. Em produção é a URL pública;
em dev pode ficar vazia, e a tela de login diz o que configurar.

Para entrar em dev, use as contas de teste (`node scripts/seed-dev-users.mjs`,
[`contas-de-teste.md`](contas-de-teste.md)): o login do app é por e-mail e senha, e as duas já
nascem confirmadas.

## Rodar

```bash
npm run dev:mobile    # expo start --go, a partir da raiz
```

Os scripts do `mobile/` sobem no modo **Expo Go** (`--go`) de propósito. Com o `expo-dev-client`
instalado, o `expo start` puro escolhe a dev build: o QR code vira `exp+fightossstreak://`, e o Expo
Go não abre esse endereço. Enquanto não houver dev build instalada (#143), o QR code não levaria a
lugar nenhum.

- **Emulador Android:** com o AVD aberto, aperte `a` no terminal do Expo.
- **iPhone físico:** abra a câmera, leia o QR code do terminal e abra no Expo Go. Se a rede bloquear
  a conexão entre os aparelhos, `npm run start --workspace @fos/mobile -- --tunnel`.

O Metro usa a porta **8081**, a mesma que o Compose publica para o nginx. Com a stack do Compose de
pé, o Expo oferece outra porta; para fixar uma, use
`npm run start --workspace @fos/mobile -- --port 8082`.

## Dev build (EAS)

Com a dev build instalada no aparelho, o servidor sobe com
`npm run start:dev-client --workspace @fos/mobile`, e o QR code passa a abrir nela.

O Expo Go roda só os módulos nativos que vêm nele. Quando o app precisar de outro, como o login
nativo do Google (#141) ou o Sign in with Apple, o caminho passa a ser a **dev build**: um app
instalável com o seu próprio nativo, gerado na nuvem pelo EAS. É o que permite gerar o binário do
iOS sem Mac.

```bash
npx eas-cli login
npx eas-cli build --profile development --platform android   # .apk para o emulador ou o aparelho
npx eas-cli build --profile development --platform ios       # exige Apple Developer e o aparelho registrado
```

Na primeira build o EAS cria o projeto na conta Expo e grava o `extra.eas.projectId` no `app.json`.
Esse id não é segredo e vai para o repositório.

Os perfis ficam em `mobile/eas.json`:

| Perfil | Para quê |
|---|---|
| `development` | dev build com `expo-dev-client`, distribuição interna; o JavaScript vem do `expo start` |
| `preview` | build de teste com o JavaScript embutido, distribuição interna |
| `production` | build de loja; a versão de build é incrementada pelo EAS (`appVersionSource: remote`) |

**O bundle id do iOS e o application id do Android ainda não estão no `app.json`, de propósito.** Mudá-los
depois exige publicar um app novo, e quem os registra é a #143. Na primeira build o EAS pergunta e
grava os dois no `app.json`. Esse valor só entra no repositório junto com a decisão da #143.

As pastas `android/` e `ios/` não são versionadas: o EAS as gera a cada build a partir do `app.json`
(*Continuous Native Generation*). Credencial de assinatura nunca entra no repositório; o
`.gitignore` já recusa `.jks`, `.p8`, `.p12` e afins.

## Testes, lint e typecheck

```bash
npm run test:mobile                         # Jest + jest-expo + Testing Library
npm run typecheck --workspace @fos/mobile
npm run lint                                # o repositório inteiro, mobile incluído
```

`npm test` e `npm run typecheck` na raiz já incluem o mobile. **Consequência:** o job `web`, que é
required check, roda os dois, e um teste quebrado no mobile trava o merge pelo `web`, mesmo com o job
`mobile` fora da ruleset. Isso vem dos critérios da #140 (*`npm test` cobre o mobile*). Já o
job `mobile` virar required check é decisão separada; ver [`../repositorio.md`](../repositorio.md).

Os testes renderizam a tela com a API trocada por uma resposta fixa e não tocam a rede. Para iterar,
`npx jest --watch` dentro de `mobile/`.

## Armadilhas do monorepo

- **Dois Reacts.** O web usa o React 18 e o Expo, o 19. O npm resolve isso sozinho: o 18 fica em
  `node_modules/`, e o 19, o React Native e o Expo ficam em `mobile/node_modules/`. **Não acrescente
  `react` nem `react-native` à raiz.** Para conferir, rode `npm ls react`; todo pacote do Expo precisa
  estar dentro de `mobile/`.
- **Metro sem configuração.** O `@expo/metro-config` da SDK 57 já resolve os workspaces e os pacotes
  aninhados, e por isso não existe `metro.config.js`. O job `mobile` exporta o bundle do Android
  (`npx expo export`) justamente para pegar a regressão que o Jest não pega: um pacote de `shared/`
  que não resolve, ou um segundo React vindo da raiz.
- **`expo-modules-core` no Jest.** O `jest-expo` importa esse pacote sem declará-lo, e aqui o npm o
  aninha dentro de `expo/`. O `mobile/jest.config.js` aponta o import para a cópia que o `expo`
  resolve. Não instale `expo-modules-core` direto: o `expo-doctor` reprova, e a versão deixaria de
  acompanhar a do `expo`.
- **`test-renderer` fixado em `~1.2.0`.** É o renderizador do Testing Library 14. A 1.3 puxa um
  `react-reconciler` que pede React `^19.3`, e a SDK 57 fixa o 19.2.3. Os testes até passam, mas
  `npm ls react` acusa peer inválido. Suba junto com a SDK, quando o React do Expo alcançar o que ele
  pede.
- **Um TypeScript só.** A SDK 57 sugere o TypeScript 6, e o monorepo está no 5.x, de que dependem o
  `typescript-eslint` e o web. Por isso o `mobile/package.json` exclui `typescript` da checagem do
  Expo (`expo.install.exclude`), e o `expo-doctor` passa limpo. Subir o TypeScript é mudança do
  repositório inteiro, não do app.
- **A imagem do web não leva o mobile.** O `.dockerignore` exclui `mobile/`, e o `npm ci` da imagem
  ignora o workspace ausente: instala só o que o web e `shared/` usam. Se um dia o Dockerfile do web
  passar a copiar `mobile/package.json`, a imagem começa a baixar o React Native inteiro.

## O que o job `mobile` confere

Lint, `npx expo install --check` (as versões casam com a SDK), typecheck, os testes e o bundle do
Android pelo Metro. A segunda checagem existe por causa do Dependabot: ele cobre o npm da raiz,
workspaces incluídos, e pode propor subir o `react` ou o `react-native` do app sozinhos. Fora da
SDK o app compila e quebra no aparelho. **PR do Dependabot que acende esse passo não se mergeia:**
a troca de versão do app é sempre uma troca de SDK inteira (abaixo).

Por isso o `.github/dependabot.yml` **ignora** o que a SDK fixa: `expo`, `expo-*`, `jest-expo`,
`react-native`, os módulos nativos (`react-native-safe-area-context`, `react-native-screens`,
`react-native-webview`), `@react-native/*` e o `test-renderer` (acima). Foi o que derrubou o lote
da #158, que subia o React Native para 0.87 com a SDK 57 no 0.86. O `react` não entra na lista,
porque o web depende dele no 18, e continua guardado só por este passo. Módulo nativo novo no app
entra na lista do `dependabot.yml` no mesmo PR.

## Trocar de SDK

```bash
cd mobile
npx expo install expo@^<nova> --fix   # alinha expo, react, react-native e os módulos nativos
npx expo-doctor
```

Depois, rode `npm ls react` para confirmar que o React do Expo continua dentro de `mobile/`, e
`npx expo export --platform android` para conferir que o bundle fecha.
