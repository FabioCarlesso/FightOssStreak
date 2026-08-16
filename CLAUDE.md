# CLAUDE.md — FightOssStreak (FOS)

Contrato curto de comportamento. O planejamento completo vive em [`docs/`](docs/) — leia antes de decidir qualquer coisa estrutural.

## O que este projeto é

Ferramenta pessoal de **revisão e retenção** do que é aprendido no tatame. **Não ensina jiu-jitsu** (D1). Se uma mudança fizer o app parecer um curso, ela está errada.

## Regras que não se negociam

1. **Currículo é dado, não código** (D11). A árvore vive em `backend/src/main/resources/curriculum/*.json` e é ingerida na subida. Nunca hardcode nó, pré-requisito ou quiz em Java/TS.
2. **`shared/types/generated/` é gerado** a partir do OpenAPI. Não editar à mão — rode `npm run gen:types`.
3. **Vídeo só por embed do YouTube** (D7). Não baixar, não re-hospedar, não recortar. Creditar canal em cada nó.
4. **Sem login no MVP** (D9). Usuário único, resolvido pelo backend. Não introduzir contas sem revisitar a decisão.
5. **Disclaimer é requisito de produto**, não enfeite. Textos em `docs/06-disclaimer-responsabilidade.md`; mudança material no texto exige subir a versão do aceite.
6. **`main` só muda por PR com CI verde** (D18). Nunca commitar direto em `main` — trabalhe em branch e abra PR. As regras são versionadas em `.github/rulesets/main.json`; mudança nelas entra por PR como qualquer outra, e depois roda `./scripts/apply-repo-rules.sh`.
7. **Renomear job de CI quebra a proteção de `main`.** Os jobs `backend` e `web` são os required checks. Renomeou? Atualize `.github/rulesets/main.json` no mesmo PR (`docs/09-regras-repositorio.md`).

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
npm test                    # testes de shared/domain
npm run gen:types           # regenera shared/types a partir do OpenAPI
cd backend && ./mvnw test   # testes do backend
docker compose up -d db     # Postgres local (perfil prod-like)
```

## Ao alterar o currículo

Editar o JSON, rodar `cd backend && ./mvnw test` — há teste que valida integridade do grafo (referências, ciclos, códigos duplicados). Registrar mudança pedagógica relevante em `docs/07-decisoes.md`.

## Ao tomar decisão estrutural

Anotar em `docs/07-decisoes.md` com justificativa e critério de revisão. O log existe para que reversões futuras sejam conscientes.
