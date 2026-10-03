# D64 — Exclusão de conta apaga o feedback do autor e esquece quem decidiu

## Justificativa

A auditoria (FOS-04) achou `feedback.user_id` e `feedback.decided_by` com chave estrangeira para
`app_user` (V10) e o `AccountService.delete` sem tocar na tabela: quem tinha mandado feedback, ou
decidido algum, recebia 500 no `DELETE /api/me` — a transação revertia, a conta ficava e o 5xx
entrava na taxa do alerta da D54. O mesmo caminho servia à fusão de identidade e à varredura de
demonstração.

**O feedback do autor é apagado, não anonimizado**: a mensagem é texto livre de quem escreveu e pode
trazer dado pessoal, e anonimizar exigiria afrouxar o `NOT NULL` de `user_id`, que existe porque
feedback sem autor não tem a quem responder. A fila perde o histórico daquela pessoa, e é a troca
certa: a promessa de `docs/privacidade/README.md` é "o que sai, sai".

**Onde a conta só decidiu**, o feedback é de outra pessoa e fica, com `decided_by` nulo — o status e
a data da decisão continuam.

**`app_user.decided_by` e `role_changed_by` de outras contas não são zerados**: não têm FK (V13) de
propósito, e `docs/privacidade/README.md` já registra que o número sobrevive na linha alheia e deixa de
apontar para alguém — zerar apagaria a trilha de que houve uma decisão.

## Revisar quando

Se a fila precisar do histórico de quem saiu (contagem por categoria, por exemplo), o caminho é
agregado sem texto, não reter a mensagem. Se `app_user.decided_by` ganhar FK, a exclusão precisa
zerá-lo como faz aqui

---

[Índice das decisões](../README.md)
