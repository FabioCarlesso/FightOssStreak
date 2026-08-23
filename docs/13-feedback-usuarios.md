# Plano: feedback de usuários

## Problema

Não existe hoje canal nenhum dentro do app para reportar bug, pedir troca de vídeo, apontar
conteúdo errado ou sugerir funcionalidade. Quem usa o app (o autor, e desde D36/D37 quem tiver
acesso aprovado) só consegue avisar por fora — mensagem, memória, ou uma issue manual no GitHub.
Isso é o mesmo silêncio que a D38 corrigiu do outro lado (a fila de acesso): sinal que existe mas
não tem lugar para pousar.

## Decisão de forma: fila própria no app, não issue automática nem só e-mail

Segue o desenho que D36–D38 já validou para a fila de acesso — nova entidade com `user_id`,
endpoint de escrita para quem usa e endpoint de leitura/decisão restrito ao dono
(`fos.auth.owner-emails`, mesmo portão do `AdminController`) — em vez de:

- **Virar issue no GitHub direto**: exigiria token do GitHub configurado em produção (superfície
  de credencial nova, o que a app hoje evita — D36 nota que "provedor ou envio de e-mail sem
  credencial não é registrado"), e abriria um endpoint público capaz de criar conteúdo num
  repositório de terceiro sem limite óbvio de abuso.
- **Só e-mail para o dono**: mais simples, mas sem histórico, sem status e sem forma de saber o
  que já foi visto ou resolvido — o mesmo defeito que a D38 documentou como "a fila só andava
  quando o autor lembrava de abrir a tela".

Fila própria custa mais código agora e paga menos atrito depois: o dono decide dentro do próprio
app, com o mesmo modelo mental da fila de acesso.

## Escopo

- Formulário genérico, acessível de qualquer tela (ex: link fixo no rodapé, ao lado do que já
  leva à landing/apresentação).
- Campo de nó **opcional**: pré-preenchido quando o formulário é aberto a partir da página de um
  nó (ex.: botão "reportar problema neste nó" perto do vídeo ou do conceito); em branco quando é
  sugestão geral (bug de app, nova funcionalidade).
- Categoria obrigatória, para o dono priorizar sem abrir cada item: `BUG`, `CONTEUDO_ERRADO`,
  `TROCA_DE_VIDEO`, `SUGESTAO_FUNCIONALIDADE`, `OUTRO`.
- Texto livre obrigatório (a descrição em si).
- Tela do dono: lista ordenada da mais antiga para a mais nova (mesmo critério da fila de
  acesso), com nó referenciado (se houver), categoria, autor, data — e ação de mudar status.
- Status: `ABERTO` → `EM_ANALISE` / `RESOLVIDO` / `RECUSADO`. Sem reabertura automática pelo
  usuário (mesmo espírito da D37 para conta recusada) — se quiser revisitar, manda outro.

## Fora de escopo (nesta fatia)

- Resposta ao usuário dentro do app (fio de conversa, notificação de status mudou).Autor decide
  por fora (e-mail, ou nada) por enquanto; formalizar isso é decisão futura, só se a falta de
  retorno virar reclamação de quem manda feedback.
- Aviso automático por e-mail a cada feedback novo ou resumo em janela (o que a D38 fez para a
  fila de acesso). Considerar depois, se a fila de feedback também for esquecida em silêncio —
  não antecipar a mecânica antes de saber se o volume justifica.
- Feedback anônimo / de quem não tem conta: exigiria endpoint público sem sessão, com todo o
  problema de abuso que a D37 já resolveu para o pedido de acesso (nenhum e-mail sai sem
  aprovação, nenhuma escrita sem autenticação). Quem não tem conta aprovada não teria como enviar
  — condizente com o app ser de uso pessoal/aprovado, não público.
- Upload de imagem/anexo. Texto descreve; se um print for essencial, o autor pede por fora.

## Desenho técnico (rascunho)

- **Tabela nova** `feedback` (migration `V10__feedback.sql`): `id`, `user_id` (FK `app_user`,
  quem mandou), `node_id` (FK `node`, nullable), `category`, `message`, `status`, `created_at`,
  `decided_at`, `decided_by` (FK `app_user`, nullable) — mesmo par decisão/decisor que `app_user`
  já tem para aprovação de conta.
- **Endpoints**:
  - `POST /api/feedback` — quem está autenticado e aprovado manda; `userId` sai do
    `CurrentUserProvider`, nunca do corpo.
  - `GET /api/admin/feedback` — restrito ao dono, mesmo interceptor de `/api/admin/**`
    (`AccessGateInterceptor`, ver D36 sobre o cuidado com caminho decodificado).
  - `POST /api/admin/feedback/{id}/status` — muda status; corpo com o novo status.
- **Currículo continua dado** (regra 1 do CLAUDE.md): `node_id` referencia o nó pelo id que já
  existe no banco (ingerido do JSON), o formulário não hardcoda nó nenhum.
- **Tipos gerados**: novo schema no OpenAPI (`FeedbackRequest`, `FeedbackView`, categoria/status
  como enum) e `npm run gen:types` depois de fechar o contrato — não editar
  `shared/types/generated/` à mão.

## Perguntas em aberto antes de codar

1. Quem pode mandar feedback: só conta aprovada, ou também a demonstrativa (D39)? Como a demo é
   descartável e sem identidade — provavelmente **não**, mas vale confirmar antes de escrever o
   guard.
2. Categoria fixa (enum) é suficiente, ou vale campo livre de "assunto" além da categoria?
3. O botão "reportar problema neste nó" mora dentro da página do nó (perto do vídeo) ou é sempre
   o mesmo formulário genérico com o campo pré-preenchido, sem botão contextual novo?

## Próximos passos

- [ ] Decidir as três perguntas acima
- [ ] Migration + entidade + repositório
- [ ] `FeedbackController` (escrita) + extensão do `AdminController` (leitura/decisão), ou
      `FeedbackAdminController` separado — decidir ao implementar, conforme o tamanho
- [ ] Contrato OpenAPI + `npm run gen:types`
- [ ] Formulário no web + tela do dono
- [ ] Registrar em `07-decisoes.md` quando as perguntas em aberto forem fechadas
