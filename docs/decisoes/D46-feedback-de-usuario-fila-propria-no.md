# D46 — Feedback de usuário: fila própria no app, nó opcional, demo bloqueada

## Justificativa

`docs/produto/feedback.md` planejou a funcionalidade; esta entrada registra as três decisões que
ficaram em aberto até a implementação.

**Fila própria, e não issue automática no GitHub nem só e-mail**: mesmo desenho da fila de acesso
(D36–D38) — tabela `feedback`, `POST /api/feedback` para quem manda, `GET/POST
/api/admin/feedback/**` sob o mesmo `OwnerOnlyInterceptor` para quem decide — porque virar issue
exigiria token do GitHub em produção (a app hoje sobe sem segredo nenhum quando falta credencial, e
este seria o primeiro caso a exigir uma) e só e-mail não teria histórico nem status.

**Conta de demonstração (D39) não manda feedback**: reaproveita o mesmo raciocínio que já faz
`AccountService.isOwner` responder falso para ela — sem identidade própria e descartável em duas
horas, um feedback dela não tem para quem responder nem por quanto tempo vale.

**Sem botão contextual na página do nó nesta fatia**: o formulário é único e genérico, com campo de
nó por código (texto livre, validado contra o currículo no backend — nó inexistente responde 404,
igual a qualquer outra rota); pré-selecionar o nó a partir da própria página é UI que pode entrar
depois sem mudar contrato.

**Categoria é enum fechado** (`BUG`, `CONTEUDO_ERRADO`, `TROCA_DE_VIDEO`, `SUGESTAO_FUNCIONALIDADE`,
`OUTRO`), sem campo livre de "assunto" além da mensagem — a fila do dono lê a mensagem inteira, um
título curto não pouparia essa leitura. Sem reabertura automática de status (`ABERTO →
EM_ANALISE/RESOLVIDO/RECUSADO`): quem quiser revisitar manda outro feedback, mesmo espírito de conta
recusada não ser reaberta (D37)

## Revisar quando

Se o volume de feedback crescer a ponto de o dono esquecer de abrir a tela — mesmo sintoma que levou
a D38, e a correção seria a mesma: um resumo em janela, não aviso por item. Ou se pedirem
contextualizar o envio a partir da própria página do nó — decisão de UI, não de contrato

---

[Índice das decisões](../README.md)
