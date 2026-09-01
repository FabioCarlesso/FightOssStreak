# CLAUDE.md — FightOssStreak (FOS)

Contrato curto de comportamento. O planejamento completo vive em [`docs/`](docs/) — leia antes de decidir qualquer coisa estrutural.

## O que este projeto é

Ferramenta pessoal de **revisão e retenção** do que é aprendido no tatame. **Não ensina jiu-jitsu** (D1). Se uma mudança fizer o app parecer um curso, ela está errada.

## Regras que não se negociam

1. **Currículo é dado, não código** (D11). A árvore vive em `backend/src/main/resources/curriculum/*.json` e é ingerida na subida. Nunca hardcode nó, pré-requisito ou quiz em Java/TS.
2. **`shared/types/generated/` é gerado** a partir do OpenAPI. Não editar à mão — rode `npm run gen:types`.
3. **Vídeo só por embed do YouTube** (D7). Não baixar, não re-hospedar, não recortar. Creditar canal em cada nó.
4. **Cadastro é aberto: qualquer um cria conta com e-mail e senha** (D47/D48, que revisitaram D36–D38). Não há fila, aprovação nem magic link de login — tudo isso foi desmontado. Login por Google entra direto. Quem não tem provedor **se cadastra**: a conta nasce `APROVADO` e **não verificada**, sem sessão, e só existe de verdade quando o link de confirmação (24h, uso único) for **confirmado com um clique** — abrir a URL só consulta o link, porque varredor de e-mail abre tudo que chega. Senha é hash com `DelegatingPasswordEncoder`, mínimo de 12 caracteres, em `password_credential` — nunca em `user_identity`. Recuperação é link de 1 hora que queima os pendentes e derruba as sessões abertas. Cadastro, reenvio e recuperação respondem **igual** para e-mail que existe e que não existe. `login_token` tem `purpose`, e ele é **conferido no consumo** — link de 24h não pode valer pelo de 1h. **Vínculo entre provedores é por `app_user.primary_email`, sempre verificado**: identidade nova cujo e-mail verificado já pertence a uma conta se anexa àquela conta; e-mail não verificado nunca vincula nada. O usuário da requisição sai do `CurrentUserProvider` — **todo método de autenticação novo precisa ser reconhecido lá**, senão o login autentica e o app responde 401 em silêncio (foi o defeito da #51). Todo login que a aplicação faz por conta própria passa pelo `SessionLogin`: rotacionar o id, gravar o contexto e registrar a sessão. A aplicação **sobe sem segredo nenhum**, e é assim que dev e CI rodam — sem credencial de envio, o cadastro por senha responde 503, como o provedor sem `client-id` não aparece. **Quem administra é `app_user.role`** (D49), mudado pela tela *Usuários* sem deploy; quem decide isso continua sendo `AccountService.roleOf`, **ponto único**, e `/api/me` devolve `role`. Só conta com e-mail verificado — pelo provedor ou pela confirmação do próprio app — vira `ADMIN`. `fos.auth.owner-emails` não é fonte da verdade: é **semente de bootstrap e saída de emergência**, que promove na subida e em todo login verificado e **nunca rebaixa**. Conta abusiva se **bloqueia** movendo para `RECUSADO` (`POST /api/admin/usuarios/{id}/status`): o `AccessGateInterceptor` relê o estado a cada requisição e responde 403 `acesso_recusado` já na ação seguinte, inclusive em aba aberta — **bloqueio não derruba sessão**, e derrubar faria o `ConcurrentSessionFilter` responder 401 antes do portão, devolvendo a pessoa para o login em vez do motivo — não é a fila de aprovação de volta, e **conta bloqueada continua podendo se excluir** por `DELETE /api/me`. Sem sessão é 401. `fos.demo.template-email` (D39) cria conta `APROVADO` descartável, sem identidade de ninguém, que vence em duas horas — mexer nisso reabre a decisão. **Permissão granular** (perfil por recurso) segue fora de escopo: são dois papéis, e o critério para mudar isso está na D49.
5. **Disclaimer é requisito de produto**, não enfeite. Textos em `docs/06-disclaimer-responsabilidade.md`; mudança material no texto exige subir a versão do aceite.
6. **`main` só muda por PR com CI verde** (D18). Nunca commitar direto em `main` — trabalhe em branch e abra PR. As regras são versionadas em `.github/rulesets/main.json`; mudança nelas entra por PR como qualquer outra, e depois roda `./scripts/apply-repo-rules.sh`.
7. **Renomear job de CI quebra a proteção de `main`.** Os jobs `backend` e `web` são os required checks. Renomeou? Atualize `.github/rulesets/main.json` no mesmo PR e rode `./scripts/apply-repo-rules.sh` (`docs/09-regras-repositorio.md`). O caso de renome não depende mais de memória: `scripts/verificar-ruleset.mjs` roda no CI e falha apontando o contexto órfão. Já **acrescentar `paths:` ao `pull_request:`** derruba a proteção do mesmo jeito e a guarda não pega — não introduza (D19).
8. **Coleta de uso não guarda IP, não cria cookie e não chama terceiro** (D50). O IP é lido na
   requisição, deriva país e compõe a `visit_key`, e é descartado no mesmo método — **não existe
   coluna de IP em tabela nenhuma**, e `UsageSemIpTest` reprova o build se uma aparecer em qualquer
   migration. A `visit_key` é `hash(sal do dia + IP + User-Agent)` com o sal **sorteado por dia e
   nunca persistido**: é o que conta pessoas sem cookie e sem identificador estável, e é o que
   dispensa banner de consentimento. Geolocalização é **arquivo local**, ausente em dev e CI — país
   desconhecido é categoria, não erro. O cliente só manda o que só ele sabe (rota, host do referrer,
   os três `utm_*`); dispositivo, navegador, sistema, idioma e país são **derivados** da requisição,
   e os quatro eventos de funil são emitidos pelo **backend** — do cliente seriam forjáveis. O
   caminho é normalizado contra a lista de rotas em `UsagePaths` antes de virar linha: rota nova do
   app precisa entrar lá, e segmento variável nunca é gravado (senão token de confirmação acabaria
   em tabela de métrica). Cru vive 90 dias, agregado fica, `DELETE /api/me` leva o cru da conta.
   O freio por visita **não segura quem varia o `User-Agent`** — essa metade da chave é do cliente
   por definição, e a #77 só consertou a outra. Quem segura o tamanho da tabela é o **teto diário**
   (`fos.usage.daily-cap`), que não depende de chave nenhuma; não troque um pelo outro. A coleta é
   o único consumidor do `AccessRateLimiter` que roda em toda navegação: ela varre o mapa **por
   prefixo**, porque varrer tudo com a janela curta dela apagaria o contador de força bruta de
   senha.
   Nada disso pode quebrar tela: evento que falha é evento perdido. Mexer em qualquer uma dessas
   linhas exige reescrever `docs/11-privacidade.md` — a promessa está lá por escrito.
   **Quem lê tudo isso é o painel** (`/admin/painel`, D52), e ele é **agregado e de ninguém**: lê
   `usage_daily` e **nunca** `usage_event`. Nada na resposta dele identifica pessoa — não há
   e-mail, nome nem `user_id`, e há teste que varre o corpo inteiro atrás de uma arroba. Visão por
   pessoa, sessão individual ou funil por conta não é ajuste de tela: é a D50 revertida, e passa
   por reescrever `docs/11-privacidade.md` antes de escrever a consulta. Dimensão nova do painel é
   **linha no `UsageAggregator`**, nunca migration — foi assim que navegador e idioma entraram.
9. **O endereço de quem chama sai do `ClientIp`, nunca do `getRemoteAddr()`** (D51, #77). Atrás do
   nginx o segundo devolve o **primeiro** elemento do `X-Forwarded-For` — que é o que o cliente
   escreveu —, e com ele todo freio por IP vira decoração: basta um endereço novo por requisição.
   O que vale é o `X-Fos-Forwarded-For`, escrito pelo nginx em toda requisição proxiada, lido **do
   fim para o começo** pulando `fos.proxy.trusted-hops` saltos. Endpoint público novo que precise
   de freio por origem chama o `ClientIp` — não copie a expressão. Mudou a topologia (CDN na
   frente, proxy a mais)? O conserto é a variável `FOS_PROXY_TRUSTED_HOPS`, e ela está na tabela
   do README. **Código e variável só funcionam juntos e entram por caminhos diferentes** — o
   código por PR, a variável por clique no painel —, e no deploy da #96 ela não veio: o app rodou
   com o default `1` em silêncio, porque `1` é valor válido. Por isso o `ProxyTopology` confere a
   cadeia observada contra a declarada e emite `WARN` quando não batem (D53), uma vez por hora e
   sem endereço nenhum no texto. Ele **não** adivinha o número nem impede a subida: aviso, não
   portão. E **nenhum dos dois lados manda copiar o número observado** — cadeia longa é o que se vê
   quando quem chama forja `X-Forwarded-For` sem borda que saneie, e cadeia curta, num backend
   declarado acima da topologia real, também pode trazer elemento forjado dentro dela.
10. **Quem avisa que o site caiu não pode ser o site** (D54, #86). O monitoramento tem duas
    metades e elas não se substituem. **De fora**: o workflow `saude` bate na URL pública a cada
    dez minutos de um runner do GitHub, e **duas execuções seguidas** sem `200` abrem uma issue —
    a volta comenta e fecha **a mesma**, nunca abre outra. Ele lê a variável de repositório
    `URL_PUBLICA`; sem ela não faz nada e não fica vermelho. Não é required check, e não deve
    virar: o site fora do ar não pode travar o merge da correção. **De dentro**: o `HttpStatFilter`
    conta requisição, status e latência, agrega em memória e grava por hora em `http_stat_hourly` —
    sem Prometheus, sem Grafana, sem container novo (D22). Filtro e não interceptor **porque o 401
    da cadeia de segurança nunca chega ao MVC**, e é justamente o pico de 401 que o alerta procura.
    A rota gravada é o **padrão** que o roteamento casou, nunca o caminho que chegou: é a guarda do
    `UsagePaths` por outra porta, e sem ela token de confirmação acabaria em tabela de métrica.
    Latência vira **histograma de escada fixa** (`HttpStats`) porque percentil não é somável —
    trocar a escada é migration, ao contrário das dimensões da coleta. O alerta por e-mail tem
    **trava por incidente, não por janela** (o defeito que a D38 já pagou), sai para
    `fos.auth.owner-emails` e só existe com credencial de envio; **a gravação não depende dela** —
    herdar a condição da D38 deixaria dev, CI e qualquer instalação sem provedor sem histórico
    nenhum. Nada disso guarda IP, conta ou chave de visita: contar requisição não é observar
    pessoa, e a D50 vale igual. O 500 carrega **identificador de correlação** no corpo e no log, e a
    mensagem da exceção fica só no log. O `@ExceptionHandler(Exception.class)` que o produz tem
    precedência sobre o resolvedor do Spring — o desvio de 4xx no começo dele é o que impede JSON
    malformado de virar 500 e, pior, de entrar na taxa que dispara o alerta. Mexer nisso exige
    reescrever a seção de saúde de `docs/11-privacidade.md`.
11. **Lint e formatação são portão, não sugestão.** `npm run lint` (ESLint + Prettier) e `./mvnw spotless:check` rodam antes dos testes nos dois jobs. `npm run lint:fix` e `./mvnw spotless:apply` corrigem. Arquivo gerado fica fora do lint.

## Estrutura

```
backend/   Spring Boot + Postgres (fonte da verdade de progresso e SRS)
web/       React + Vite (MVP)
shared/    domain (regras puras), api-client, types (gerados)
docs/      planejamento e log de decisões
```

## Comandos

```bash
npm install                 # workspaces: web + shared/*
npm run dev:backend         # Spring Boot em :8080 (perfil dev, H2 em memória)
npm run dev:web             # Vite em :5173, proxy /api -> :8080
npm test                    # shared/domain + scripts + fluxos de UI do web (vitest/jsdom)
npm run lint                # ESLint + Prettier; lint:fix corrige
npm run gen:types           # regenera shared/types a partir do OpenAPI
cd backend && ./mvnw test   # testes do backend
cd backend && ./mvnw spotless:check   # formatação do Java; spotless:apply corrige
docker compose up -d db     # só o Postgres local (perfil prod-like)
docker compose up --build   # stack inteira em container: db + backend + web (nginx) em :8081
```

As duas imagens constroem a partir da **raiz** do repo (`-f backend/Dockerfile .`, `-f web/Dockerfile .`)
e não carregam host, porta ou credencial fixos — o que varia entre Compose e Railway (D22) entra por
variável de ambiente. Tabela completa no README.

Prints da landing: `node scripts/capturar-prints.mjs --semear` refaz os oito prints que a página
pública exibe, com o app rodando (`docs/10-prints-da-landing.md`). Mexeu na aparência da árvore, do
nó, do drill ou da tela inicial? O print correspondente precisa ser refeito no mesmo PR.

Testar tela autenticada em `localhost`: o app exige login e dev não tem provedor nem envio de
e-mail configurados. `node scripts/seed-dev-users.mjs` cria `aluno@teste.local` e `dono@teste.local`
no Postgres do Compose (uma vez, com o schema migrado) e `node scripts/mint-dev-login.mjs <e-mail>`
imprime a URL de entrada. Nenhum dos dois é código do Spring nem migration do Flyway — não rodam
sozinhos em ambiente nenhum. Ver `docs/14-contas-de-teste-local.md`.

Vídeos: `node scripts/catalogar-video.mjs <NÓ> <url>` cataloga o canônico (verifica e credita o
canal), `... <NÓ> --extra <url>...` acrescenta complementares (D32, teto de 4 por nó) e
`node scripts/verificar-videos.mjs` reconfere os já catalogados — o workflow `videos` roda esse
segundo semanalmente e avisa por issue. Ver `docs/08-curadoria-videos.md`.

## Ao alterar o currículo

Editar o JSON, rodar `cd backend && ./mvnw test` — há teste que valida integridade do grafo (referências, ciclos, códigos duplicados). Registrar mudança pedagógica relevante em `docs/07-decisoes.md`.

Escrever ou revisar conceito e quiz de um nó? `docs/12-fontes-de-conteudo.md` é a régua de fonte —
o que conta, o que não conta, e como texto de terceiro pode ser usado — e traz a tabela `nó →
fontes consultadas` para registrar de onde veio o que foi escrito.

## Ao tomar decisão estrutural

Anotar em `docs/07-decisoes.md` com justificativa e critério de revisão. O log existe para que reversões futuras sejam conscientes.
