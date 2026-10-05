# Arquitetura

Projeto solo e open source (MIT) em monorepo: backend Spring Boot + Postgres como fonte da verdade
de progresso e SRS, web em React + Vite, e `shared/` com o que web e mobile reaproveitam. Este
arquivo descreve cada componente, suas fronteiras e o porquê das escolhas de stack.

## Stack
- **MVP web**: React (Vite) + TypeScript
- **Mobile (em andamento, D67)**: React Native via Expo
- **Backend**: Spring Boot
- **Banco**: Postgres
- **Vídeo**: embed do player oficial do YouTube (sem hospedagem própria)
- **Código compartilhado**: tipos TS, cliente de API e regras de negócio puras (streak, SRS) em
  `shared/`

## Estrutura do monorepo

```
fightossstreak/
├── CLAUDE.md                  # contrato de comportamento p/ Claude Code (curto, aponta pra docs/)
├── LICENSE                    # MIT
├── README.md                  # inclui o aviso de responsabilidade completo
│
├── backend/                   # Spring Boot
│   ├── src/main/java/...
│   ├── src/main/resources/
│   │   └── curriculum/        # currículo versionado como dados (JSON/YAML), não hardcoded
│   ├── pom.xml
│   ├── Dockerfile             # contexto de build: a raiz do repo, não backend/
│   └── railway.json
│
├── web/                       # React + Vite (MVP web-first)
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── api/
│   │   └── state/
│   ├── package.json
│   ├── vite.config.ts
│   ├── Dockerfile             # nginx servindo o dist/ e proxiando /api (D23)
│   ├── nginx.conf.template    # envsubst no start: porta e upstream vêm do ambiente
│   └── railway.json
│
├── mobile/                    # React Native / Expo (D67); hoje só a tela de fumaça (#140)
│   ├── src/{components,screens,api,state}/
│   ├── app.json · eas.json    # config do Expo e perfis de build do EAS
│   └── jest.config.js
│
├── shared/                    # compartilhado entre web e mobile
│   ├── types/                 # GERADO a partir do OpenAPI — não editar à mão
│   ├── api-client/
│   └── domain/                # regras puras: cálculo de streak, SRS, desbloqueio de nó
│
├── docs/                      # este planejamento
└── .github/workflows/         # backend.yml e web.yml (portões), mobile, saude, videos, codeql
```

### Pontos importantes

- **Currículo como dado, não como código.** A árvore vive em arquivo versionado
  (`backend/src/main/resources/curriculum/`) e é ingerida no banco por migration/seed. Assim dá para
  revisar mudanças de currículo em PR, e a validação futura com um faixa-preta vira um diff legível
  em vez de um dump de SQL.
- **`shared/types` é gerado.** Springdoc-openapi expõe o spec; `openapi-typescript` gera os tipos.
  Colocar num script `npm run gen:types` e rodar no CI para falhar se estiver desatualizado.
- **`shared/domain` é o que mais se paga na migração para RN** — cálculo de streak, agendamento de
  SRS e lógica de desbloqueio são idênticos em web e mobile e não dependem de UI.
- **CI sem filtro de caminho em PR**: `backend.yml` e `web.yml` rodam em todo pull request, porque
  são
  required checks; o filtro de caminho só vale no push para `main` (D19,
[`repositorio.md`](repositorio.md)).
- `infra/` com Terraform é prematuro. A plataforma é a **Railway** (D22), com dois serviços a partir
  deste repo mais um Postgres gerenciado; a configuração de cada um cabe em um `railway.json`
  versionado ao lado do respectivo Dockerfile.

## Web e landing

O browser fala só com o `web`: o nginx serve o `dist/` e encaminha `/api` ao backend, que não tem
domínio público (D23/D24). Em dev, o Vite faz o mesmo papel com um proxy de `/api`.

A raiz (`/`) é a landing pública que apresenta o projeto; o app começa em `/hoje`, atrás do login e
do aceite do aviso (D33). A landing **não depende da API para renderizar**: aparece inteira mesmo
com
o backend frio, que é o caso comum de cold start. A única coisa que ela pergunta ao servidor é se
este ambiente tem demonstração configurada (D39) — sem resposta, a página é exatamente a mesma, só
sem aquele botão.

