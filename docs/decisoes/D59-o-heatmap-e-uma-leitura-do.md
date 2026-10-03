# D59 — O heatmap é uma leitura do mesmo conjunto de dias do streak, e não uma segunda contagem

## Justificativa

O contador responde uma pergunta só — "a corrente está viva?" — e quem quebrou e retomou não tem
nada a ver nele, justamente a pessoa que precisa ver que voltou. O heatmap responde a outra: "como
foram os últimos meses?". O risco real não era desenhar a grade, era **de onde ela lê**: um "dia
ativo" próprio acenderia dias que a corrente ignora, e o app afirmaria duas coisas diferentes sobre
o mesmo dia na mesma tela — a D58 outra vez, por outra porta. Por isso o insumo é literalmente o
mesmo (sessões que não são `DESCANSO` mais drills avulsos), agregado por dia no banco, e dia
perdoado por freeze é **marcado** sem entrar na escala de intensidade, porque dia coberto não é dia
de treino (D55). Rota própria (`GET /api/streak/historico`) e não campo novo no `StreakView`: aquele
DTO viaja dentro de todo `DrillResult`, e seis meses de datas ali engordariam cada registro de
treino por causa de uma tela só. A tela pede o histórico **depois** do streak, porque é o streak que
materializa o dia perdoado em `streak_freeze` — em paralelo, o primeiro carregamento depois de um
dia perdido mostraria o cartão dizendo "um freeze cobriu quinta" com a grade marcando quinta como
falta. Fica **agregado por dia**: heatmap por nó foi recusado na própria issue, e detalhar por
técnica transformaria a tela inicial num relatório

## Revisar quando

Se o heatmap virar a primeira coisa que se olha e a agenda parar de ser aberta, é o critério de
falha do `docs/produto/mvp-web.md` acontecendo — gamificação se sustentando sozinha. E se alguém pedir "dias em que abri
o app" na grade, é a D58 sendo revertida: o conjunto de dias é um só

---

[Índice das decisões](../README.md)
