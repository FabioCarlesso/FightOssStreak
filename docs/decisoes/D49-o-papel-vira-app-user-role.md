# D49 — O papel vira `app_user.role` e o bloqueio reativo ganha quem o produza — dois critérios da D48 puxados de uma vez, sem tabela de permissão e sem a fila de volta

## Justificativa

A D48 não deixou isto em aberto: escreveu os dois gatilhos com todas as letras — *"quando aparecer
um segundo administrador, ou uma permissão que não seja 'tudo' — aí `Role` vira tabela"* e *"quando
surgir conta abusiva de verdade: aí entra bloqueio reativo por `RECUSADO`, não fila de aprovação"* —
e os dois foram puxados juntos. Esta entrada é o cumprimento de um critério registrado, não reversão
por gosto.

**Papel vira dado sem virar sistema de permissão.** Continuam sendo dois papéis, `ADMIN` e
`USUARIO`, e perfil por recurso segue fora de escopo pelo mesmo argumento da D48: é infra para um
problema que ainda não existe. O que muda é a **fonte** — `app_user.role`, `VARCHAR NOT NULL` com
default `'USUARIO'` —, não o **ponto de decisão**: `AccountService.roleOf` continua sendo o único
lugar que responde "esta conta administra?", porque espalhar a checagem é exatamente o defeito que a
D48 evitou de propósito. O que se ganha é o incômodo que motivou a issue: **administrador novo
deixou de exigir deploy**, e quem administra passa a enxergar quem se cadastrou sem abrir `psql`. A
exigência de e-mail verificado não afrouxou, mudou de momento: vale onde a promoção acontece — `POST
/api/admin/usuarios/{id}/role` recusa com `409` conta sem `primary_email` — em vez de a cada leitura
da variável.

**`fos.auth.owner-emails` não sai; é rebaixada a semente e saída de emergência.** Sem ela, ambiente
novo sobe **sem nenhum administrador** e não existe ninguém com poder de promover o primeiro — o
bootstrap teria de ser `UPDATE` no banco, que é o oposto do que esta decisão foi buscar. Então a
lista continua: promove na subida (`AdminSeedStartup`, sobre `seedAdmins()`) e em todo login com
e-mail verificado (`applyOwnerSeed`). E **promove sem nunca rebaixar**, o que é a metade que importa
— tirar um endereço da variável não é ordem de tirar o papel de ninguém, e uma semente que
rebaixasse em silêncio transformaria um deploy com a lista mal preenchida em perda de acesso à
administração do app, justo no ambiente onde ninguém sobrou para consertar. Pelo mesmo motivo a
migration **não** faz backfill de `ADMIN`: SQL não enxerga variável de ambiente, e um backfill que
chutasse a lista promoveria errado — quem promove é a subida, onde a lista existe e onde dá para
exigir o e-mail verificado.

**Bloqueio reativo por `RECUSADO`, e não a fila de aprovação de volta.** A diferença não é de forma,
é de momento: a fila barrava todo mundo **antes** de saber quem era, e o bloqueio barra alguém
**depois** de haver motivo — o primeiro é um portão contra desconhecidos, o segundo é uma decisão
sobre uma pessoa. Nada de portão novo foi escrito: `AccessStatus.RECUSADO`, o
`AccessGateInterceptor` que o lê e o `403 acesso_recusado` estão de pé desde a D48 esperando
exatamente por isto, e `POST /api/admin/usuarios/{id}/status` é só o produtor que faltava — nem uma
linha do interceptor mudou, que é o teste de que a D48 apostou certo ao deixá-lo em pé sem barrar
ninguém. Bloquear vale **na ação seguinte** da conta, e não só no próximo login — inclusive numa aba
que já estava aberta —, e isso sai de graça: o portão relê `access_status` a cada requisição, então
basta a linha do banco mudar.

**A primeira versão derrubava as sessões abertas por `SessionLogin.endSessionsOf`, e essa foi a
decisão errada**, descoberta só ao rodar o app: marcar a sessão como expirada faz o
`ConcurrentSessionFilter` responder `401 sessao_encerrada` **antes** de qualquer interceptor, o `403
acesso_recusado` nunca acontece, e a web — que lê 401 como "não há sessão" — devolve a pessoa
bloqueada para a tela de login. Ou seja: derrubar a sessão não adiantava bloqueio nenhum, porque o
portão já barrava, e custava exatamente a tela que explica o que houve, reintroduzindo o looping de
login que o código no corpo existe para evitar.

**Bloqueio não encosta em sessão.** A redefinição de senha continua derrubando, e ali é outro
problema: o ponto é expulsar quem entrou com a senha antiga, e 401 é a resposta certa porque a
pessoa *deve* entrar de novo.

**O que se aceita, e está escrito porque dói**: quem administra passa a ver **o e-mail de todas as
contas** — é a primeira tela do app com dado pessoal de terceiro, e `docs/privacidade/README.md`
registra isso; um clique errado **tranca alguém para fora** do app, daí as guardas em `409` (e-mail
não verificado, ação sobre si mesmo, último `ADMIN`, conta de demonstração) e a confirmação com o
e-mail da conta afetada escrito no diálogo; e a trilha de auditoria (`decided_by`, `decided_reason`)
guarda uma **decisão sobre uma pessoa**, com nome e motivo, pelo tempo de vida da conta.

**O que continua fora**: excluir a conta de outra pessoa — exclusão é do titular, por `DELETE
/api/me`, e a assimetria é o argumento inteiro, porque bloqueio é reversível e apagar não é; e por
isso conta bloqueada **continua podendo se excluir** (`/api/me` está fora do portão), senão bloquear
viraria sequestro de dado pessoal. Fora também: ação em massa, detecção automática de abuso,
denúncia, quarentena, expiração de bloqueio e "último acesso" — o último nem existe como dado, e
criá-lo é coleta de uso, que é outra issue.

**Detalhe que só apareceu implementando**: `decided_at`/`decided_by`/`decided_reason` e
`role_changed_at`/`role_changed_by` são **pares separados**, e não um par reaproveitado, porque são
duas decisões diferentes sobre a mesma conta — "virou administrador" e "foi bloqueada" — e
guardá-las na mesma coluna faria a segunda apagar a primeira, justo no dado que existe para
responder "quem fez isso?". E `decided_by`/`role_changed_by` **não têm FK** para `app_user`: quem
decidiu pode excluir a própria conta depois, e a FK transformaria essa exclusão em erro de
integridade ou apagaria a trilha junto — o id ali é registro histórico, não referência viva.

## Revisar quando

Quando aparecer uma permissão que **não** seja "tudo" — aí sim é tabela de papéis de verdade, com
perfil por recurso, e não um enum de dois valores. Ou quando o bloqueio manual não der conta do
volume, e barrar conta abusiva precisar deixar de ser um clique por vez. Ou se a trilha de auditoria
precisar responder "o que aconteceu com esta conta ao longo do tempo?" em vez de "qual foi a última
decisão" — aí as colunas na conta viram tabela de eventos

---

[Índice das decisões](../README.md)
