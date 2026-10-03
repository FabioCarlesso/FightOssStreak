# D57 — Peso e sensação são guardados e mostrados, nunca interpretados

## Justificativa

Entram porque são o que faz o registro valer no dia sem técnica nova — sem eles a D56 não cobre a
rotina que a motivou. Mas peso em esporte de luta puxa corte de peso, e sensação puxa "treinar ou
descansar": os dois são conselho, e o FOS não aconselha (D1, `docs/produto/disclaimer.md`). Então sem meta, sem faixa
saudável, sem alerta, e **sem correlação apresentada como causa** — sensação contra recall em vinte
sessões é superstição com gráfico; a série pode aparecer na tela, a conclusão não. Peso é **opcional
e por sessão**, nunca diário: pesar no dia do treino é o hábito real, e um tracker diário seria
outro produto, com outra régua de risco. Guardado em `NUMERIC(5,2)`, não em ponto flutuante.
Consequência que é parte da decisão e não pós-venda: peso e sensação são **dado referente à saúde**
(LGPD, art. 5º, II), palavra que `docs/privacidade/README.md` não usa nenhuma vez hoje — a seção nova
entra no mesmo PR, dizendo que ficam só na conta de quem escreveu, que `DELETE /api/me` os leva
junto e que a coleta da D50 e o painel da D52 **nunca** os veem. O aviso de `docs/produto/disclaimer.md` ganha uma
frase sobre não orientar corte de peso, e essa frase **sobe a versão do aceite**

## Revisar quando

Se aparecer pedido de meta de peso, de alerta por sensação ou de qualquer leitura que vire
recomendação, é outro produto: reabrir a D1 antes de escrever a consulta

---

[Índice das decisões](../README.md)
