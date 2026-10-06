# D70 — O lembrete de revisão é notificação local, planejada no aparelho a partir da árvore

## Justificativa

A #142 traz o que o app tem de diferente do web: avisar no momento certo. O SRS já sabe quando cada
nó vence; o que faltava era chegar à pessoa sem que ela abrisse a página.

### Local, e não push pelo servidor

O `expo-notifications` agenda a notificação no próprio aparelho. Push pelo servidor (FCM/APNs)
exigiria token de dispositivo guardado no banco, credencial de envio em cada loja e um job no backend
para decidir quem avisar e quando — um dado pessoal novo e um terceiro novo para entregar uma
contagem que o app consegue calcular sozinho. A D68 já tinha deixado o push de fora.

**O custo:** o plano só é refeito quando o app roda. Quem não abre o app por mais de uma semana para
de receber lembretes depois do sétimo dia agendado. Isso é aceito: o lembrete existe para quem usa o
app, não para trazer de volta quem largou — e trazer de volta a qualquer custo é o caminho para o
critério de falha do `docs/produto/mvp-web.md`.

### O plano sai da árvore, não da agenda de hoje

`GET /api/reviews/today` só lista o que já venceu. Para agendar os próximos dias, o app precisa da
data de cada nó, e `GET /api/curriculum/tree` já traz o `nextReviewOn` de cada um — a mesma coluna
(`srs_review.next_review_on`) de que a agenda sai. Por isso não houve rota nova no backend.

O plano é uma notificação por dia, no horário escolhido, para cada um dos próximos **sete dias** em
que houver revisão vencida, contando como se nada fosse revisado até lá. É refeito ao abrir o app,
ao voltar para ele e após cada registro. Sete dias ficam longe do teto de notificações pendentes do
iOS e cobrem quem some por alguns dias.

### O que o lembrete diz, e quando pede permissão

- **Só a contagem.** Nome de técnica aparece na tela bloqueada, e peso e sensação são dado de saúde
  (D57). O texto nunca menciona streak: a D67 já decidiu que a notificação é sobre revisão vencida.
- **A permissão é pedida depois do primeiro registro**, nunca na abertura. O sistema deixa pedir uma
  vez só, e um pedido sem motivo aparente é o que a pessoa nega. Quem negou reativa nos ajustes do
  aparelho, e a tela *Conta* diz isso.
- **Ligado e horário ficam no aparelho**, no `expo-secure-store`. Não são dado da conta, e o servidor
  não precisa saber a que horas a pessoa quer ser lembrada.
- **Sair, ser bloqueado ou excluir a conta cancela o que estava agendado.** O lembrete mora dentro
  dos portões, e ao desmontar ele cancela tudo.

### O Expo Go do Android não roda o lembrete

Desde a SDK 53, o Expo Go do Android não traz o módulo de push, e o `import` do `expo-notifications`
lança erro ali, mesmo para quem só usa notificação local. O app carrega o módulo com `require`
tardio e, no Expo Go do Android, usa um notificador inerte: o lembrete fica indisponível e a tela
*Conta* explica. No Expo Go do iOS e em qualquer build instalada, o lembrete funciona. A alternativa
— trocar o fluxo de dev para a dev build — fica para quando a #143 trouxer o identificador do app.

### No Android, até uma hora de atraso

Sem a permissão de alarme exato, o Android entrega o lembrete numa janela de uma hora a partir do
horário escolhido — medido no emulador, o aviso das 19:00 chegou às 20:00. Pedir `USE_EXACT_ALARM`
resolveria, mas a Play Store reserva essa permissão a despertador e agenda, e um lembrete diário de
revisão não precisa de pontualidade de minuto.

## Revisar quando

- Se a medição mostrar que lembrete local não move as revisões atendidas. Aí a pergunta é a
  mecânica, não o canal, e push pelo servidor não resolve.
- Se o app precisar avisar algo que só o servidor sabe (resposta de feedback, conta bloqueada). Aí o
  push volta à mesa, com a coleta do token documentada em `docs/privacidade/`.

---

[Índice das decisões](../README.md)
