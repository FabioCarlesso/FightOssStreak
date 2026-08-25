# Contas de teste no ambiente local

Como entrar no app rodando na sua máquina sem ter provedor OAuth nem envio de e-mail
configurados — para testar tela autenticada, fila do dono e qualquer coisa que dependa de
`CurrentUserProvider`.

## Por que isto existe

Desde a D36/D37 o app **exige login**, e todos os caminhos reais dependem de credencial que
deliberadamente não é versionada: provedor sem `client-id` não é registrado, e envio de e-mail sem
`FOS_EMAIL_API_KEY` não existe. Isso é proposital — dev e CI sobem sem segredo nenhum. O efeito
colateral é que a tela de login em `localhost` mostra *"nenhuma forma de entrada está configurada
neste ambiente"*, e não há como chegar a nada autenticado.

**A D47 não muda isso, e é bom entender por quê.** O cadastro com senha (#81) parece a saída óbvia
para o problema desta página — criar uma conta local e pronto —, mas ele *é* o e-mail de
confirmação: sem `FOS_EMAIL_API_KEY` o link nunca sai, a conta fica não verificada para sempre e o
login por senha responde 403. Por isso `POST /api/auth/cadastro` responde **503** em dev, e os dois
scripts abaixo continuam sendo o caminho local. Quem quiser exercitar o fluxo de senha de verdade
precisa de credencial de envio de verdade — ou do roteiro manual no Compose, no fim desta página.

Inserir uma linha em `app_user` não resolve: quem decide se você está logado é a sessão do Spring
Security, não a presença no banco. O `CurrentUserProvider` só resolve usuário a partir de uma
autenticação de tipo conhecido (OAuth2, e-mail, demonstração ou senha).

O caminho que funciona sem credencial é o da **entrada por e-mail** (a do #52, que a fatia 3 da #81
vai desmontar), fabricada à mão: o
`login_token` guarda só o SHA-256 do valor que viaja no link, então dá para escolher o valor,
gravar o hash e abrir `/api/login/email/<valor>`. É exatamente o fluxo de produção, só que sem o
e-mail no meio.

## Por que Postgres local, e não o H2 do perfil dev

O perfil `dev` usa H2 **em memória**: o banco morre junto com o processo, e as contas semeadas
sumiriam a cada reinício do backend. O Postgres do `docker-compose` tem volume (`fos-pgdata`), então
semear uma vez basta — as contas sobrevivem a reinícios e a rebuilds.

## Por que isto não alcança produção

Três camadas, e nenhuma depende de lembrar de nada:

1. **Não são código do Spring.** Não são `@Component`, `@Configuration` nem `CommandLineRunner`:
   nada no boot da aplicação os executa. Rodam só quando alguém digita `node scripts/...`.
2. **Não são migration do Flyway.** Ficam em `scripts/`, e não em
   `backend/src/main/resources/db/migration/` — o que estivesse lá rodaria automático em todo
   startup, em todo ambiente, inclusive no próximo deploy. Esta é a distinção que importa: um seed
   marcado só com `@Profile("dev")` **não** seria garantia, porque o README já documenta que
   `SPRING_PROFILES_ACTIVE` esquecida faz produção cair no perfil `dev` em silêncio.
3. **Falam com um container que só existe na sua máquina.** O alvo é `docker exec fos-db`, fixo no
   script. Produção usa o Postgres gerenciado da Railway, com credenciais que vivem só no painel de
   lá e nunca tocam este repositório — não há rede, volume nem credencial em comum.

## Como usar

Uma vez, para preparar o banco:

```bash
docker compose up -d db

# uma passada do backend só para o Flyway criar o schema
cd backend && FOS_DB_URL=jdbc:postgresql://localhost:5432/fos FOS_DB_USER=fos \
  FOS_DB_PASSWORD=fos ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
# espere "Started FightOssStreakApplication", derrube com Ctrl+C

node scripts/seed-dev-users.mjs
```

Isso cria duas contas, ambas já `APROVADO` (não passam pela fila):

| E-mail | Papel | Para quê |
|---|---|---|
| `aluno@teste.local` | conta comum | o app do ponto de vista de quem usa: árvore, drill, envio de feedback |
| `dono@teste.local` | conta dona | o que só o autor vê: *Solicitações* e a fila de feedback |

`seed-dev-users.mjs` é idempotente — rodar de novo não duplica nada, só informa o que já existia.

A cada sessão de teste, para entrar:

```bash
# terminal 1
cd backend && FOS_DB_URL=jdbc:postgresql://localhost:5432/fos FOS_DB_USER=fos \
  FOS_DB_PASSWORD=fos FOS_OWNER_EMAILS=dono@teste.local \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres

# terminal 2
npm run dev:web

# terminal 3 — imprime a URL de entrada; abra no navegador
node scripts/mint-dev-login.mjs aluno@teste.local
node scripts/mint-dev-login.mjs dono@teste.local
```

O link emitido vale **24h** e pode ser usado uma vez; rodar o script de novo emite outro. (A
entrada por e-mail de verdade usa 15 minutos — aqui é mais folgado só por conveniência de reteste,
e é o link que expira, nunca a conta.)

## Detalhes que economizam tempo

- **`FOS_OWNER_EMAILS=dono@teste.local` é obrigatório para testar a fila do dono.** Sem essa
  variável, `dono@teste.local` entra como conta comum: `AccountService.isOwner` exige e-mail
  verificado **e** presente na lista. O seed já grava `email_verified = true`; a lista é sua parte.
- **Cada conta aceita o disclaimer separadamente.** É por conta, não por navegador — trocar de
  usuário mostra o aviso de novo, e isso é o comportamento correto.
- **O link redireciona para `localhost:8080/hoje` e cai numa página de erro do Spring.** Não é
  defeito: o cookie de sessão já foi gravado para `localhost:5173` pelo proxy do Vite. Abra
  `http://localhost:5173/hoje` na sequência e você está dentro. (Em produção web e API são a mesma
  origem, então o redirect acerta.)
- **Para começar do zero**: `docker compose down -v` apaga o volume junto, e aí o roteiro recomeça
  na criação do schema.

## Exercitando o cadastro por senha (#81)

O roteiro acima entra pelo link de e-mail. Para percorrer o fluxo de senha — cadastrar, confirmar,
entrar, redefinir — não há atalho local: ele exige provedor de envio, porque o cadastro só termina
quando o link de confirmação chega a uma caixa de verdade.

```bash
docker compose up --build   # a stack inteira em :8081
```

Com `FOS_EMAIL_API_KEY` e `FOS_EMAIL_FROM` preenchidos no ambiente do Compose, o que dá para
conferir de ponta a ponta é:

1. cadastrar com um endereço seu → `202`, nenhuma sessão, um e-mail na caixa;
2. abrir o link (vale **24h**, uma vez só) → cai em `/hoje` já dentro;
3. sair, entrar de novo com a senha;
4. *esqueci minha senha* → link de **1h**; ao usá-lo, a sessão que estava aberta cai.

Sem essas duas variáveis o passo 1 responde `503 cadastro_indisponivel`, que é o comportamento
correto e não um defeito do ambiente.
