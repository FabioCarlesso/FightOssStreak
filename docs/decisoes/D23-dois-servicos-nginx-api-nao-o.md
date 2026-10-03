# D23 — Dois serviços (nginx + API), não o `dist/` empacotado dentro do JAR

## Justificativa

Servir o front pelo próprio Spring seria um serviço a menos, e é tentador. Mas amarraria os dois
ciclos de build: trocar uma cor no CSS passaria a exigir recompilar o backend e reiniciar a API,
derrubando o processo por uma mudança que não é dele. Com nginx na frente, a mesma-origem — que é a
premissa de `web/src/api/client.ts` e do `WebCorsConfig` restrito a localhost — passa a existir de
verdade fora do dev server do Vite, em vez de só no proxy que some quando o `npm run dev` para. E o
estático ganha cache próprio: bundle com hash imutável, `index.html` sem cache

## Revisar quando

Se o custo de operar dois serviços incomodar antes de o projeto ter tráfego que justifique
separá-los

---

[Índice das decisões](../README.md)
