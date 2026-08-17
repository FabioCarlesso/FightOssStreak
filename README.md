# FightOssStreak (FOS)

Ferramenta pessoal de revisão e retenção do que é aprendido no tatame, com mecânicas de gamificação (currículo em árvore, quiz, streak, repetição espaçada).

> ⚠️ Este projeto não ensina jiu-jitsu e não substitui instrução presencial com professor qualificado. Ver aviso completo em [`docs/06-disclaimer-responsabilidade.md`](docs/06-disclaimer-responsabilidade.md).

## O que já funciona

MVP web ponta a ponta: árvore de currículo com desbloqueio progressivo, detalhe do nó com conceito e quiz conceitual corrigido no servidor, registro de drill, streak e agenda de revisão por repetição espaçada. A tela `/progresso` mede os critérios de sucesso do MVP sobre o uso real.

| Camada | Estado |
|---|---|
| Currículo (46 nós, 9 módulos) | Transcrito como dado versionado em `backend/src/main/resources/curriculum/` |
| Quiz conceitual | Escrito para **M0 a M3** (25 nós, 91 perguntas). Os outros 21 nós, de M4 a M8, estão pendentes de curadoria |
| Vídeos do YouTube | **M0 e M1 catalogados (11/46)** pelo script, pendentes de conferência assistindo (D21 em [`07-decisoes.md`](docs/07-decisoes.md)). M2–M8 seguem sem vídeo — ver [instruções](backend/src/main/resources/curriculum/README.md) |
| Backend | Spring Boot + Flyway, API documentada em OpenAPI |
| Web | React + Vite |
| Mobile | Não iniciado (fase posterior, D4) |

### Modo demonstração

Na árvore (`/arvore`), o botão **Demonstração**, no card *Progresso*, abre os 46 nós de todos os
módulos para inspeção ignorando os pré-requisitos — serve para revisar conceito, vídeo e redação
das perguntas sem passar no quiz de cada nó anterior. Com o modo ligado, uma faixa no topo diz
isso em todas as telas e oferece o desligar.

**A demonstração não grava**: em nó que estaria bloqueado o quiz aparece só para leitura e o
registro de drill fica fora, então progresso, streak e agenda de revisão ficam intactos, e os
contadores da árvore continuam mostrando o que está travado de verdade (D31). O estado vive na
sessão do navegador — sobrevive a um F5, não a uma aba nova.

## Rodando

Dois modos. **Docker** para usar o app; **dev** para mexer no código.

### Com Docker (só precisa de Docker)

```bash
docker compose up --build
```

Abra <http://localhost:8081>. Na primeira abertura o app pede aceite do aviso de responsabilidade.

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

Detalhes que não são óbvios:

- **`PORT` explícita, mesmo a plataforma sabendo injetá-la.** No backend porque o
  `BACKEND_ORIGIN` do nginx aponta para uma porta fixa: serviço sem domínio público não tem
  garantia de receber a variável, e se receber outra o proxy bate em porta errada. No web
  para que o *target port* do domínio tenha um valor conhecido para casar.
- **`SPRING_PROFILES_ACTIVE` esquecida não quebra nada — e é justamente o problema.** O
  `application.yml` tem `profiles.default: dev`, que é H2 em memória. O deploy fica verde, o
  healthcheck passa, a API responde, e todo progresso some no deploy seguinte. Sinal de que
  o perfil pegou: as migrations do Flyway nos logs da subida.
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
- **As credenciais `fos/fos/fos` do Compose são de conveniência local.** Não reaproveitar.

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
