# D48 — A fila de aprovação é desmontada, e `fos.auth.owner-emails` passa a significar "conta de administração" — sem tabela de papéis

## Justificativa

Com o cadastro aberto (D47), o estado `PENDENTE` deixou de ser produzido e tudo que foi construído
em volta dele ficou sem função: a fila em `/api/admin/solicitacoes`, a tela `AccessRequestsPage`, o
resumo horário do dono (D38, com `QueueNoticeSchedule` e `queue_notice_sent_at`), o 403
`acesso_pendente` e a entrada por link de e-mail inteira (#52) — ela existia para quem a fila
liberava.

**Código morto em caminho de autenticação é pior que código morto comum**: ele continua sendo lido
como se valesse, e o próximo a mexer ali gasta o tempo entendendo um portão que não barra ninguém.
Sai tudo, do código, do schema e da doc; o log das D36/D37/D38 fica como registro do que já foi
verdade.

**`owner-emails` precisava de significado novo, não de remoção.** Depois da D37 ela queria dizer "vê
a fila e decide"; sem fila, ficaria uma lista de configuração sem referente. Passa a querer dizer
**conta de administração** — métricas, fila de feedback e o que vier — e o conceito ganha nome
(`Role`: `ADMIN` ou `USUARIO`), decidido num **ponto só** (`AccountService.roleOf`, lido pelo
`OwnerOnlyInterceptor`) e exposto em `/api/me` como `role`. O web passou a checar esse campo em vez
de um booleano chamado `owner`, que descrevia a fila e não o que a conta pode fazer.

**Nomear sem construir a máquina é o recorte**: nada de tabela de papéis, tela de gestão ou
permissão granular. O critério para isso mudar é concreto — um **segundo** administrador, ou uma
permissão que **não** seja "tudo". Enquanto a lista couber numa variável de ambiente e o poder for
indivisível, tabela é infra para um problema que não existe.

**A exigência de e-mail verificado fica, e ganhou alcance**: "verificado" passou a incluir a
confirmação do próprio app, e não só a de um provedor externo — quem confirma pelo link do cadastro
tem `email_verified = true` igual a quem entrou pelo Google. Sem ela, digitar o endereço do
administrador num provedor que não verifica e-mail daria acesso de administração; a regra não mudou,
só o universo de quem a satisfaz.

**O que se aceita perder, e está escrito porque dói**: `RECUSADO` deixou de ter quem o produza, e o
app fica **sem nenhuma forma de barrar uma conta abusiva** — um app de cadastro aberto e público
nessa condição. O estado e o portão que o lê (`AccessGateInterceptor`) ficam de pé justamente por
isso: o bloqueio reativo, se um dia fizer falta, entra por eles em uma issue própria — e não pela
volta da fila, que era a resposta errada para outro problema.

**A D39 fica de pé e fica estranha**: a exceção que ela abriu (conta de demonstração nascendo
`APROVADO` sem aprovação) perde o contexto que a tornava exceção, porque ninguém mais espera
aprovação. As outras três garantias dela — descartável, sem identidade de ninguém, com prazo de duas
horas — seguem valendo e continuam sendo o que a sustenta. Detalhe que só apareceu implementando:
tirar `ENTRADA` do enum `LoginTokenPurpose` **exigiu apagar as linhas antigas na migration**, porque
valor que o enum não conhece estoura na *leitura* — o erro apareceria dias depois, quando alguém
pedisse redefinição de senha e o app carregasse os tokens pendentes daquela conta.

## Revisar quando

Quando aparecer um segundo administrador, ou uma permissão que não seja "tudo" — aí `Role` vira
tabela. Ou quando surgir conta abusiva de verdade: aí entra bloqueio reativo por `RECUSADO`, não
fila de aprovação

---

[Índice das decisões](../README.md)
