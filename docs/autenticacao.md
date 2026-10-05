# Autenticação e contas

Como se entra no app, quem administra, como se bloqueia e como se exclui uma conta. A história das
decisões vai da [D36](decisoes/D36-login-social-com-acesso-sob-aprovacao.md) (login sob aprovação) à
[D49](decisoes/D49-o-papel-vira-app-user-role.md) (papel em tabela e bloqueio reativo), passando
pela [D47](decisoes/D47-o-cadastro-abre-qualquer-um-cria.md), que abriu o cadastro, e pela
[D48](decisoes/D48-a-fila-de-aprovacao-e-desmontada.md), que desmontou o portão. O que se guarda de
cada conta está em [`privacidade/contas.md`](privacidade/contas.md).

## Como se entra

O app **exige login**, e há dois caminhos (D47). Sem sessão a API responde `401`.

- **Com e-mail e senha** (`POST /api/auth/cadastro`): qualquer um cria conta, sem fila e sem
  aprovação. A conta nasce **não verificada e sem sessão** — quem entra é o **link de confirmação**
  que chega por e-mail, vale **24 horas** e funciona uma vez. Abrir o link não confirma nada: ele
  leva a uma tela do app, e quem gasta o link (e abre a sessão) é o clique em *Confirmar meu e-mail*
  — do contrário, o varredor de links da caixa de entrada confirmaria pela pessoa. Senha de no
  mínimo **12 caracteres**, guardada só como hash; *esqueci minha senha* manda um link de **1 hora**
  que, ao ser usado, queima os links pendentes e derruba as sessões abertas da conta.
- **Por provedor externo** (Google; Facebook se configurado): entra direto. Quem chega por ali já
  teve a identidade verificada por um terceiro, e para essa pessoa o app continua sem ver senha
  nenhuma.

**Google e senha no mesmo endereço são a mesma conta**, desde que o e-mail esteja **verificado** dos
dois lados: a identidade nova se anexa à conta que já existe, com o progresso intacto, em vez de
criar uma conta vazia. E-mail não verificado nunca vincula nada — e **verificado é só o que o
provedor afirma** (D63): o Google manda `email_verified`, o Facebook não manda nada, então quem entra
pelo Facebook ganha conta própria, que não se anexa a outra pelo e-mail nem vira administração pela
`FOS_AUTH_OWNER_EMAILS`.

**Não há mais fila de aprovação, resumo horário nem link de e-mail como meio de login** (D48). Quem
entrava por link antes do cadastro aberto continua entrando: basta se cadastrar com o **mesmo
endereço** — o e-mail já está verificado, então a senha nova se anexa à conta antiga, com o
progresso onde estava.

E um degrau **antes** dos dois (D39): a landing oferece *Ver o app funcionando*, que abre uma conta
de demonstração temporária, já com progresso de exemplo, sem pedir nada a ninguém.

## No app mobile

O app Android e iOS entra **por token, não por sessão** (D68). Cada login devolve um token opaco,
que o app guarda no armazenamento seguro do aparelho e manda em `Authorization: Bearer` em toda
requisição; o banco guarda só o hash dele. Três portas:

- **E-mail e senha** (`POST /api/mobile/auth/senha`): as mesmas regras e respostas do login da web,
  com o mesmo freio. **Cadastro, confirmação e recuperação continuam na web** — o link de e-mail
  abre no navegador, e depois a pessoa entra no app.
- **Google nativo** (`POST /api/mobile/auth/google`): o app manda o ID token do Google, e o backend
  confere assinatura, emissor, prazo e audiência contra os client IDs do app. O `sub` é o mesmo da
  web, então é a **mesma identidade** de quem entra pelo navegador. `email_verified` só vale quando
  o Google o afirma (D63).
- **Sign in with Apple, só no iOS** (`POST /api/mobile/auth/apple`), exigido pela App Store em app
  que oferece Google. O backend confere o token e o **nonce**. O e-mail escondido pela Apple
  (*relay*) não vincula conta nem semeia administração: quem quiser a mesma conta nas duas
  plataformas entra pelo Google no iPhone também. Na exclusão da conta, o backend revoga o acesso
  na Apple.

