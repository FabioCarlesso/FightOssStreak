# Regras de negócio

O comportamento do app, regra por regra. O porquê de cada uma está na decisão citada, em
[`decisoes/`](decisoes/README.md). As regras puras (streak, SRS, desbloqueio, heatmap) vivem em
`shared/domain` e são **espelhadas** no backend, que é a fonte da verdade (D17) — não escreva a
segunda sem a primeira.

## Currículo e desbloqueio

- O currículo é **dado, não código** (D11): 46 nós em 9 módulos, em
  `backend/src/main/resources/curriculum/*.json`, ingeridos na subida. O conteúdo está em
  [`produto/curriculo.md`](produto/curriculo.md) e o formato do JSON em
  `backend/src/main/resources/curriculum/README.md`.
- Um nó desbloqueia quando seus pré-requisitos estão concluídos: **todos** (`ALL`, padrão) ou
  **qualquer um** (`ANY`), declarado por nó (D13).
- Onde há quiz, é ele que conclui o nó; nó sem quiz é concluído pelo registro de drill (D15).
- O quiz é corrigido no servidor, sai de um banco rotativo por nó (D44) e tem as alternativas
  embaralhadas ao servir (D16).

## Drill e revisão espaçada

- Registrar drill ("treinei isso hoje") reagenda o nó pelo SM-2 e alimenta o streak.
- O drill grava, no momento do registro, se o nó estava vencido (`was_due`) e para quando estava
  agendado (`due_on`) — é o que mede as revisões atendidas, porque isso não se reconstitui depois
  (D20).
- A home lista o que drillar hoje, do mais atrasado para o menos. Rebaixar essa agenda na home é
  reverter a D56c, não mexer em layout.

## Diário de treino (D56, D57, D58)

- O diário é a **entrada**, o currículo é a **saída**: `training_session` é a unidade do que
  aconteceu no tatame, e técnica vinculada **continua sendo um `drill_log`**, com `session_id`
  anulável. Por isso o `TrainingSessionService` **delega ao `DrillService.log`** — SM-2,
  `was_due`/`due_on`, progresso e a devolução de freeze saem de lá. Tabela paralela de "técnica
  treinada" daria ao SRS duas verdades sobre o mesmo fato.
- **Só `trained_on` é obrigatório.** Sessão incompleta é sessão válida e não há rascunho.
- Sessão `DESCANSO` **não recebe técnica**, e sessão com técnica não vira descanso.
- **Peso e sensação são dado referente à saúde**: guardados e mostrados, **nunca interpretados** —
  sem meta, sem faixa, sem alerta e sem correlação apresentada como causa (D57). Ver
  [`privacidade/dados-de-saude.md`](privacidade/dados-de-saude.md).
- Não há exclusão de sessão inteira — correção é por edição. **Corrigir a data leva junto o
  `drilled_on` das técnicas vinculadas**, sem reagendar o SRS.

## Streak e freeze

- O streak é **derivado a cada leitura** e conta **dia com registro**:
  `sessões (exceto DESCANSO) ∪ drills avulsos` (D58). Ancora em ontem: hoje ainda não acabou.
- **O mês perdoa até dois dias** sem quebrar a sequência (D55), configurável por
  `FOS_STREAK_FREEZES_PER_MONTH` (`0` desliga). A tabela `streak_freeze` é **livro-caixa, não
  cache**: guarda o saldo já gasto, e é isso que faz o teto ser "por mês" e não "por corrente".
- Quatro invariantes: **hoje nunca gasta freeze**; buraco só é cobrado quando a caminhada alcança
  outro dia de treino do outro lado dele; dia perdoado que ganha registro depois **devolve** o
  freeze; dia coberto mantém a corrente e **não conta** como dia de treino.
- `GET /api/streak` **escreve**, e GET que escreve tem corrida: quem grava é o
  `StreakFreezeWriter`, com transação própria (`REQUIRES_NEW`) e desfazimento explícito. Não volte a
  gravar isso com `save()`.
- Freeze manual e compra de freeze estão fora de escopo: não há economia de pontos no FOS.
- O **heatmap** da home lê o **mesmo** conjunto de dias, por dia, em `GET /api/streak/historico`;
  dia perdoado é marcado sem entrar na escala de intensidade (D59).

## Aviso de responsabilidade

O aceite é **por conta**, com data e versão do texto, e fica **depois** do login. Mudança material
no texto sobe `fos.disclaimer-version` e reexibe o aceite. Textos e histórico de versões em
[`produto/disclaimer.md`](produto/disclaimer.md).

## Modo demonstração da árvore

Na árvore (`/arvore`), o botão **Demonstração**, no card *Progresso*, abre os 46 nós de todos os
módulos para inspeção ignorando os pré-requisitos — serve para revisar conceito, vídeo e redação das
perguntas sem passar no quiz de cada nó anterior. Com o modo ligado, uma faixa no topo diz isso em
todas as telas e oferece o desligar.

**A demonstração não grava**: em nó que estaria bloqueado o quiz aparece só para leitura e o
registro de drill fica fora, então progresso, streak e agenda de revisão ficam intactos, e os
contadores da árvore continuam mostrando o que está travado de verdade (D31). O estado vive na
sessão do navegador — sobrevive a um F5, não a uma aba nova.

Não confundir com a **conta de demonstração** (D39), que fica do outro lado do portão: aquela é um
link público que abre o app numa conta temporária e **grava de verdade**
([`autenticacao.md`](autenticacao.md#demonstração-pública)); esta é inspeção de quem já está logado,
e **não grava nada**.

## Métricas do MVP

`GET /api/metrics/mvp` e a tela `/progresso` medem os quatro critérios de sucesso sobre o uso real.
Metas, o que cada uma responde e como é medida em [`produto/mvp-web.md`](produto/mvp-web.md).
