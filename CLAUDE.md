# CLAUDE.md — FightOssStreak (FOS)

Contrato curto de comportamento. O planejamento completo vive em [`docs/`](docs/) — leia antes de decidir qualquer coisa estrutural.

## O que este projeto é

Ferramenta pessoal de **revisão e retenção** do que é aprendido no tatame. **Não ensina jiu-jitsu** (D1). Se uma mudança fizer o app parecer um curso, ela está errada.

## Regras que não se negociam

1. **Currículo é dado, não código** (D11). A árvore vive em `backend/src/main/resources/curriculum/*.json` e é ingerida na subida. Nunca hardcode nó, pré-requisito ou quiz em Java/TS.
2. **`shared/types/generated/` é gerado** a partir do OpenAPI. Não editar à mão — rode `npm run gen:types`.
3. **Vídeo só por embed do YouTube** (D7). Não baixar, não re-hospedar, não recortar. Creditar canal em cada nó.
4. **Provedor entra direto; a fila é de quem não tem provedor** (D37, que revisitou a D36). Login por Google/Facebook cria conta já `APROVADO`. Quem não tem provedor pede acesso por e-mail, nasce `PENDENTE`, o autor é avisado por e-mail (D38) e libera — aí entra por **magic link** de 15 minutos e uso único, guardado só como hash. Recusa não é reaberta. `fos.auth.owner-emails` significa "vê a fila e decide", com e-mail verificado. Sem sessão é 401; sem liberação é 403 com o motivo. O usuário da requisição sai do `CurrentUserProvider` — **todo método de autenticação novo precisa ser reconhecido lá**, senão o login autentica e o app responde 401 em silêncio (foi o defeito da #51). Provedor ou envio de e-mail sem credencial não é registrado: a aplicação sobe sem segredo nenhum, e é assim que dev e CI rodam. Papéis, admin genérico, senha própria e cadastro aberto seguem fora de escopo.
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

Vídeos: `node scripts/catalogar-video.mjs <NÓ> <url>` cataloga o canônico (verifica e credita o
canal), `... <NÓ> --extra <url>...` acrescenta complementares (D32, teto de 4 por nó) e
`node scripts/verificar-videos.mjs` reconfere os já catalogados — o workflow `videos` roda esse
segundo semanalmente e avisa por issue. Ver `docs/08-curadoria-videos.md`.

## Ao alterar o currículo

Editar o JSON, rodar `cd backend && ./mvnw test` — há teste que valida integridade do grafo (referências, ciclos, códigos duplicados). Registrar mudança pedagógica relevante em `docs/07-decisoes.md`.

## Ao tomar decisão estrutural

Anotar em `docs/07-decisoes.md` com justificativa e critério de revisão. O log existe para que reversões futuras sejam conscientes.
