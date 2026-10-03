# D30 — A regra 7 vira verificação; o CodeQL fica de fora da ruleset de propósito

## Justificativa

Duas decisões sobre o que é portão.

**A guarda da ruleset** (`scripts/verificar-ruleset.mjs`) existe porque o contexto de um required
check é o nome do job: renomear um job derruba a proteção de `main` em silêncio, e isso estava
documentado no CLAUDE.md e em `docs/repositorio.md` — mas documentação depende de alguém lembrar no momento em que
está fazendo outra coisa. Roda como primeiro passo do job `web`, antes de qualquer instalação, e lê
um recorte do bloco `jobs:` em vez de usar parser de YAML justamente para poder ser o primeiro.

**O CodeQL faz o caminho oposto e não entra na ruleset**: análise de segurança que trava merge por
falso positivo vira coisa que se aprende a ignorar — o valor está no alerta na aba Security. Junto
vieram o Dependabot nos três ecossistemas (sem automerge, porque mergear sem ler changelog é o que a
D18 evita) e `concurrency` nos dois workflows, que o job `backend` justificava sozinho por subir a
aplicação inteira para diffar o `openapi.json`. E o build das duas imagens Docker passou a rodar no
CI, porque desde a Railway (D22) a imagem é o caminho de produção e o build do `web` quebra por
mudança em `package-lock.json` ou na estrutura de `shared/` sem quebrar teste nenhum

## Revisar quando

O CodeQL vira portão só se os alertas dele passarem a ser tratados com a mesma seriedade que uma
falha de teste. A guarda da ruleset sai no dia em que o GitHub oferecer a checagem nativamente

---

[Índice das decisões](../README.md)
