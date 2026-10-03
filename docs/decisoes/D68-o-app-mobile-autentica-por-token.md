# D68 — O app mobile autentica por token opaco no banco, e entra por Google nativo, Apple e senha

## Justificativa

O app mobile (D67) não tem como usar a autenticação da web. O `shared/api-client` lê o
`XSRF-TOKEN` de `document.cookie` e manda `credentials: 'same-origin'`, e nenhum dos dois existe em
React Native. O login por provedor da web é um redirecionamento do Spring (`oauth2Login`) que
termina num cookie, e no app o login do Google é nativo e devolve um *ID token*. E a App Store,
pela guideline 4.8, exige uma opção equivalente ao Sign in with Apple em app que oferece login pelo
Google. Esta decisão responde a cada linha da regra 4 do CLAUDE.md para o cliente novo.

### Mecanismo: token opaco guardado como hash

O app recebe, no login, um **token opaco**: 32 bytes aleatórios, em base64url. O banco guarda só o
**SHA-256** dele, numa tabela própria (`mobile_token`), como já é feito com `login_token`. O
aparelho guarda o valor no **`expo-secure-store`** (Keychain no iOS, Keystore no Android), nunca em
`AsyncStorage`. Toda requisição do app manda `Authorization: Bearer <token>`.

**Alternativas descartadas:**

- **Manter a sessão por cookie no app.** O store de cookie nativo do React Native varia por
  plataforma, o CSRF teria que ser lido de outro jeito e as sessões vivem em memória: todo deploy
  derrubaria o login de todo aparelho. Na web isso é uma tela de login; no celular é o app que
  "esqueceu" a pessoa sem motivo.
- **JWT.** A vantagem do JWT é não consultar o banco, e aqui ela não existe: o
  `AccessGateInterceptor` já relê a conta a cada requisição, e revogar (troca de senha, logout,
  exclusão) exigiria uma lista de bloqueio. Seria uma consulta de qualquer jeito, mais uma chave de
  assinatura para guardar.
- **Par access + refresh token.** Com token opaco conferido no banco, o refresh não protege nada
  que a revogação já não proteja. É complexidade sem ganho.

**Validade:** o token expira depois de **90 dias sem uso** (`fos.mobile.token-idle-days`). O
`last_used_at` é atualizado no máximo uma vez por dia, para que a leitura não vire escrita em toda
requisição.

### Quem é o usuário: o token aponta para a identidade

A linha guarda o **`user_identity` por onde a pessoa entrou**, e não o `app_user`. É o que mantém o
`CurrentUserProvider` resolvendo pelo mesmo par `(provider, subject)`. Também faz o token seguir a
identidade quando o `mergeIdentityInto` a move para outra conta. Se apontasse para a conta, a
fusão deixaria um token vivo para uma conta que deixou de ser a da pessoa.

- **O `CurrentUserProvider` ganha um ramo novo** (`MobileTokenAuthentication`). É a lição da #51
  aplicada de propósito: tipo de autenticação que ele não reconhece vira 401 em silêncio.
- **Um filtro lê o `Authorization`** e monta o contexto de segurança **só para aquela requisição**.
  Nada é gravado em sessão, e o app nunca recebe `JSESSIONID`.
- **Requisição com `Authorization` é decidida só pelo token.** Se o cabeçalho existe e o token não
  vale, a resposta é 401, mesmo que a requisição traga também um cookie de sessão válido. Sem essa
  regra, um token qualquer no cabeçalho bastaria para pular o CSRF e cair na sessão do cookie.
- **O CSRF é dispensado só para requisição com `Authorization: Bearer`.** Navegador não anexa esse
  cabeçalho sozinho, então não há falsificação cross-site a proteger. O cookie continua exigindo
  CSRF como hoje.

### Emissão: um ponto só, como o `SessionLogin`

O `SessionLogin` existe para que todo login que a aplicação faz por conta própria cumpra os mesmos
deveres. O app tem o equivalente (`MobileTokens.issue`), e é o único lugar que cria token. Os
deveres do `SessionLogin` não se transferem um a um:

- **Rotacionar o id** não se aplica: não existe token anterior a fixar, todo token nasce sorteado.
- **Gravar o contexto** vira gravar a linha.
- **Registrar** é a própria linha, que é o que permite revogar depois.

Rotas novas, todas sem sessão e fora do CSRF por não terem cookie:

| Rota | Entrada | Reaproveita |
|---|---|---|
| `POST /api/mobile/auth/senha` | e-mail e senha | `PasswordAccessService.authenticate`, com o mesmo freio por e-mail e por `ClientIp` |
| `POST /api/mobile/auth/google` | ID token do Google | `AccountService.registerLogin("google", ...)` |
| `POST /api/mobile/auth/apple` | identity token, nonce, código de autorização e nome | `AccountService.registerLogin("apple", ...)` |
| `POST /api/mobile/auth/sair` | o próprio Bearer | revoga aquele token |

**Cadastro, confirmação e recuperação continuam na web.** O app abre a tela de cadastro, e o link
de confirmação sai de `fos.public-url` (D62) e abre no navegador, exigindo a senha (D61). Depois a
pessoa entra no app com e-mail e senha. Universal Links e App Links ficam para depois.

