# D55 — O streak perdoa até dois dias por mês, e o perdão é livro-caixa e não cache

## Justificativa

Qualquer dia sem revisão zerava a sequência, e isso punia viagem, lesão e semana corrida — a
mecânica que existe para reforçar hábito virava o motivo de desistir depois da primeira falha. Entra
o freeze: um dia perdido consome um freeze do mês **daquele dia** e a corrente segue; sem saldo, o
comportamento é o de antes e a corrente para ali. Quatro escolhas carregam o desenho.

**(a) Mês de calendário, não janela deslizante de 30 dias** — a segunda é mais justa na média e
impossível de explicar em uma linha na tela, que é onde a informação precisa caber.

**(b) O consumo é gravado (`streak_freeze`), e a tabela é livro-caixa, não cache**: o streak segue
derivado do `drill_log` a cada leitura, mas um freeze gasto **continua gasto** depois que a corrente
que ele salvava quebrou. Sem a tabela o saldo seria "por corrente" e não "por mês", e bastaria
deixar a sequência morrer para ganhar freeze novo.

**(c) Buraco só é cobrado quando a caminhada alcança outro dia de treino do outro lado dele** — a
corrente que morre por falta de saldo não pode levar junto os freezes que gastou tentando
sobreviver, senão a pessoa perde a sequência **e** o saldo do mês na mesma virada.

**(d) Não há job diário**: o perdão é derivado na leitura seguinte do streak, que é quando alguém
tem o que ver, e a gravação é idempotente pela chave `(user_id, covered_on)` — é o que permite `GET
/api/streak` recalcular em toda abertura da home sem queimar saldo. Por isso ele escreve, e não é
`readOnly` — e um GET que escreve tem corrida: **duas abas abertas bastavam para a que perdesse
morrer na `uq_streak_freeze` com 500**, medido em 25 leituras simultâneas. Pior que tela feia, o 5xx
era da própria aplicação e entrava na taxa que dispara o alerta da D54: duas abas podiam mandar
e-mail de "site fora do ar". Fechar isso levou **duas** peças, e nenhuma sozinha bastou — o `WHERE
NOT EXISTS` derrubou de ~8% para ~2% das requisições e não chegou a zero (no `READ COMMITTED` a
subconsulta não vê a inserção não confirmada do vizinho), e a violação **capturada** ainda
estourava, porque a tradução já marca a transação como `rollback-only` e era o *commit* que falhava.
Quem resolve é o `StreakFreezeWriter`: `TransactionTemplate` com `REQUIRES_NEW` e desfazimento
explícito, para a restrição ser rede e não mina.

**(e) Dia perdoado que ganha registro depois devolve o freeze** — `drilledOn` existe justamente para
registrar o treino de ontem sem falsear a data, e sem a devolução abrir a home de manhã e só então
lembrar de registrar o treino de ontem custaria saldo. O que a tabela guarda é "dia **sem** treino
que foi perdoado", então um dia com treino deixou de pertencer a ela. Isso não afrouxa o (b): o que
não volta é o freeze de um dia que continua sem treino.

**Hoje nunca gasta freeze**: o dia não acabou, e é a mesma razão que faz o streak ancorar em ontem.
Dia coberto mantém a corrente e **não** conta como dia de treino — o número na tela continua
significando "dias em que treinei". Regra pura em `shared/domain`, espelhada no backend (D17). Ficou
de fora, e é escopo e não esquecimento: **freeze manual** ("usar agora") e **compra de freeze** por
qualquer moeda — não existe economia de pontos no FOS, e criar uma para isto seria a gamificação se
sustentando sozinha, o critério de falha do `docs/produto/mvp-web.md`

## Revisar quando

`fos.streak.freezes-per-month` é a saída rápida: **0 devolve o comportamento anterior à #99** sem
deploy de código. Se o critério de sucesso do MVP (≥ 12 dias em 30) subir enquanto o streak médio
sobe mais rápido, o freeze está inflando o número em vez de segurar gente — aí o teto desce. Se
aparecer pedido de freeze manual, revisitar: a diferença é quem escolhe qual dia é perdoado

---

[Índice das decisões](../README.md)
