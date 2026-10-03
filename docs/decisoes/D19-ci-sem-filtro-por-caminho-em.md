# D19 — CI sem filtro por caminho em pull request

## Justificativa

Workflow descartado por filtro de `paths` não reporta status algum, e required check que não reporta
trava o PR em "waiting for status" para sempre — um PR só de `docs/` nunca mergearia. O filtro fica
no `push` para `main`, onde nada depende do resultado. Custo: os dois jobs rodam em todo PR

## Revisar quando

Se o tempo de CI incomodar — aí a saída é detecção de mudança em job, com job pulado por `if:` (job
pulado reporta sucesso; workflow filtrado não reporta nada)

---

[Índice das decisões](../README.md)
