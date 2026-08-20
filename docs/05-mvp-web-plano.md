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
- ~~Login/contas (uso pessoal — usuário único basta)~~ — entrou depois pela #24, quando abrir
  o app para outras pessoas passou a valer mais que a simplicidade do usuário único (D36)
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

### Como as métricas são medidas

As quatro estão em `GET /api/metrics/mvp` e na tela `/progresso`, cada uma ao lado da própria meta. Duas exigiram dado novo, porque não eram reconstituíveis depois (ver D20 em `07-decisoes.md`):

- **Revisões atendidas** — o drill grava, no momento do registro, se o nó estava vencido e para quando estava agendado. As duas pontas da fração recortam pela mesma régua: só entra o que venceu dentro da janela, senão limpar parte de um backlog antigo apareceria como 100%. Janela sem nada agendado não vira 0%, vira "sem agenda", porque não houve sugestão a ignorar.
- **Quiz refeito** — toda submissão entra em `quiz_attempt`, e conta a tentativa **posterior à primeira aprovação** do nó. Errar e passar na segunda é o caminho normal de conclusão; contá-lo acenderia a meta no primeiro erro de quem só está avançando.

**Critério de falha honesto:** se depois de 30 dias o app estiver sendo aberto só para "não perder o streak", sem que o SRS mude o que se treina no tatame, a mecânica falhou — a gamificação estará se sustentando sozinha, sem gerar aprendizado. Nesse caso, repensar antes de investir em mobile.

## Etapas de construção — todas concluídas

1. ~~Estrutura de telas e fluxo de navegação~~
2. ~~Schema de banco~~ (esqueleto em `01-stack-tecnica.md`)
3. ~~Catalogar vídeos do YouTube para os nós de M0 e M1~~ — feito com a ressalva de D21: os 11
   vídeos ainda não foram assistidos por inteiro
4. ~~Setup do repositório, workspaces e geração de tipos via OpenAPI~~

O que vem depois não é mais construção, é uso: a lista viva está em **Próximos passos** no
[`README.md`](../README.md). Manter duas listas era garantir que uma ficasse mentindo.
