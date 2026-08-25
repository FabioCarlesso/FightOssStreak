# Plano: feedback de usuários

## Problema

Não existe hoje canal nenhum dentro do app para reportar bug, pedir troca de vídeo, apontar
conteúdo errado ou sugerir funcionalidade. Quem usa o app (o autor, e desde D36/D37 quem tiver
acesso aprovado) só consegue avisar por fora — mensagem, memória, ou uma issue manual no GitHub.
> **Nota (D48):** a fila de acesso citada abaixo como precedente de desenho **não existe mais** — o
> cadastro abriu (D47) e o portão de aprovação foi desmontado. As referências a ela aqui são
> históricas: o que sobreviveu foi o *formato* (entidade com `user_id`, rota pública para quem manda,
> rota de administração para quem decide), e é ele que a fila de feedback usa.

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

## Perguntas em aberto — resolvidas para implementar

1. **Conta demonstrativa (D39) não manda feedback.** Ela não tem identidade própria
   (`provider = demo`, sem e-mail, `isOwner` sempre falso) e é descartável em duas horas — um
   feedback dela não tem para quem responder nem por quanto tempo vale. O guard nega com o mesmo
   tipo de erro que a demo já usa para não ter poder além do próprio progresso.
2. **Categoria fixa (enum) é suficiente.** Campo livre de "assunto" duplicaria o que a primeira
   linha da mensagem já cobre, sem ganho — a fila do dono lê a mensagem inteira, não só o título.
3. **Sem botão contextual novo na página do nó nesta fatia.** O formulário é único e genérico,
   acessível do rodapé; o campo de nó fica nele, com busca/seleção manual. Prender a pré-seleção
   à página do nó é UI adicional que pode entrar depois, sem mudar contrato nenhum — o campo já
   nasce pronto para receber o valor de onde quer que venha.

## Próximos passos

- [x] Decidir as três perguntas acima
- [x] Migration + entidade + repositório
- [x] `FeedbackController` (escrita) + extensão do `AdminController` (leitura/decisão) — coube no
      mesmo controller, sem justificar arquivo próprio
- [x] Contrato OpenAPI + `npm run gen:types`
- [x] Formulário no web (`/feedback`, link no rodapé) + tela do dono na mesma página
- [x] Registrar em `07-decisoes.md` (D46)
