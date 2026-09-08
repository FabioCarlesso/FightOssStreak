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
- Registro de drill ("treinei hoje") e streak, com **freeze**: até dois dias perdidos por mês de
  calendário são perdoados sem quebrar a sequência (#99, D55). Dia coberto mantém a corrente e não
  conta como dia de treino, e o saldo do mês aparece na home
- Agenda de revisão por SRS na home ("revise hoje: X, Y")
- **Diário de treino** (#114, D56): a sessão é a unidade do que aconteceu no tatame — data, tipo,
  duração, peso, sensação, o que aprendeu e o que precisa melhorar —, com vínculo **opcional** de
  técnicas do currículo. Técnica vinculada é o mesmo `drill_log` de sempre, então o SRS continua com
  uma verdade só. O streak passa a contar **dia com registro**, e não dia com drill (D58): sessão de
  qualquer tipo menos `DESCANSO` conta

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

### Números descritivos, sem meta (D56)

O diário trouxe dois números que **não** são critério de sucesso e nunca devem virar um:

| Número | Onde aparece | Por que não tem meta |
|---|---|---|
| Sessões no mês | `/hoje` e `/diario` (`sessionsInMonth`) | mede quanto se escreve, não quanto se retém — e escrever é o meio, não o fim |
| Sessões com técnica vinculada | leitura do diário | idem: o que interessa é a revisão que a técnica gera, e isso já é medido abaixo |

Meta em cima deles empurraria o app para o caderno, que é exatamente o critério de falha da D56.
**O critério de revisões atendidas (≥ 60%) não afrouxa** — é ele que segura a D58: se o streak médio
subir enquanto as revisões atendidas caem, o número virou presença e não retenção, e ou o streak
volta a contar só `drill_log`, ou a agenda passa a ser o indicador principal da home.

### Como as métricas são medidas

As quatro estão em `GET /api/metrics/mvp` e na tela `/progresso`, cada uma ao lado da própria meta. Duas exigiram dado novo, porque não eram reconstituíveis depois (ver D20 em `07-decisoes.md`):

- **Revisões atendidas** — o drill grava, no momento do registro, se o nó estava vencido e para quando estava agendado. Técnica vinculada a uma sessão do diário entra aqui igual, porque é o mesmo `drill_log` gravado pelo mesmo caminho de código (D56a) — se o diário produzisse um registro paralelo, esta fração passaria a mentir. As duas pontas da fração recortam pela mesma régua: só entra o que venceu dentro da janela, senão limpar parte de um backlog antigo apareceria como 100%. Janela sem nada agendado não vira 0%, vira "sem agenda", porque não houve sugestão a ignorar.
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
