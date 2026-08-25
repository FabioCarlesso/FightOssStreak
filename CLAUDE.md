# CLAUDE.md — FightOssStreak (FOS)

Contrato curto de comportamento. O planejamento completo vive em [`docs/`](docs/) — leia antes de decidir qualquer coisa estrutural.

## O que este projeto é

Ferramenta pessoal de **revisão e retenção** do que é aprendido no tatame. **Não ensina jiu-jitsu** (D1). Se uma mudança fizer o app parecer um curso, ela está errada.

## Regras que não se negociam

1. **Currículo é dado, não código** (D11). A árvore vive em `backend/src/main/resources/curriculum/*.json` e é ingerida na subida. Nunca hardcode nó, pré-requisito ou quiz em Java/TS.
2. **`shared/types/generated/` é gerado** a partir do OpenAPI. Não editar à mão — rode `npm run gen:types`.
3. **Vídeo só por embed do YouTube** (D7). Não baixar, não re-hospedar, não recortar. Creditar canal em cada nó.
4. **Cadastro é aberto: qualquer um cria conta com e-mail e senha** (D47, que revisitou D36/D37). Login por Google entra direto. Quem não tem provedor **se cadastra**: a conta nasce `APROVADO` e **não verificada**, sem sessão, e só existe de verdade quando o link de confirmação (24h, uso único) for aberto. Senha é hash com `DelegatingPasswordEncoder`, mínimo de 12 caracteres, em `password_credential` — nunca em `user_identity`. Recuperação é link de 1 hora que queima os pendentes e derruba as sessões abertas. Cadastro, reenvio e recuperação respondem **igual** para e-mail que existe e que não existe. `login_token` tem `purpose`, e ele é **conferido no consumo** — link de 24h não pode valer pelo de 1h. **Vínculo entre provedores é por `app_user.primary_email`, sempre verificado**: identidade nova cujo e-mail verificado já pertence a uma conta se anexa àquela conta; e-mail não verificado nunca vincula nada. O usuário da requisição sai do `CurrentUserProvider` — **todo método de autenticação novo precisa ser reconhecido lá**, senão o login autentica e o app responde 401 em silêncio (foi o defeito da #51). Todo login que a aplicação faz por conta própria passa pelo `SessionLogin`: rotacionar o id, gravar o contexto e registrar a sessão. A aplicação **sobe sem segredo nenhum**, e é assim que dev e CI rodam — sem credencial de envio, o cadastro por senha responde indisponível, como o provedor sem `client-id` não aparece. `fos.auth.owner-emails` significa "vê a fila e decide", com e-mail verificado; a fila de aprovação e o resumo horário (D38) ainda existem e saem na fatia 3 da #81. Sem sessão é 401. `fos.demo.template-email` (D39) cria conta `APROVADO` descartável, sem identidade de ninguém, que vence em duas horas — mexer nisso reabre a decisão. Papéis em tabela e admin genérico seguem fora de escopo.
5. **Disclaimer é requisito de produto**, não enfeite. Textos em `docs/06-disclaimer-responsabilidade.md`; mudança material no texto exige subir a versão do aceite.
6. **`main` só muda por PR com CI verde** (D18). Nunca commitar direto em `main` — trabalhe em branch e abra PR. As regras são versionadas em `.github/rulesets/main.json`; mudança nelas entra por PR como qualquer outra, e depois roda `./scripts/apply-repo-rules.sh`.
7. **Renomear job de CI quebra a proteção de `main`.** Os jobs `backend` e `web` são os required checks. Renomeou? Atualize `.github/rulesets/main.json` no mesmo PR e rode `./scripts/apply-repo-rules.sh` (`docs/09-regras-repositorio.md`). O caso de renome não depende mais de memória: `scripts/verificar-ruleset.mjs` roda no CI e falha apontando o contexto órfão. Já **acrescentar `paths:` ao `pull_request:`** derruba a proteção do mesmo jeito e a guarda não pega — não introduza (D19).
8. **Lint e formatação são portão, não sugestão.** `npm run lint` (ESLint + Prettier) e `./mvnw spotless:check` rodam antes dos testes nos dois jobs. `npm run lint:fix` e `./mvnw spotless:apply` corrigem. Arquivo gerado fica fora do lint.

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