O aceite do aviso continua sendo o primeiro a decidir em `/hoje`, `/arvore`, `/no/:code` e
`/progresso`. Quem já entrou no app uma vez passa direto da raiz para a agenda do dia;
`/?ver=apresentacao` traz a apresentação de volta. Os prints que a landing exibe são refeitos pelo
procedimento de [`desenvolvimento/prints-da-landing.md`](desenvolvimento/prints-da-landing.md).

## Por que React/React Native em vez de nativo
- Um único código-base cobre iOS, Android e (via React puro) a versão web
- Reaproveita experiência com Angular/TypeScript — curva menor que Dart ou Swift/Kotlin
- Nativo dobraria o esforço de manutenção sem ganho relevante para app de conteúdo + gamificação
- Limitações de RN (animações muito customizadas, APIs nativas de ponta) são irrelevantes neste
  escopo

## Decisões técnicas que precisam ser tomadas no dia 1

### Modelagem de pré-requisitos: é um grafo, não uma hierarquia
Nós têm múltiplos pré-requisitos e múltiplos sucessores. Modelar como tabela de arestas resolve e é
simples:

```
node          (id, module_id, code, title, belt_level, youtube_video_id, order)
node_prereq   (node_id, prereq_node_id)      -- aresta do grafo
user_progress (user_id, node_id, status, completed_at, quiz_score)
srs_review    (user_id, node_id, next_review_at, interval_days, ease_factor)
```
Desbloqueio = todos os `prereq_node_id` do nó estão concluídos. Detecção de ciclo deve ser feita na
ingestão do currículo, não em runtime.

### Contrato de API: OpenAPI desde o começo
Sem geração automática de tipos, `shared/types` diverge do backend em duas semanas.
Springdoc-openapi no backend + `openapi-typescript` gerando os tipos em `shared/` num script de
build.

### Autenticação
Não estava definida e afeta o schema. Para MVP de uso pessoal, o mais simples resolve (usuário
único, ou OAuth via Google). Mas registre desde já: **a Apple exige exclusão de conta** em apps com
login — se houver conta, precisa haver rota de deleção antes da publicação iOS.

**O que aconteceu depois.** A D36 trouxe login social com acesso sob aprovação, e a promessa que
vinha junto era que *o app nunca vê credencial* — a autenticação acontecia no provedor, e a D37
recusou senha própria explicitamente por isso. **Desde a D47 essa frase não é mais verdadeira**: com
o cadastro aberto (#81), o app guarda hash de senha (`password_credential`,
`DelegatingPasswordEncoder` com prefixo `{bcrypt}`) e opera recuperação por e-mail. Foi troca
consciente — a fila de aprovação tinha virado o gargalo de um app que quer usuários —, e os riscos
que a D37 evitava (hash vazado, recuperação como vetor de tomada de conta, força bruta) passaram a
existir e a ser mitigados em vez de evitados. Detalhe de implementação que vale como regra: o hash
**nunca** aparece em resposta de API, e a senha em claro só existe dentro do request que a recebeu.

## Complexidade por camada

| Camada | Complexidade | Motivo |
|---|---|---|
| App web (React) | Baixa-Média | Ciclo de iteração rápido, sem build nativo |
| App mobile (RN) | Média | Gamificação (animações, streaks, notificações) exige polimento |
| Backend/API (Spring Boot) | Baixa-Média | Stack dominada; desafio é modelar currículo/progresso |
| **Curadoria de conteúdo** | **Alta** | Gargalo real: mapear técnicas, escolher vídeos, escrever quizzes coerentes |
| Algoritmo de SRS | Baixa | Adaptar SM-2 (Anki) — implementações de referência existem |
| Vídeo | **Baixa** (era Média) | Embed do YouTube elimina hospedagem, streaming e custo de CDN |

## Custo de infraestrutura
Com embed de YouTube, o único custo recorrente é hospedagem de backend + banco. Para MVP: camada
gratuita de Railway/Render/Fly.io ou Postgres gerenciado barato. Terraform e AWS ficam para quando
(e se) houver escala real.

## Conclusão
O maior risco não é técnico — é o tempo de curadoria: mapear cada nó a um vídeo bom do YouTube e
escrever quizzes que testem compreensão de conceito, não decoreba.
