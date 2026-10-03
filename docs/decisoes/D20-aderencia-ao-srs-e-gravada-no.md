# D20 — Aderência ao SRS é gravada no momento do drill, não inferida depois

## Justificativa

Duas das quatro métricas de `docs/produto/mvp-web.md` não eram calculáveis: `srs_review.next_review_on` é sobrescrito
pelo próprio drill, então "o nó estava vencido?" desaparece no instante do registro, e
`user_progress.last_quiz_score` guarda só a última nota, tornando um quiz refeito indistinguível de
um respondido uma vez. `drill_log` passou a carregar `was_due`/`due_on` e as submissões viraram log
em `quiz_attempt`. Duas regras de contagem, escolhidas para a métrica não mentir para cima: a
aderência recorta numerador e denominador pela mesma régua (só o que venceu dentro da janela), e
janela sem nada agendado responde "sem agenda" em vez de 0%; e "quiz refeito" conta só tentativa
posterior à primeira aprovação, porque errar e passar na segunda é conclusão normal, não repetição
espontânea

## Revisar quando

Se a aderência passar a ser lida como nota de desempenho em vez de sinal sobre a mecânica — aí o
problema é de leitura, não de cálculo

---

[Índice das decisões](../README.md)
