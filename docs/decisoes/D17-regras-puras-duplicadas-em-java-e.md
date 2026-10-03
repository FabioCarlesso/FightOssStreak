# D17 — Regras puras duplicadas em Java e TypeScript

## Justificativa

`docs/arquitetura.md` pede `shared/domain` com streak, SRS e desbloqueio para reaproveitar em mobile; o backend
precisa das mesmas regras porque é quem persiste. O backend é a fonte da verdade; o TS serve a
preview otimista na UI. Os casos de teste de SRS são espelhados nos dois lados, com valores fixados,
para que divergência quebre o build

## Revisar quando

Se a duplicação passar a divergir com frequência — aí vale mover o cálculo para um só lado e aceitar
o round-trip

---

[Índice das decisões](../README.md)
