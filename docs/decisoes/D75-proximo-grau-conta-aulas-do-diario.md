# D75 — O próximo grau conta as aulas do diário contra os mínimos que a academia de quem usa exige

## Justificativa

O app não estava sendo usado no dia a dia, e o motivo mais concreto para registrar toda aula é o
**próximo grau**: academia costuma exigir um mínimo de aulas por grau, muitas vezes separado entre
com e sem kimono. A #193 é a primeira de quatro fases do plano de graduação (#193 a #196) e entra
pequena de propósito: contar o que o diário já registra. Quatro escolhas carregam o desenho.

**(a) Os números vêm de fora, digitados pela própria pessoa.** O app não traz lista, mínimo nem nome
de academia nenhuma. Material de graduação costuma ser protegido por direito autoral, e o
repositório é público: transcrever um manual para `curriculum/*.json` (D11) seria publicá-lo. Por
isso a meta é **dado da conta** (`graduation_goal`), com rótulo de grau em texto livre — cada
academia nomeia do seu jeito — e todos os mínimos opcionais.

**(b) Um mínimo de aulas aqui não contradiz os "números descritivos, sem meta" de
`docs/produto/mvp-web.md`.** Aquela regra existe para o app não **inventar** alvo de quantidade, que
empurraria o produto para o caderno. Este alvo não é do app: é a régua que a academia já usa, e o
app só mostra a distância até ela. O card **informa e não premia** — bater o mínimo não muda cor
nem vira destaque, como os marcos do streak — e diz que mínimo de aulas não é graduação: quem decide
o grau é o professor (D1). O card mora **abaixo** da agenda de revisão e do diário (D56c).

**(c) Marcar "sem kimono" no diário não reabre a D6.** O currículo continua só Gi: não entra nó, quiz
nem vídeo No-Gi. `training_session.attire` registra **como a aula aconteceu**, porque é assim que a
academia conta. A coluna é anulável (sem backfill; sessão antiga é "não informado") e só existe para
o que acontece no tatame — `FISICO` e `DESCANSO` são recusados com 400 se vierem com kimono.

**(d) Conta só `AULA`, e a contagem é derivada a cada leitura.** Rola, drill e físico não são aula, e
somá-los inflaria o número que a pessoa compara com o da academia. Aula sem marcação de kimono
entra no total e em nenhuma das metades, e o card diz isso. Nada grava número de aula: corrigir uma
sessão no diário muda a contagem na leitura seguinte, sem segunda verdade (a mesma lógica do streak,
D55/D58). A regra pura é `countClassesSince`, em `shared/domain`, espelhada na consulta do backend
(D17).

O último "com ou sem kimono" usado fica **no navegador** (`fos.ultimoKimono`) para a próxima sessão já
vir marcada: registrar a aula não pode custar um toque a mais, e é conveniência de quem digita, não
dado da conta. A meta nunca entra na coleta da D50 nem no painel da D52, e `DELETE /api/me` a leva
junto.

## Revisar quando

- Se as revisões atendidas caírem abaixo dos 60% de `docs/produto/mvp-web.md` enquanto as aulas
  registradas sobem: o card virou contador de presença e está competindo com a agenda, e ele perde
  lugar na home antes de qualquer outra coisa.
- Se aparecer pedido de lista de requisitos de academia embutida no app, ou compartilhada entre
  contas: isso é distribuir material de terceiro, e só cabe com autorização expressa da academia, em
  decisão própria (a #196 faz o checklist **pessoal**).
- Se aparecer pedido de contar `ROLA` ou `OUTRO` como aula: a regra está em um lugar só
  (`kindCountsAsClass` e a consulta `countClassesSince`), mas mudar o que conta muda o que o número
  significa, e isso é decisão e não ajuste.

---

[Índice das decisões](../README.md)
