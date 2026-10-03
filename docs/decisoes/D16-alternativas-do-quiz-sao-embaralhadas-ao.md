# D16 — Alternativas do quiz são embaralhadas ao servir

## Justificativa

No JSON versionado a alternativa correta é escrita em primeiro lugar — é o que torna o currículo
legível em um PR. Servir nessa ordem entregaria o gabarito. A ordem é embaralhada por hash de
(pergunta, alternativa): estável entre requisições, sem relação com a posição original

## Revisar quando

—

---

[Índice das decisões](../README.md)
