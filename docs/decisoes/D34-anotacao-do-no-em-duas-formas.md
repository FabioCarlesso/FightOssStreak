# D34 — Anotação do nó em duas formas: a do drill é histórico, a fixada é releitura

## Justificativa

A #45 nasceu de um achado: a anotação por drill já existia inteira — `drill_log.note` desde a `V1`,
o textarea do `DrillForm` pedindo "o que o professor corrigiu", e `CurriculumQueryService` montando
`recentDrills` na resposta de `GET /api/nodes/{code}` — e o `NodePage` **descartava o campo**. Com o
`setNote('')` depois de salvar, o texto sumia no instante do envio: escrita no vazio. Exibir o
histórico é metade da issue e não custou backend nenhum. A outra metade é a **anotação fixada**, que
existe porque as duas perguntas são diferentes: "o que aconteceu no treino de 12/08" é log e não se
edita; "o detalhe que eu sempre esqueço nesta técnica" precisa estar à vista toda vez que o nó abre,
e garimpá-lo em dez registros é o mesmo que não tê-lo.

**Coluna em `user_progress`, não tabela nova**: a chave da anotação é (usuário, nó), que já é a PK
de lá.

**`PUT`, não `POST`**: é valor que se substitui, não evento que se acumula — o oposto do drill.

**Nota em branco limpa** em vez de gravar string vazia, para "sem anotação" ter uma representação
só. A decisão delicada foi o que gravar em `user_progress.status` quando o nó nunca foi tocado e a
linha precisa nascer: `IN_PROGRESS` é o valor que o drill usa e faria a árvore anunciar "em
andamento" um nó que a pessoa só comentou. Gravar **`AVAILABLE`** não muda nada, e isso é
verificável: `UnlockService.resolveStatuses` devolve o status persistido como está quando ele não é
LOCKED, e resolve o bloqueio pelo grafo *antes* de consultá-lo — a linha nova é indistinguível de
não haver linha, e há teste comparando o `summary` da árvore antes e depois de anotar. Anotar não
mexe em streak, SRS, conclusão nem em nenhuma métrica de `docs/produto/mvp-web.md`.

**Na UI a fixada fica no card do conceito** e o histórico no fim, depois do registro de drill: a
ordem é a hierarquia — o conceito é do currículo, a anotação é o que ele virou no seu treino, e o
histórico é onde isso se acumula. O que a tela grava vence o dado recarregado até a resposta chegar,
senão a anotação antiga reaparece por um round-trip logo depois de salvar, que é justamente quando
se olha para conferir

## Revisar quando

Se a fixada começar a ser usada como diário (uma linha por treino colada no mesmo campo) — aí o que
falta não é campo, é o histórico estar bom o suficiente para ser lido

---

[Índice das decisões](../README.md)
