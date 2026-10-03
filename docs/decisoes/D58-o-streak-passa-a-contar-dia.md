# D58 — O streak passa a contar dia com registro, e não dia com drill — revisita a D55 sem derrubá-la

## Justificativa

Com a D56 a rotina real é escrever todo dia e vincular técnica às vezes, e um streak alimentado só
pelo `drill_log` mostraria corrente morta para quem treinou seis dias na semana: o app mentindo
sobre a rotina de quem usa, que é o oposto do que a D55 foi consertar. O conjunto de datas do
cálculo passa a ser **sessões mais os drills avulsos**; o cálculo puro em `shared/domain`, o teto
por mês e o livro-caixa `streak_freeze` ficam idênticos — **uma corrente, um livro-caixa**, sem
segunda regra e sem segunda tabela, porque duas correntes disputando o mesmo saldo criariam a
pergunta "qual delas gastou o freeze" sem resposta possível. O tipo `DESCANSO` é o que impede o
abuso trivial: dia anotado sem treino fica no diário e **não** conta como dia de treino. O risco de
o streak virar "escrevi hoje" é real e **não é contido pelo streak** — quem o contém é o critério
que já existe em `docs/produto/mvp-web.md` (revisões atendidas ≥ 60%) e a agenda no topo da home. Continua uma
corrente só na tela, e ela significa "dias em que registrei treino"

## Revisar quando

Se o streak médio subir enquanto as revisões atendidas caem, o número virou presença e não retenção:
ou volta a contar só `drill_log`, ou a agenda passa a ser o indicador principal da home

---

[Índice das decisões](../README.md)
