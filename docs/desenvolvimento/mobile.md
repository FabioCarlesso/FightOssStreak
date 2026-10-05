# App mobile

Como rodar o app Android e iOS em desenvolvimento. O app vive em `mobile/`, em Expo + React Native
(D67), e reaproveita `@fos/domain`, `@fos/api-client` e `@fos/types` pelos workspaces do npm: a
regra pura não é reescrita no app (D17).

Hoje o app é só uma **tela de fumaça** (#140). Ela prova as duas costuras de que todo o resto vai
depender: a regra de `@fos/domain` rodando no React Native, e o `@fos/api-client` falando com o
backend por uma rota pública (`GET /api/auth/providers`). Telas reais e login entram na #141.

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

O valor é embutido no bundle quando o Metro sobe, então mudá-lo exige reiniciar o `expo start`. Sem a
variável, a tela de fumaça diz o que configurar, em vez de tentar um endereço que não existe no
aparelho.

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

## Trocar de SDK

```bash
cd mobile
npx expo install expo@^<nova> --fix   # alinha expo, react, react-native e os módulos nativos
npx expo-doctor
```

Depois, rode `npm ls react` para confirmar que o React do Expo continua dentro de `mobile/`, e
`npx expo export --platform android` para conferir que o bundle fecha.
