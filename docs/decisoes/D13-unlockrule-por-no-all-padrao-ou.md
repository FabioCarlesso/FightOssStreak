# D13 — `unlockRule` por nó: `ALL` (padrão) ou `ANY`

## Justificativa

`docs/produto/curriculo.md` descreve o Módulo 4 como "pré-requisito: M3.2 **ou** M3.3" — semântica de OU que o modelo
"todos os pré-requisitos concluídos" de `docs/arquitetura.md` não expressa. Em vez de duplicar nós ou inventar
arestas falsas, o nó declara como combinar seus pré-requisitos

## Revisar quando

Se aparecer regra mais complexa que ALL/ANY — aí é sinal de que o currículo precisa de outra
modelagem, não de mais um enum

---

[Índice das decisões](../README.md)