O Facebook não entra no app. E o token do app **não administra**: as rotas de administração
respondem `403` a ele mesmo para conta `ADMIN` — administrar continua exigindo o navegador.

**O que encerra o token:** sair no aparelho (`POST /api/mobile/auth/sair`, só aquele), 90 dias sem
uso, **redefinir a senha** (todos os da conta, de qualquer porta) e excluir a conta. **Bloquear não
encerra**, pelo mesmo motivo de não derrubar a sessão: o app mostra o motivo em vez de voltar para
o login, e a conta bloqueada continua podendo se excluir.

Sair com um token que já morreu responde `401`, não `204`: o filtro recusa antes do controller,
porque com `Authorization` só o token decide, também no "sair". O aparelho já está fora nos dois
casos, e o `mobileLogout` do `api-client` resolve com `401` para o app apagar o token local. As
escritas em `mobile_token` são todas em massa (#148): o app dispara várias requisições ao abrir
com o mesmo token, e apagar ou atualizar pela entidade fazia a que perdesse a corrida responder
`500` no filtro.

**Configuração:** sem `FOS_MOBILE_GOOGLE_CLIENT_IDS`, a rota do Google responde `404` e o app não
mostra o botão; sem as quatro `FOS_MOBILE_APPLE_*`, o mesmo para a Apple. `GET /api/auth/providers`
diz quais existem em `mobileProviders`. A entrada por senha não depende de nada. A tabela está em
[`configuracao.md`](configuracao.md#variáveis).

## Quem administra

**Quem administra é o papel da conta, `app_user.role` (D49)**, e `GET /api/me` o devolve como `role`
(`ADMIN` ou `USUARIO`), decidido num ponto só (`AccountService.roleOf`). Promover e rebaixar é ação
da tela *Usuários*, sem deploy, e só conta com **e-mail verificado** — pelo provedor ou pela
confirmação do próprio app — pode virar `ADMIN`. `fos.auth.owner-emails` continua existindo como
**semente**: promove na subida e em todo login verificado, e nunca rebaixa. São dois papéis e ponto;
permissão granular (perfil por recurso) segue fora de escopo.

Com uma conta `ADMIN`, o menu mostra *Usuários* (`/usuarios`), com busca, filtros e paginação sobre
as contas do app, *Feedback* com a fila abaixo do formulário e *Painel* (ver
[`operacao.md`](operacao.md)). As rotas estão em [`api.md`](api.md#administração).

## Bloqueio

**Conta abusiva se bloqueia, e o bloqueio vale na hora.** Quem administra move a conta para
`RECUSADO` pela mesma tela, e a próxima requisição da conta recebe `403` com o código
`acesso_recusado`, que é a tela de conta bloqueada — inclusive numa aba que já estava aberta,
porque o portão relê o estado a cada requisição, e pelas duas portas, senha e provedor. Não é a fila
de aprovação de volta (D48): a fila barrava todo mundo antes de saber quem era, o bloqueio barra
alguém depois de haver motivo, é reversível e fica registrado com quem decidiu e por quê. **Conta
bloqueada continua podendo se excluir** (`DELETE /api/me`) — bloquear não pode virar sequestro de
dado pessoal. Ninguém bloqueia ou rebaixa a si mesmo, nem a última conta de administração: nesses
casos a API responde `409` e nada muda.

## Exclusão de conta

Em *Sua conta*, ou `DELETE /api/me`. Apaga conta, identidade, hash da senha, links pendentes,
progresso, streak, agenda, drills, anotações e aceite — em uma transação, sem volta.

## Configuração

**Habilitar o cadastro com senha**: crie a chave no provedor de envio, verifique o domínio do
remetente e defina `FOS_EMAIL_API_KEY` e `FOS_EMAIL_FROM` (e `FOS_PUBLIC_URL`, ver
[segurança](seguranca.md#links-de-e-mail-fos_public_url)). Sem elas o app sobe igual,
`POST /api/auth/cadastro` responde `503` e a tela diz que o cadastro não existe neste ambiente —
sobra a entrada por provedor. É a única credencial cuja ausência tira uma **porta de entrada**
inteira, e não só um botão: o cadastro *é* o e-mail de confirmação.

**Habilitar um provedor na web** (Google e Facebook; a Apple não entra na web — ver D36 e, para o
app, a seção abaixo):

1. Crie o app no provedor e cadastre o redirect URI
   `https://<seu-domínio>/api/login/oauth2/code/google` (e o equivalente para `facebook`). O
   caminho fica sob `/api` porque é o único que o nginx encaminha ao backend (D23/D24).
2. Defina as variáveis do provedor e `FOS_OWNER_EMAILS` com o seu e-mail.
3. Suba. Provedor sem credencial não é registrado: não é uma tela de login com botão que falha — o
   provedor simplesmente não existe, e a aplicação sobe sem segredo nenhum (é o modo dev e o CI).

Em dev, o fluxo real do OAuth é mais simples pelo Compose (`docker compose up --build`) do que pelo
Vite: o provedor devolve o browser para a origem que o backend recebeu, e atrás do dev server essa
origem é a porta do backend, não a do Vite.

**`FOS_OWNER_EMAILS` é semente, não fonte da verdade (D49).** Quem administra é `app_user.role`, no
banco, mudado pela tela *Usuários* sem deploy. A variável **promove** — na subida e em todo login
com e-mail verificado — e **nunca rebaixa**: tirar um endereço dela não tira o papel de ninguém,
senão um deploy com a lista mal preenchida viraria perda de acesso à administração. Em banco novo,
ela é o único jeito de existir um primeiro `ADMIN`, e num ambiente que ficou sem nenhum (a última
conta de administração se excluiu, por exemplo) ela é a saída de emergência: preencher e reiniciar.
Vazia num banco novo, o app sobe sem administração nenhuma — ninguém vê contas, feedback nem
métricas, e o app funciona igual para todo mundo. É ela também que faz a conta do autor adotar o
progresso pré-existente (D36). Preenchê-la **depois** de já ter entrado uma vez funciona: a regra
vale em todo login, então o login seguinte reconhece a conta, promove e adota o progresso.

## Demonstração pública

1. Entre no app com a conta que vai servir de molde e **cure a demonstração usando o próprio app**:
   conclua nós, registre drills nos dias que fizerem sentido, escreva as anotações fixadas que o
   visitante vai ler. Não há script para isso — o estado é o que o app grava.

   > ⚠️ **Tudo que você escrever nessa conta vira público.** Anotação fixada e nota de drill são
   > copiadas para cada demonstração, então qualquer visitante lê o que está ali. É a única
   > superfície do app onde texto seu é publicado sem que você publique nada — escreva pensando em
   > quem vai receber o link, e prefira uma conta separada se a sua for também a de uso real.
2. Defina `FOS_DEMO_TEMPLATE_EMAIL` com o e-mail **verificado** dessa conta e suba.

Cada visita passa a receber uma **cópia** desse estado, numa conta descartável, com as datas
deslocadas para que a agenda caia em torno de hoje. A conta-modelo nunca recebe visitante: ela pode
ser inclusive a sua conta de dono, que a cópia não herda poder nenhum. As demonstrações vencidas são
apagadas quando alguém abre a próxima. A conta de demonstração não aparece na lista de *Usuários* e
não aceita nenhuma ação de administração.

**`FOS_DEMO_TEMPLATE_EMAIL` vazia desliga a demonstração.** O botão não aparece na landing e
`POST /api/demo/sessao` responde 404 — a aplicação sobe igual. Se ela apontar para um endereço que
não tem identidade com **e-mail verificado**, o efeito é o mesmo: o recurso simplesmente não existe
naquele ambiente, em vez de aparecer e falhar no clique.

Não confundir com o *modo demonstração* da árvore, que não grava nada — ver
[`regras-de-negocio.md`](regras-de-negocio.md#modo-demonstração-da-árvore).
