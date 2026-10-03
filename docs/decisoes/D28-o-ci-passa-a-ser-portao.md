# D28 — O CI passa a ser portão de estilo, e a formatação inicial entra em um commit só

## Justificativa

Não havia ESLint, Prettier, Spotless nem `.editorconfig`: o padrão do código existia só na cabeça de
quem escreveu. Num projeto solo isso pesa mais, não menos — a ruleset exige zero aprovações humanas
(D18), então o CI *é* a revisão, e sem lint um `console.log` esquecido ou um import morto entra em
`main` sem atrito. Os passos entram nos jobs `backend` e `web` que já existem, não em job novo,
porque job novo só vira portão entrando na ruleset e aí precisaria rodar sem filtro de path (D19).

**Java em `googleJavaFormat` variante AOSP** (4 espaços), que é o que o código já usava: a variante
padrão reformataria o backend inteiro para 2 espaços, mudança de estilo grande travestida de
configuração.

**Markdown ficou fora do Prettier**, e isso é decisão: ele alinha colunas de tabela com espaço, e as
células desta tabela são parágrafos — o resultado eram linhas de milhares de colunas em que
acrescentar uma decisão reescrevia a tabela inteira, o oposto do que um log revisável em PR precisa
ser. Fora do lint também ficam os gerados (`shared/types/generated/`, `backend/openapi.json`, este
porque `gen:types` o reescreve com `JSON.stringify` e disputar a formatação faria as duas
ferramentas se desfazerem em loop) e o currículo, que é dado editorial (D11)

## Revisar quando

Ao endurecer regras que exijam refatoração (complexidade ciclomática e afins) — a base foi
estabelecida com o histórico limpo justamente para que isso venha depois. Se o retrabalho de
formatar depois do push incomodar, aí entra hook de pre-commit

---

[Índice das decisões](../README.md)
