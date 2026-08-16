# Plano de MVP Web

## Por que web primeiro
- Elimina os desafios de publicação iOS na fase de validação (ver `02`)
- Deploy em minutos vs. semanas de review de loja
- Custo praticamente zero
- Feedback loop instantâneo — e, no caso, o usuário é o próprio autor

## Reaproveitamento para mobile
React e React Native compartilham modelo mental (JSX, hooks, estado). Lógica de negócio (`shared/domain`), tipos e cliente de API são reaproveitados na migração — só a camada de UI é reescrita.

## Escopo do MVP (o que entra)
- Árvore de currículo navegável com nós bloqueados/desbloqueados
- Detalhe do nó: conceito + vídeo embutido + quiz
- Registro de drill ("treinei hoje") e streak
- Agenda de revisão por SRS na home ("revise hoje: X, Y")

## O que fica de fora do MVP
- Login/contas (uso pessoal — usuário único basta)
- Notificações push
- Perfis, ranking, social
- Vídeo próprio
- No-Gi

## Critérios de sucesso

Sem número, "validar" não valida nada. Como o usuário inicial é o próprio autor, os critérios são de **uso real, não de adoção**:

| Métrica | Meta em 30 dias | O que responde |
|---|---|---|
| Dias com registro de drill | ≥ 12 de 30 (~3x/semana, alinhado à rotina de treino) | O hábito de registrar sobrevive à rotina? |
| Nós revisados via sugestão do SRS | ≥ 60% das revisões agendadas | A sugestão de "o que drillar" é útil ou é ignorada? |
| Nós concluídos | ≥ 15 (módulos 0 e 1 completos) | O currículo acompanha o que aparece na aula? |
| Quiz refeito espontaneamente | qualquer ocorrência | Sinal forte de que o quiz tem valor de retenção real |

**Critério de falha honesto:** se depois de 30 dias o app estiver sendo aberto só para "não perder o streak", sem que o SRS mude o que se treina no tatame, a mecânica falhou — a gamificação estará se sustentando sozinha, sem gerar aprendizado. Nesse caso, repensar antes de investir em mobile.

## Próximos passos
1. Estrutura de telas e fluxo de navegação
2. Schema de banco (esqueleto já em `01-stack-tecnica.md`)
3. Catalogar vídeos do YouTube para os nós de M0 e M1 (suficiente para começar)
4. Setup do repositório, workspaces e geração de tipos via OpenAPI
