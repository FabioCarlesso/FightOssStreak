# FightOssStreak (FOS)

Ferramenta pessoal de revisão e retenção do que é aprendido no tatame, com mecânicas de gamificação (currículo em árvore, quiz, streak, repetição espaçada).

> ⚠️ Este projeto não ensina jiu-jitsu e não substitui instrução presencial com professor qualificado. Ver aviso completo em [`docs/06-disclaimer-responsabilidade.md`](docs/06-disclaimer-responsabilidade.md).

## O que já funciona

MVP web ponta a ponta: árvore de currículo com desbloqueio progressivo, detalhe do nó com conceito e quiz conceitual corrigido no servidor, registro de drill, anotações pessoais por nó, streak e agenda de revisão por repetição espaçada. A tela `/progresso` mede os critérios de sucesso do MVP sobre o uso real. A raiz (`/`) é a landing pública que apresenta o projeto; o app começa em `/hoje`, atrás do aceite do aviso.

| Camada | Estado |
|---|---|
| Currículo (46 nós, 9 módulos) | Transcrito como dado versionado em `backend/src/main/resources/curriculum/` |
| Quiz conceitual | Escrito para **M0 a M3** (25 nós, 91 perguntas). Os outros 21 nós, de M4 a M8, estão pendentes de curadoria |
| Vídeos do YouTube | **M0 e M1 catalogados (11/46)** pelo script, pendentes de conferência assistindo (D21 em [`07-decisoes.md`](docs/07-decisoes.md)). M2–M8 seguem sem vídeo — ver [instruções](backend/src/main/resources/curriculum/README.md) |
| Clipes complementares | **7 clipes** da própria academia em M1.3, M1.5 e M1.6 (D32). O canônico ensina, o clipe lembra — no máximo 4 por nó |
| Anotações por nó | Anotação fixada junto ao conceito, mais o histórico do que foi anotado a cada drill (#45) |
| Contas | **Cadastro aberto** com e-mail e senha, confirmado por link, mais login por Google e Facebook (D47/D48). Exclusão de conta incluída. Apple pendente |
| Demonstração pública | Um botão na landing abre o app numa **conta temporária** com dados de exemplo, que grava de verdade e some em duas horas (D39). Depende de `FOS_DEMO_TEMPLATE_EMAIL` |
| Backend | Spring Boot + Flyway + Spring Security, API documentada em OpenAPI |
| Web | React + Vite |
| Landing | Pública em `/`, estática e sem chamada de API, com prints das telas reais (D33) |
| Mobile | Não iniciado (fase posterior, D4) |

### Modo demonstração

> Não confundir com a **conta de demonstração** (D39), que é outra coisa e fica do outro lado do
> portão: aquela é um link público que abre o app numa conta temporária e **grava de verdade**;
> esta é inspeção do autor, já logado, e **não grava nada**.

Na árvore (`/arvore`), o botão **Demonstração**, no card *Progresso*, abre os 46 nós de todos os
módulos para inspeção ignorando os pré-requisitos — serve para revisar conceito, vídeo e redação
das perguntas sem passar no quiz de cada nó anterior. Com o modo ligado, uma faixa no topo diz
isso em todas as telas e oferece o desligar.

**A demonstração não grava**: em nó que estaria bloqueado o quiz aparece só para leitura e o
registro de drill fica fora, então progresso, streak e agenda de revisão ficam intactos, e os
contadores da árvore continuam mostrando o que está travado de verdade (D31). O estado vive na
sessão do navegador — sobrevive a um F5, não a uma aba nova.

### Landing

A raiz (`/`) apresenta o projeto para quem chega pelo link: o que é, como funciona, prints das
telas reais e os botões de entrar. Ela **não depende da API para renderizar**: aparece inteira mesmo
com o backend frio, que é o caso comum de cold start. A única coisa que ela pergunta ao servidor é
se este ambiente tem demonstração configurada (D39) — sem resposta, a página é exatamente a mesma,
só sem aquele botão.

O aceite do aviso não mudou de lugar: ele continua sendo o primeiro a decidir em `/hoje`,
`/arvore`, `/no/:code` e `/progresso`. Quem já entrou no app uma vez passa direto da raiz para a
agenda do dia; `/?ver=apresentacao` traz a apresentação de volta.

Os prints saem de `node scripts/capturar-prints.mjs --semear`, com o app rodando — o procedimento
está em [`docs/10-prints-da-landing.md`](docs/10-prints-da-landing.md). **PR que mexe na aparência
da árvore, do nó, do drill ou da tela inicial precisa refazer o print correspondente.**

## Rodando

Dois modos. **Docker** para usar o app; **dev** para mexer no código.

### Com Docker (só precisa de Docker)

```bash
docker compose up --build
```

Abra <http://localhost:8081>. O app pede login e liberação do autor antes de qualquer coisa
(ver [Acesso e contas](#acesso-e-contas)) e, para a conta liberada, o aceite do aviso de
responsabilidade.

Sobe `db` (Postgres), `backend` e `web`. O browser fala só com o `web`: é o nginx dele que
encaminha `/api` para o backend — mesma topologia de produção, e por isso nenhuma requisição
sai para `:8080`. Os dados ficam no volume `fos-pgdata` e sobrevivem a `docker compose down`;
`docker compose down -v` zera.

```bash
docker compose logs -f backend     # logs
docker compose restart backend     # reiniciar sem perder dados
docker compose down -v             # apagar tudo, inclusive o banco
```

O backend também publica `:8080` no host, só para depurar e para o Swagger
(<http://localhost:8080/swagger-ui.html>). O app não usa essa porta.

As portas são parametrizadas — a imagem não tem porta fixa dentro dela:

| Variável | Default | O que faz |
|---|---|---|
| `WEB_HOST_PORT` | `8081` | porta publicada no host |
| `PORT` | `80` | porta em que o nginx escuta **dentro** do container |

```bash
WEB_HOST_PORT=3000 docker compose up   # app em http://localhost:3000
PORT=9090 docker compose up web        # nginx escuta 9090 dentro do container
```

### Modo dev (Java 21 + Node 22)

Hot reload; é o fluxo para desenvolver. Não roda em container.

```bash
npm install

# terminal 1 — API em :8080 (perfil dev, H2 em memória, sem precisar de Docker)
npm run dev:backend

# terminal 2 — web em :5173, com proxy de /api
npm run dev:web
```

Abra <http://localhost:5173>.

O perfil `dev` usa H2 em memória — os dados somem ao reiniciar. Para desenvolver contra
Postgres sem subir a stack inteira:

```bash
docker compose up -d db
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

Como o app exige login e `localhost` não tem provedor nem envio de e-mail configurados, use as
contas de teste para chegar a qualquer tela autenticada — inclusive a do dono:

```bash
node scripts/seed-dev-users.mjs            # uma vez, com o schema já migrado
node scripts/mint-dev-login.mjs aluno@teste.local   # imprime a URL de entrada
```

Roteiro completo, e por que isto não alcança produção, em
[`docs/14-contas-de-teste-local.md`](docs/14-contas-de-teste-local.md).

## Deploy na Railway

Dois serviços a partir deste repo (papéis **backend** e **web**) mais um Postgres
gerenciado. **As imagens são as mesmas do Compose** — o que muda é só o ambiente. Nenhum
host, porta ou credencial está fixo dentro delas, e o deploy não exige editar
`application.yml` nem os Dockerfiles.

**Só o `web` recebe domínio público.** O backend fica acessível apenas pela rede privada,
atrás do nginx (D24).

Passo a passo:

1. Criar o projeto e adicionar o **Postgres** (template gerenciado da Railway). Manter o
   nome padrão `Postgres`: as referências `${{Postgres.PGHOST}}` da tabela casam pelo nome
   do serviço.
2. Criar o serviço **backend** a partir deste repo. Em *Settings → Config as code*, apontar
   para `backend/railway.json` — ele já traz `dockerfilePath` e o `healthcheckPath`.
   Não mexer em *Root Directory*: as duas imagens constroem a partir da raiz do repo.
3. Criar o serviço **web** do mesmo jeito, com `web/railway.json`.
4. Preencher as variáveis da tabela abaixo em cada serviço, **backend primeiro**: até ele
   subir no Postgres, o `web` não tem o que proxiar.
5. Gerar domínio público **só no `web`** (*Settings → Networking → Generate Domain*), com
   *target port* igual ao `PORT` do serviço.

Os nomes dos serviços são livres, mas não são cosméticos: o domínio da rede privada sai
deles. Um serviço chamado `FOS-backend` atende em `fos-backend.railway.internal`, e é esse
valor que vai no `BACKEND_ORIGIN` do `web`. Confirme o domínio em *Settings → Networking →
Private Networking* do backend antes de colar.

Variáveis, e o que cada uma vale nos dois ambientes:

| Variável | Serviço | Compose local | Railway |
|---|---|---|---|
| `PORT` | web / backend | `80` / `8080` | `8080` nos dois, definida à mão |
| `BACKEND_ORIGIN` | web | `http://backend:8080` | `http://<serviço-backend>.railway.internal:8080` |
| `NGINX_RESOLVER` | web | `127.0.0.11 ipv6=off` | `[fd12::10] ipv6=on` |
| `SPRING_PROFILES_ACTIVE` | backend | `postgres` | `postgres` |
| `FOS_DB_URL` | backend | `jdbc:postgresql://db:5432/fos` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `FOS_DB_USER` | backend | `fos` | `${{Postgres.PGUSER}}` |
| `FOS_DB_PASSWORD` | backend | `fos` | `${{Postgres.PGPASSWORD}}` |
| `SERVER_ADDRESS` | backend | `0.0.0.0` | `::` |
| `TZ` | web / backend | `America/Sao_Paulo` | `America/Sao_Paulo` |
| `FOS_PROXY_TRUSTED_HOPS` | backend | `1` | `3` — **medido**, ver abaixo |
| `VITE_PUBLIC_URL` | web (**build**) | — | URL pública do app, ex. `https://fos.up.railway.app` |
| `FOS_OWNER_EMAILS` | backend | vazia | semente de administração: e-mails que viram `ADMIN` na subida, separados por vírgula |
| `FOS_AUTH_PROVIDERS_GOOGLE_CLIENT_ID` | backend | — | id do app no Google |
| `FOS_AUTH_PROVIDERS_GOOGLE_CLIENT_SECRET` | backend | — | segredo do app no Google |
| `FOS_AUTH_PROVIDERS_FACEBOOK_CLIENT_ID` | backend | — | id do app no Meta for Developers |
| `FOS_AUTH_PROVIDERS_FACEBOOK_CLIENT_SECRET` | backend | — | segredo do app no Meta for Developers |
| `FOS_EMAIL_API_KEY` | backend | — | chave do provedor de envio (Resend) |
| `FOS_EMAIL_FROM` | backend | — | remetente, em domínio verificado |
| `FOS_DEMO_TEMPLATE_EMAIL` | backend | — | e-mail verificado da conta-modelo da demonstração |
| `FOS_USAGE_ENABLED` | backend | `true` | `false` desliga a coleta de uso (D50) por inteiro: nada é gravado **e** o endpoint responde 503, que é como o navegador para de mandar evento |
| `FOS_USAGE_GEOIP_DATABASE` | backend | vazia | caminho do CSV local de faixas de IP → país; vazia = país desconhecido |
| `FOS_USAGE_RETENTION_DAYS` | backend | `90` | retenção da tabela **crua** de eventos; o agregado não expira |
| `FOS_USAGE_DAILY_CAP` | backend | `5000` | teto de acessos gravados por dia. Uma linha custa 273 bytes medidos, então 5 000 × 90 dias ≈ 123 MB no pior caso — baixe se o disco for apertado |
| `FOS_USAGE_CRON` | backend | `0 17 3 * * *` | quando o job agrega e expurga; `-` desliga só o agendamento |

Detalhes que não são óbvios:

- **`PORT` explícita, mesmo a plataforma sabendo injetá-la.** No backend porque o
  `BACKEND_ORIGIN` do nginx aponta para uma porta fixa: serviço sem domínio público não tem
  garantia de receber a variável, e se receber outra o proxy bate em porta errada. No web
  para que o *target port* do domínio tenha um valor conhecido para casar.
- **`SPRING_PROFILES_ACTIVE` esquecida não quebra nada — e é justamente o problema.** O
  `application.yml` tem `profiles.default: dev`, que é H2 em memória. O deploy fica verde, o
  healthcheck passa, a API responde, e todo progresso some no deploy seguinte. Sinal de que
  o perfil pegou: as migrations do Flyway nos logs da subida.
- **`VITE_PUBLIC_URL` é de build, não de runtime.** Ela só existe para as tags `og:url` e
  `og:image` da prévia de link, que exigem URL absoluta. Mudá-la pede um novo deploy do `web`, e
  sem ela as duas tags simplesmente não são emitidas — o link fica sem imagem de prévia e nada
  mais quebra (D33).
- **`DATABASE_URL` não serve.** A Railway a expõe no formato `postgresql://user:pass@host/db`,
  que não é uma URL JDBC e o Spring não aceita. Daí montar `FOS_DB_URL` a partir das variáveis
  de referência do Postgres.
- **`SERVER_ADDRESS=::`.** A rede privada da Railway resolve IPv6, e ambientes criados antes
  de 16/10/2025 são IPv6-only. Com `0.0.0.0` o backend fica invisível para o nginx.
- **`NGINX_RESOLVER`.** O nginx resolve o upstream no start e cacheia; como o IP do backend
  muda a cada deploy, o `proxy_pass` vai por variável e o `resolver` re-resolve em runtime.
  Confirme o endereço na doc da Railway antes de colar — é o tipo de detalhe que muda.
- **`TZ`.** O streak usa a data do servidor. Em UTC, um drill às 22h de Brasília contaria como
  do dia seguinte.
- **`FOS_PROXY_TRUSTED_HOPS` é o que faz o freio por IP existir.** Ela diz quantos endereços a
  cadeia do `X-Forwarded-For` ganha até chegar ao backend — cada salto anexa um ao fim, e é de trás
  para frente que se acha quem está navegando. No Compose só o nginx está na frente (`1`).
  **Na Railway são `3`, e isto foi medido, não deduzido** — o log de acesso do nginx registra
  `$http_x_forwarded_for` como ele chega, e em produção ele chega assim:

  ```
  100.64.0.11 - - [...] "GET /robots.txt" 200 "-" "FOS-SONDA" "104.28.228.100, 152.233.23.193"
                                                               └ visitante ┘  └ borda mia1 ┘
  ```

  A borda da plataforma **descarta** o `X-Forwarded-For` de quem chama (a sonda mandou `9.9.9.9`
  e ele não chegou) e entrega dois elementos: o visitante e ela mesma (`152.233.22.0/23`, rede
  CDN77 — o `x-railway-edge: mia1` da resposta). O nginx anexa o próprio peer (`100.64.0.x`,
  CGNAT da Railway) e o backend recebe três. Com `2` a chave seria o nó de borda —
  **o mesmo para todo mundo que entra por ele**.

  **Errar o número não é mais silencioso.** O backend conta os elementos que chegam e, quando o
  número não bate com o declarado, escreve um `WARN` nomeando `FOS_PROXY_TRUSTED_HOPS`, o valor
  configurado e o observado (uma vez por hora, não a cada requisição). Foi o que faltou no deploy
  da #96: a variável não subiu junto com o código, o app ficou com o default `1` e nada apareceu.
  O aviso **não manda copiar o número observado**, nos dois sentidos: cadeia mais longa é o que se
  vê quando alguém escreve `X-Forwarded-For` sem borda que saneie, e cadeia mais curta, num backend
  declarado acima da topologia real, também pode trazer um elemento forjado dentro dela. O valor
  certo é quantos saltos **seus** a requisição atravessa — confira antes de mudar.

  **Errar o número não abre a porta em silêncio**, para nenhum dos dois lados: pequeno demais e
  todo mundo cai na mesma chave, e os freios passam a recusar gente legítima; grande demais e a
  cadeia fica mais curta que o configurado, e aí vale o **último** elemento — o endereço que o
  salto mais próximo escreveu —, nunca a cabeça da lista, que é o que quem chama escreveu.
  Nos dois casos é ruído visível, não bypass — mas é degradação de verdade, então **a variável
  entra no mesmo deploy que este código**: sem ela o default `1` vale, e o default é o Compose. Pôr uma CDN na frente do domínio acrescenta um salto
  e pede o número novo. Ver D51 em [`07-decisoes.md`](docs/07-decisoes.md).
- **`FOS_OWNER_EMAILS` é semente, não fonte da verdade (D49).** Quem administra é `app_user.role`,
  no banco, mudado pela tela *Usuários* sem deploy. A variável **promove** — na subida e em todo
  login com e-mail verificado — e **nunca rebaixa**: tirar um endereço dela não tira o papel de
  ninguém, senão um deploy com a lista mal preenchida viraria perda de acesso à administração.
  Em banco novo, ela é o único jeito de existir um primeiro `ADMIN`, e num ambiente que ficou sem
  nenhum (a última conta de administração se excluiu, por exemplo) ela é a saída de emergência:
  preencher e reiniciar. Vazia num banco novo, o app sobe sem administração nenhuma — ninguém vê
  contas, feedback nem métricas, e o app funciona igual para todo mundo. É ela também que faz a
  conta do autor adotar o progresso pré-existente (D36). Preenchê-la **depois** de já ter entrado
  uma vez funciona: a regra vale em todo login, então o login seguinte reconhece a conta, promove e
  adota o progresso.
- **`FOS_DEMO_TEMPLATE_EMAIL` vazia desliga a demonstração.** O botão não aparece na landing e
  `POST /api/demo/sessao` responde 404 — a aplicação sobe igual. Se ela apontar para um endereço
  que não tem identidade com **e-mail verificado**, o efeito é o mesmo: o recurso simplesmente não
  existe naquele ambiente, em vez de aparecer e falhar no clique.
- **Provedor sem credencial não é registrado.** Não é uma tela de login com botão que falha: o
  provedor simplesmente não existe, e a aplicação sobe sem segredo nenhum (é o modo dev e o CI).
- **Sem `FOS_EMAIL_API_KEY` não há cadastro por senha.** O cadastro *é* o e-mail de confirmação,
  então sem provedor de envio ele responde **503** e a tela diz isso — mesma regra dos provedores de
  login. A aplicação sobe igual, e dev e CI continuam sem segredo nenhum.
- **`FOS_PUBLIC_URL` não existe mais.** Ela servia só ao resumo horário da fila (D38), que saiu com
  o portão de aprovação (D48). Os links de confirmação e redefinição saem da URL da própria
  requisição.
- **A base de geolocalização é baixada no build da imagem, não versionada (D50).** O
  `backend/Dockerfile` puxa o [DB-IP Lite](https://db-ip.com) (CC BY 4.0) do mês corrente, com recuo
  para o mês anterior, e já aponta `FOS_USAGE_GEOIP_DATABASE` para ele — **na Railway não há o que
  configurar**. Quem fala com o db-ip.com é a máquina de build; nenhuma chamada a serviço externo
  acontece por requisição, e o IP de quem usa o app não sai daqui. O download **nunca derruba o
  build**: terceiro fora do ar vira base ausente, o app sobe igual e coleta tudo menos país, que
  vira `ZZ`. É assim que dev e CI rodam — o CI passa `--build-arg GEOIP=false` de propósito. Para
  usar outra base, aponte a variável para um CSV `início,fim,país[,região]` (aceita `.gz`); para
  ficar sem nenhuma, defina-a vazia. **Crédito**: dado de país por DB-IP, sob CC BY 4.0.
- **Nada da coleta guarda endereço de IP.** O IP deriva país e compõe a chave de visita, e é
  descartado no mesmo método: não há coluna, não há log, e há teste que reprova o build se uma
  coluna com cara de IP aparecer em qualquer migration. Detalhes em `docs/11-privacidade.md`.
- **As credenciais `fos/fos/fos` do Compose são de conveniência local.** Não reaproveitar.

## Acesso e contas

O app **exige login**, e há dois caminhos (D47):

- **Com e-mail e senha** (`POST /api/auth/cadastro`): qualquer um cria conta, sem fila e sem
  aprovação. A conta nasce **não verificada e sem sessão** — quem entra é o **link de confirmação**
  que chega por e-mail, vale **24 horas** e funciona uma vez. Abrir o link não confirma nada: ele
  leva a uma tela do app, e quem gasta o link (e abre a sessão) é o clique em *Confirmar meu
  e-mail* — do contrário, o varredor de links da caixa de entrada confirmaria pela pessoa. Senha de no mínimo **12 caracteres**,
  guardada só como hash; *esqueci minha senha* manda um link de **1 hora** que, ao ser usado, queima
  os links pendentes e derruba as sessões abertas da conta.
- **Por provedor externo** (Google; Facebook se configurado): entra direto. Quem chega por ali já
  teve a identidade verificada por um terceiro, e para essa pessoa o app continua sem ver senha
  nenhuma.

**Google e senha no mesmo endereço são a mesma conta**, desde que o e-mail esteja **verificado** dos
dois lados: a identidade nova se anexa à conta que já existe, com o progresso intacto, em vez de
criar uma conta vazia. E-mail não verificado nunca vincula nada.

**Não há mais fila de aprovação, resumo horário nem link de e-mail como meio de login** (D48). Quem
entrava por link antes do cadastro aberto continua entrando: basta se cadastrar com o **mesmo
endereço** — o e-mail já está verificado, então a senha nova se anexa à conta antiga, com o progresso
onde estava.

**Quem administra é o papel da conta, `app_user.role` (D49)**, e `GET /api/me` o devolve como `role`
(`ADMIN` ou `USUARIO`), decidido num ponto só (`AccountService.roleOf`). Promover e rebaixar é ação
da tela *Usuários*, sem deploy, e só conta com **e-mail verificado** — pelo provedor ou pela
confirmação do próprio app — pode virar `ADMIN`. `fos.auth.owner-emails` continua existindo como
**semente**: promove na subida e em todo login verificado, e nunca rebaixa. São dois papéis e ponto;
permissão granular (perfil por recurso) segue fora de escopo.

**Conta abusiva se bloqueia, e o bloqueio vale na hora.** Quem administra move a conta para
`RECUSADO` pela mesma tela, e a próxima requisição da conta recebe `403` com o código
`acesso_recusado`, que é a tela de conta bloqueada — inclusive numa aba que já estava aberta, porque
o portão relê o estado a cada requisição, e pelas duas portas, senha e provedor. Não é a fila de aprovação de volta (D48): a fila barrava todo mundo antes de saber quem
era, o bloqueio barra alguém depois de haver motivo, é reversível e fica registrado com quem decidiu
e por quê. **Conta bloqueada continua podendo se excluir** (`DELETE /api/me`) — bloquear não pode
virar sequestro de dado pessoal. Ninguém bloqueia ou rebaixa a si mesmo, nem a última conta de
administração: nesses casos a API responde `409` e nada muda.

E um degrau **antes** dos dois (D39): a landing oferece *Ver o app funcionando*, que abre uma
conta de demonstração temporária, já com progresso de exemplo, sem pedir nada a ninguém.

Sem sessão a API responde `401`. A decisão e o porquê estão em
[`docs/07-decisoes.md`](docs/07-decisoes.md) — a história completa vai da D36 (login sob aprovação) à
D49 (papel em tabela e bloqueio reativo), passando pela D47, que abriu o cadastro, e pela D48, que
desmontou o portão.

**Habilitar o cadastro com senha**: crie a chave no provedor de envio, verifique o domínio do
remetente e defina `FOS_EMAIL_API_KEY` e `FOS_EMAIL_FROM`. Sem elas o app sobe igual, `POST
/api/auth/cadastro` responde `503` e a tela diz que o cadastro não existe neste ambiente — sobra a
entrada por provedor. É a única credencial cuja ausência tira uma **porta de entrada** inteira, e
não só um botão: o cadastro *é* o e-mail de confirmação.

**Habilitar um provedor** (Google e Facebook nesta fase; a Apple ainda não — ver D36):

1. Crie o app no provedor e cadastre o redirect URI
   `https://<seu-domínio>/api/login/oauth2/code/google` (e o equivalente para `facebook`).
   O caminho fica sob `/api` porque é o único que o nginx encaminha ao backend (D23/D24).
2. Defina as variáveis do provedor e `FOS_OWNER_EMAILS` com o seu e-mail.
3. Suba. Sem credencial nenhuma o app continua subindo — a tela de login é que fica sem botão.

**Habilitar a demonstração pública**:

1. Entre no app com a conta que vai servir de molde e **cure a demonstração usando o próprio app**:
   conclua nós, registre drills nos dias que fizerem sentido, escreva as anotações fixadas que o
   visitante vai ler. Não há script para isso — o estado é o que o app grava.

   > ⚠️ **Tudo que você escrever nessa conta vira público.** Anotação fixada e nota de drill são
   > copiadas para cada demonstração, então qualquer visitante lê o que está ali. É a única
   > superfície do app onde texto seu é publicado sem que você publique nada — escreva pensando
   > em quem vai receber o link, e prefira uma conta separada se a sua for também a de uso real.
2. Defina `FOS_DEMO_TEMPLATE_EMAIL` com o e-mail **verificado** dessa conta e suba.

Cada visita passa a receber uma **cópia** desse estado, numa conta descartável, com as datas
deslocadas para que a agenda caia em torno de hoje. A conta-modelo nunca recebe visitante: ela pode
ser inclusive a sua conta de dono, que a cópia não herda poder nenhum. As demonstrações vencidas
são apagadas quando alguém abre a próxima.

**Ler o feedback de quem usa**: entre com uma conta `ADMIN` e abra *Feedback*; a fila aparece abaixo
do formulário. Também está em `GET /api/admin/feedback`.

**Ver se o app está sendo usado**: com uma conta `ADMIN`, o menu mostra *Painel*
(`/admin/painel`), com acessos por dia, funil de seis degraus, origem, perfil de uso, telas mais
abertas e três números sobre as contas — em 7, 30 ou 90 dias, sempre com o comparativo do período
anterior. O painel é **agregado e de ninguém**: ele lê só a contagem diária (`usage_daily`), nunca a
tabela crua de eventos, e nenhum campo da resposta identifica alguém (`docs/11-privacidade.md`). O
período termina **ontem** — o dia corrente ainda recebe evento e só entra na contagem depois de
fechado, então um painel recém-instalado fica zerado até o job diário rodar pela primeira vez.

**Administrar as contas**: com uma conta `ADMIN`, o menu mostra *Usuários* (`/usuarios`), com busca,
filtros e paginação sobre as contas do app. As rotas por trás, todas sob `/api/admin/**` e portanto
`403` para quem não administra:

| Rota | O que faz |
|---|---|
| `GET /api/admin/painel` | acessos, funil, origem e perfil de uso; `dias` é 7, 30 ou 90 (qualquer outro valor é `400`) |
| `GET /api/admin/usuarios` | lista as contas, da mais nova para a mais antiga; filtros `status`, `role` e `verificado`, busca por trecho de e-mail ou rótulo, `page`/`size` com teto de 100 |
| `POST /api/admin/usuarios/{id}/role` | promove a `ADMIN` ou rebaixa a `USUARIO` |
| `POST /api/admin/usuarios/{id}/status` | bloqueia (`RECUSADO`) ou devolve o acesso (`APROVADO`), com motivo |
| `GET /api/admin/feedback` | fila de feedback |
| `POST /api/admin/feedback/{id}/status` | decide um feedback |

A conta de demonstração (D39) não aparece na lista e não aceita nenhuma dessas ações.

**Excluir a conta**: em *Sua conta*, ou `DELETE /api/me`. Apaga conta, identidade, hash da senha,
links pendentes, progresso, streak, agenda, drills, anotações e aceite — em uma transação, sem
volta.

Em dev, o fluxo real do OAuth é mais simples pelo Compose (`docker compose up --build`) do que
pelo Vite: o provedor devolve o browser para a origem que o backend recebeu, e atrás do dev server
essa origem é a porta do backend, não a do Vite.

## Testes

```bash
npm test                    # regras de shared/domain + scripts + fluxos de UI do web
cd backend && ./mvnw test   # regras, integridade do currículo e fluxo de ponta a ponta
npm run typecheck           # todos os workspaces TypeScript
```

O teste de integridade do currículo falha se houver ciclo de pré-requisitos, referência quebrada, código duplicado ou quiz malformado — é o que torna seguro editar a árvore em um PR.

Do lado do web, os testes cobrem os três fluxos que decidem se o produto é usável — o aceite do disclaimer, o quiz e o registro de drill (D29) — mais a árvore em modo demonstração e a troca de nó dentro de `/no/:code`, onde o risco é o modo vazar para o uso normal e o estado de um nó aparecer no seguinte (D31). Rodam em jsdom com o cliente de API mockado — nenhum toca a rede. Para iterar em um deles, `npm run test:watch --workspace @fos/web`.

## Lint e formatação

```bash
npm run lint                       # ESLint + Prettier em web/ e shared/*
npm run lint:fix                   # corrige o que é corrigível
cd backend && ./mvnw spotless:check   # formatação e imports do Java
cd backend && ./mvnw spotless:apply   # corrige
```

As duas verificações rodam no CI **antes** dos testes, dentro dos jobs `backend` e `web` — falha rápida e barata primeiro. Ficam de fora do lint os arquivos gerados (`shared/types/generated/`, `backend/openapi.json`), o currículo (que é dado editorial, D11) e o Markdown de `docs/`, porque o Prettier alinha colunas de tabela com espaço e as tabelas de `07-decisoes.md` têm células que são parágrafos.

O Java usa `googleJavaFormat` na variante **AOSP** (4 espaços), que é o estilo que o código já tinha; a variante padrão reformataria o backend inteiro para 2 espaços. O `.editorconfig` na raiz reflete o mesmo padrão, para o editor não desfazer no salvamento o que o CI vai cobrar.

A formatação inicial de todo o repositório está em um commit só, registrado em [`.git-blame-ignore-revs`](.git-blame-ignore-revs). Para o `git blame` local ignorá-lo:

```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

## Estrutura

```
backend/   Spring Boot + Postgres — fonte da verdade de progresso e SRS
  ├── src/main/resources/curriculum/   currículo versionado como dado (D11)
  ├── Dockerfile                       imagem da API (contexto de build: raiz do repo)
  └── railway.json                     config do serviço backend na Railway
web/       React + Vite — MVP
  ├── Dockerfile                       imagem do nginx que serve o dist/ e proxia /api
  ├── nginx.conf.template              template processado por envsubst no start
  └── railway.json                     config do serviço web na Railway
shared/    domain (regras puras), api-client, types (GERADOS do OpenAPI)
docs/      planejamento e log de decisões
```

Detalhes em [`docs/03-estrutura-projeto.md`](docs/03-estrutura-projeto.md) e [`CLAUDE.md`](CLAUDE.md).

## Próximos passos

1. **Assistir aos 11 vídeos de M0 e M1 e confirmar o encaixe** — a única etapa que não se
   automatiza (D21). Critérios por nó em [`docs/08-curadoria-videos.md`](docs/08-curadoria-videos.md);
   para trocar um vídeo, `node scripts/catalogar-video.mjs <NÓ> <url>`, que verifica e credita o
   canal automaticamente. Os já catalogados são reconferidos semanalmente pelo workflow `videos`
   (`node scripts/verificar-videos.mjs`), que avisa quando um sai do ar
2. Catalogar os vídeos de M2–M8 e escrever o quiz conceitual de M4–M8 (M2 e M3 já têm quiz)
3. Usar por 30 dias e avaliar contra os [critérios de sucesso](docs/05-mvp-web-plano.md)
4. Só então considerar mobile (D4)

## Planejamento

Toda a documentação de planejamento está em [`docs/`](docs/):

- [`00-visao-geral.md`](docs/00-visao-geral.md) — posicionamento e escopo
- [`01-stack-tecnica.md`](docs/01-stack-tecnica.md) — stack e decisões técnicas
- [`02-publicacao-ios-desafios.md`](docs/02-publicacao-ios-desafios.md)
- [`03-estrutura-projeto.md`](docs/03-estrutura-projeto.md)
- [`04-arvore-curriculo-bjj.md`](docs/04-arvore-curriculo-bjj.md) — currículo (46 nós)
- [`05-mvp-web-plano.md`](docs/05-mvp-web-plano.md)
- [`06-disclaimer-responsabilidade.md`](docs/06-disclaimer-responsabilidade.md)
- [`07-decisoes.md`](docs/07-decisoes.md) — log de decisões
- [`08-curadoria-videos.md`](docs/08-curadoria-videos.md) — critérios de curadoria dos vídeos
- [`09-regras-repositorio.md`](docs/09-regras-repositorio.md) — `main` protegida, PR obrigatório, CI como portão
- [`10-prints-da-landing.md`](docs/10-prints-da-landing.md) — como refazer os prints que a landing exibe
- [`11-privacidade.md`](docs/11-privacidade.md) — o que o app guarda de dado pessoal, e como apagar
- [`12-fontes-de-conteudo.md`](docs/12-fontes-de-conteudo.md) — régua de fonte para conceito e quiz
- [`13-feedback-usuarios.md`](docs/13-feedback-usuarios.md) — fila de feedback de quem usa o app
- [`14-contas-de-teste-local.md`](docs/14-contas-de-teste-local.md) — como entrar no app em `localhost` sem provedor configurado

## Contribuindo

`main` é protegida: não aceita push direto e o merge só libera com o CI verde. Toda mudança entra
por pull request.

```bash
git switch -c minha-mudanca
# ... commits ...
git push -u origin minha-mudanca
gh pr create --fill
```

As regras são versionadas em [`.github/rulesets/main.json`](.github/rulesets/main.json) — ver
[`docs/09-regras-repositorio.md`](docs/09-regras-repositorio.md) para aplicá-las ou alterá-las.

Os contextos exigidos pela ruleset são os nomes dos jobs `backend` e `web`. Renomear um deles sem
atualizar o JSON derrubaria a proteção em silêncio, e por isso o CI verifica:

```bash
node scripts/verificar-ruleset.mjs
```

Atualizações de dependência chegam por PR do Dependabot toda segunda-feira, nos três ecossistemas
(npm, Maven e actions). Patch e minor vêm agrupados; major vem separado, porque exige ler o
changelog. Nada é mergeado automaticamente.

## Aviso de responsabilidade

> **AVISO IMPORTANTE — LEIA ANTES DE USAR**
>
> O FightOssStreak é uma ferramenta de **organização e revisão de estudos**, destinada a complementar o treino presencial de jiu-jitsu brasileiro em academia, sob supervisão de professor qualificado.
>
> **Este aplicativo não ensina jiu-jitsu e não substitui instrução presencial.** O conteúdo aqui é de caráter estritamente informativo e instrucional de apoio. Jiu-jitsu é uma atividade física de contato que envolve técnicas de imobilização, torção articular e estrangulamento, com risco real de lesão grave.
>
> **Nunca pratique as técnicas referenciadas neste aplicativo:** sem supervisão de um professor qualificado; fora de um ambiente adequado de treino; com parceiro que não tenha consentido e não conheça os riscos; sem aquecimento e condicionamento adequados.
>
> **Técnicas de estrangulamento podem causar perda de consciência, lesão neurológica ou morte.** Técnicas de torção articular podem causar lesão permanente. Sempre respeite o toque (tap) do parceiro imediatamente.
>
> Consulte um médico antes de iniciar qualquer atividade física, especialmente se você tem condição pré-existente ou histórico de lesão.
>
> O autor e os colaboradores deste aplicativo **não se responsabilizam** por qualquer lesão, dano ou prejuízo decorrente do uso das informações aqui contidas. Ao usar este aplicativo, você reconhece que assume integralmente os riscos da prática.
>
> Os vídeos referenciados são conteúdo de terceiros, incorporados a partir do YouTube. Não somos autores desse conteúdo e não temos vínculo com seus criadores.

## Licença

MIT — ver [`LICENSE`](LICENSE).
