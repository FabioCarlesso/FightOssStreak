# D56 — O diário de treino vira pilar do MVP: a sessão é a entrada, o currículo é a saída

## Justificativa

A D1 separou "revisar" de "ensinar" e continua de pé — diário nenhum ensina jiu-jitsu —, mas o
corolário que a landing tirou dela ("Não é diário de treino: se a meta fosse anotar o que você fez,
bastava um caderno") descrevia um produto que o próprio autor não usa assim: o treino gera peso,
duração, sensação, o que ficou faltando e, **às vezes**, uma técnica do currículo. Sem lugar para o
resto, o registro não acontece — e é o registro que alimenta a camada de retenção. Entra
`training_session` como unidade do que aconteceu no tatame, com o vínculo de técnicas por dentro.
Três escolhas carregam o desenho.

**(a) Técnica vinculada é o `drill_log` que já existe**, com `session_id` anulável, e não uma tabela
paralela: um segundo registro de "treinei isto" daria ao SRS duas verdades sobre o mesmo fato, e só
uma delas agendaria revisão. O anulável é o que faz os drills anteriores à feature aparecerem no
diário como avulsos, sem backfill e sem migration de dados.

**(b) Vínculo é opcional e retroativo** — sessão sem técnica é cidadã de primeira classe, porque a
rotina real é escrever todo dia e vincular às vezes (rola solta, físico, aula sem nada do
currículo), e vincular depois retroage no SRS e devolve o freeze pela regra (e) da D55.

**(c) A agenda de revisão continua no topo da home**, acima do diário: é o único elemento que o
caderno não faz, e rebaixá-la seria virar o BJJ Notes com quiz junto. Cai a seção "Não é diário de
treino" de `web/src/content/landing.ts` e o parágrafo de diferencial de `docs/produto/visao.md` — o diferencial
passa a ser "diário cujo registro volta agendado", e não "não somos diário"

## Revisar quando

Se as revisões atendidas caírem abaixo dos 60% de `docs/produto/mvp-web.md` enquanto as sessões sobem, o diário
virou caderno e está substituindo a retenção em vez de alimentá-la — aí a agenda volta a ser a única
porta de entrada. Campo novo que não termina em revisão nem em pergunta para a próxima aula
(parceiro, rounds, placar) é decisão da D1, não ajuste de tela

---

[Índice das decisões](../README.md)