### Revogação: encerrar acesso é encerrar sessão e token

| Evento | Sessão (web) | Token (app) |
|---|---|---|
| Redefinição de senha | derrubada (hoje) | **todos os da conta revogados** |
| Logout | encerrada | aquele token revogado |
| `DELETE /api/me` | — | apagados com a conta |
| Bloqueio (`RECUSADO`) | **mantida** | **mantido** |

A redefinição de senha passa a chamar um ponto só que encerra as duas coisas. Senão, trocar a senha
depois de perder o celular deixaria o celular dentro, que é exatamente o caso em que a troca
importa.

O bloqueio segue a regra que já vale para a sessão: o `AccessGateInterceptor` relê o estado e
responde 403 `acesso_recusado` na requisição seguinte, e o app mostra o motivo. Revogar faria o
filtro responder 401 antes do portão e mandar a pessoa para o login, que foi o defeito da primeira
versão da #90. E a conta bloqueada continua podendo se excluir pelo app.

### O token do app não administra

Requisição autenticada por token mobile **não alcança `/api/admin/**`**, nem para conta `ADMIN`: a
resposta é 403. O app não tem tela de administração (#141), e um token num celular perdido é a
credencial mais provável de vazar. Restringir é o que mantém administrar exigindo o navegador. O
papel não muda: `/api/me` continua devolvendo `role`, e `AccountService.roleOf` segue sendo o
ponto único.

### Google nativo

O app obtém o ID token pelo login nativo do Google, e o backend confere:

- a assinatura contra as chaves públicas do Google;
- `iss`, `exp` e `aud`, que precisa ser um dos client IDs mobile configurados (Android e iOS são
  client IDs próprios, diferentes do da web);
- `email_verified`, que **só vale quando é `true`**. Ausente vale como não verificado (D63).

O `sub` do Google é o mesmo para qualquer client ID, e o provedor gravado é `google`, o mesmo da
web. Por isso entrar pelo app e pelo navegador cai **na mesma identidade**, sem depender de vínculo
por e-mail.

**O Facebook não entra no app.** Não é exigido por nenhuma loja, e ele não vincula conta (D63).
Levá-lo ao celular só multiplicaria as contas sem vínculo.

### Sign in with Apple

O botão aparece **só no iOS**, onde a guideline o exige. Android e web ficam como estão. O backend
confere o identity token contra as chaves públicas da Apple (`iss`, `exp`, `aud` igual ao bundle
id) e o **nonce**: o app sorteia o nonce, manda o hash para a Apple e o valor cru para o backend,
o que impede reaproveitar um token capturado.

- **Verificado é o que a Apple afirma**, como na D63: `email_verified` verdadeiro.
- **E-mail relay** (`privaterelay.appleid.com`, quando a pessoa escolhe esconder o endereço) é
  verificado, mas não é de mais ninguém. **Não vincula** a conta que a pessoa já tem pelo Google ou
  pela senha, e **nunca vira `ADMIN`** pela `fos.auth.owner-emails`. Quem quiser a mesma conta nas
  duas plataformas entra pelo Google no iPhone também, que o app oferece lado a lado. Ligar contas
  manualmente fica fora de escopo.
- **O nome só vem no primeiro login.** O app o repassa nessa chamada, e ele vira o `displayName` da
  identidade nova.
- **Excluir a conta revoga o acesso na Apple.** É a orientação da Apple para apps com Sign in with
  Apple. Para isso o backend troca o código de autorização do primeiro login por um refresh token
  da Apple, guarda-o numa tabela própria (`apple_credential`), como a senha vive em
  `password_credential` e nunca em `user_identity`, e o revoga no `DELETE /api/me`. Para fazer essa
  troca o backend precisa de uma credencial da Apple (chave `.p8`, Key ID, Team ID).

**A aplicação continua subindo sem segredo nenhum.** Sem os client IDs mobile do Google, a rota do
Google responde 404 e o app não mostra o botão, como o provedor sem `client-id` não aparece na web.
Sem a credencial da Apple, a rota da Apple também responde 404, e a Apple não aparece.
`GET /api/auth/providers` passa a dizer quais métodos o app pode oferecer.

### O que esta decisão não decide

- **Versão mínima do app**, coleta de uso no app e as rotas no `UsagePaths`: são da #139.
- **Push pelo servidor**: a notificação da #142 é local.
- **Documentação de comportamento.** CLAUDE.md (regra 4), `docs/autenticacao.md` e
  `docs/seguranca.md` descrevem o que existe, e mudam **no PR que implementa** (#139), não aqui.
  Até lá, a frase "a Apple ainda não" em `autenticacao.md` continua verdadeira.

## Revisar quando

- Se o app precisar de administração. A restrição de `/api/admin/**` cai, e a pergunta passa a ser
  segundo fator, não token.
- Se a Apple ou o Google mudarem a exigência de revogação ou de login equivalente.
- Se aparecer um token vazado em uso. Aí entram a rotação a cada uso e o vínculo com o aparelho,
  que esta versão deixou de fora por simplicidade.
- Se ligar contas pelo e-mail relay virar pedido real. A regra de vínculo por e-mail verificado não
  o cobre, e ligar à mão é uma decisão nova

---

[Índice das decisões](../README.md)
