# Estrutura do Projeto (Monorepo)

Projeto solo e open source (licença MIT prevista) — monorepo reduz overhead nesse estágio.

```
fightossstreak/
├── CLAUDE.md                  # contrato de comportamento p/ Claude Code (curto, aponta pra docs/)
├── LICENSE                    # MIT
├── README.md                  # inclui disclaimer resumido (ver 06)
│
├── backend/                   # Spring Boot
│   ├── src/main/java/...
│   ├── src/main/resources/
│   │   └── curriculum/        # currículo versionado como dados (JSON/YAML), não hardcoded
│   ├── pom.xml
│   └── Dockerfile
│
├── web/                       # React + Vite (MVP web-first)
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── api/
│   │   └── state/
│   ├── package.json
│   └── vite.config.ts
│
├── mobile/                    # React Native / Expo (fase posterior)
│   └── src/{components,screens,api,state}/
│
├── shared/                    # compartilhado entre web e mobile
│   ├── types/                 # GERADO a partir do OpenAPI — não editar à mão
│   ├── api-client/
│   └── domain/                # regras puras: cálculo de streak, SRS, desbloqueio de nó
│
├── docs/                      # este planejamento
└── .github/workflows/         # backend.yml, web.yml (path-filtered)
```

## Pontos importantes

- **Currículo como dado, não como código.** A árvore vive em arquivo versionado (`backend/src/main/resources/curriculum/`) e é ingerida no banco por migration/seed. Assim dá para revisar mudanças de currículo em PR, e a validação futura com um faixa-preta vira um diff legível em vez de um dump de SQL.
- **`shared/types` é gerado.** Springdoc-openapi expõe o spec; `openapi-typescript` gera os tipos. Colocar num script `npm run gen:types` e rodar no CI para falhar se estiver desatualizado.
- **`shared/domain` é o que mais se paga na migração para RN** — cálculo de streak, agendamento de SRS e lógica de desbloqueio são idênticos em web e mobile e não dependem de UI.
- **CI path-filtered**: `backend.yml` e `web.yml` disparam só quando a respectiva pasta muda.
- `infra/` com Terraform é prematuro — deploy simples (Railway/Render/Fly.io) resolve nesta fase.
