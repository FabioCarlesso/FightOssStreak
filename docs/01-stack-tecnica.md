# Stack Técnica

## Decisão final
- **MVP web**: React (Vite) + TypeScript
- **Mobile (fase posterior)**: React Native via Expo
- **Backend**: Spring Boot
- **Banco**: Postgres
- **Vídeo**: embed do player oficial do YouTube (sem hospedagem própria)
- **Código compartilhado**: tipos TS, cliente de API e regras de negócio puras (streak, SRS) em `shared/`

## Por que React/React Native em vez de nativo
- Um único código-base cobre iOS, Android e (via React puro) a versão web
- Reaproveita experiência com Angular/TypeScript — curva menor que Dart ou Swift/Kotlin
- Nativo dobraria o esforço de manutenção sem ganho relevante para app de conteúdo + gamificação
- Limitações de RN (animações muito customizadas, APIs nativas de ponta) são irrelevantes neste escopo

## Decisões técnicas que precisam ser tomadas no dia 1

### Modelagem de pré-requisitos: é um grafo, não uma hierarquia
Nós têm múltiplos pré-requisitos e múltiplos sucessores. Modelar como tabela de arestas resolve e é simples:

```
node          (id, module_id, code, title, belt_level, youtube_video_id, order)
node_prereq   (node_id, prereq_node_id)      -- aresta do grafo
user_progress (user_id, node_id, status, completed_at, quiz_score)
srs_review    (user_id, node_id, next_review_at, interval_days, ease_factor)
```
Desbloqueio = todos os `prereq_node_id` do nó estão concluídos. Detecção de ciclo deve ser feita na ingestão do currículo, não em runtime.

### Contrato de API: OpenAPI desde o começo
Sem geração automática de tipos, `shared/types` diverge do backend em duas semanas. Springdoc-openapi no backend + `openapi-typescript` gerando os tipos em `shared/` num script de build.

### Autenticação
Não estava definida e afeta o schema. Para MVP de uso pessoal, o mais simples resolve (usuário único, ou OAuth via Google). Mas registre desde já: **a Apple exige exclusão de conta** em apps com login — se houver conta, precisa haver rota de deleção antes da publicação iOS.

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
Com embed de YouTube, o único custo recorrente é hospedagem de backend + banco. Para MVP: camada gratuita de Railway/Render/Fly.io ou Postgres gerenciado barato. Terraform e AWS ficam para quando (e se) houver escala real.

## Conclusão
O maior risco não é técnico — é o tempo de curadoria: mapear cada nó a um vídeo bom do YouTube e escrever quizzes que testem compreensão de conceito, não decoreba.
