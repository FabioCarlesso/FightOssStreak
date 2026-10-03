# D18 — `main` protegida: default fixa, só muda por PR, merge só com CI verde

## Justificativa

Projeto solo tende a virar push direto em `main`, e aí o CI vira relatório em vez de portão — quebra
chega em `main` e o histórico não tem ponto de revisão. A ruleset é declarada em
`.github/rulesets/main.json` e aplicada por `scripts/apply-repo-rules.sh`, porque configuração de
servidor sem versionamento não tem histórico nem forma de restaurar. Sem exigir aprovação humana
(`required_approving_review_count: 0`): ninguém aprova o próprio PR, e com um contribuidor só isso
travaria todo merge. Detalhes em `docs/repositorio.md`

## Revisar quando

Ao entrar um segundo contribuidor — aí a aprovação passa a ser possível e vale exigir 1

---

[Índice das decisões](../README.md)
