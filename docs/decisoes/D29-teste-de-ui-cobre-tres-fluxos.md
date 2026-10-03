# D29 — Teste de UI cobre três fluxos, e só três

## Justificativa

O CI do web rodava testes de regra, typecheck e build — nada tocava a camada onde o produto é usado,
que é onde mora lógica que compila sem erro e ainda assim erra. Os três escolhidos são os que
decidem se o app é utilizável: o `DisclaimerGate` (que é requisito de produto, `docs/produto/disclaimer.md`, e cujo defeito
silencioso — deixar passar sem aceite — é o mais grave do app), o `QuizForm` (que trata o
`quiz_unavailable` de 35 dos 46 nós, D15) e o `DrillForm` (cujo preview não promete intervalo acima
de 2 repetições, porque o fator de facilidade real vive no backend). Vitest + Testing Library em
jsdom, com o cliente de API mockado no módulo: nenhum teste toca a rede.

**Fora de escopo por decisão:** navegador de verdade (Playwright) é desproporcional neste estágio, e
a regra de SRS não é reduplicada aqui — ela já é espelhada entre `shared/domain` e o backend com
valores fixados (D17), e testá-la de novo pela UI cobriria a mesma coisa por um caminho mais frágil

## Revisar quando

Se aparecer bug de integração entre telas que teste de componente não pega — aí o sinal é que falta
um caminho end-to-end, não mais teste de componente

---

[Índice das decisões](../README.md)
