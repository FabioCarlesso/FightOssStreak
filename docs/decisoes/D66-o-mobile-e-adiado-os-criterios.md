# D66 — O mobile é adiado: os critérios do MVP web não foram atingidos

## Justificativa

A D4 deixou o app nativo para depois, com gatilho escrito: **os critérios de sucesso do MVP
atingidos** (`docs/produto/mvp-web.md`). O épico mobile (#137 a #144, Expo como já previa a D2)
começou pela conferência desse gatilho, e ele não disparou. Leitura de `/progresso` em produção,
janela de 2026-09-04 a 2026-10-03:

| Critério | Medido | Meta |
|---|---|---|
| Dias com registro de drill | 5 de 30 | ≥ 12 |
| Revisões atendidas via SRS | 36% (8 de 22) | ≥ 60% |
| Nós concluídos | 0 | ≥ 15 |
| Quiz refeito | 0 | ≥ 1 |

Nenhum dos quatro. "Nós concluídos" conta só a conclusão **dentro** da janela
(`MvpMetricsService`), então o zero diz que o currículo não avançou no mês, não que nunca avançou.

**O argumento de que o app nativo consertaria isso foi considerado e não se sustenta.** A hipótese
seria que os 5 de 30 medem o atrito de registrar, e não o hábito. Mas o celular já é o alvo
principal da web desde a D26, e o diário (D56) foi feito justamente para tirar atrito do registro.
O que o app traria de novo é notificação, e notificação sobre uma agenda que hoje é atendida em 36%
das vezes empurra para o critério de falha — o app aberto por obrigação, não porque a revisão muda
o que se treina. Seria investir em distribuição antes de saber se há o que distribuir.

**O que fica parado:** #138 a #144 continuam abertas e bloqueadas por esta decisão. Nada do que
elas pedem é desfeito: a análise de autenticação mobile, a stack e o caminho de publicação seguem
válidos para quando o gatilho disparar. A decisão de autenticação mobile, quando vier, leva o
próximo número livre.

**O que não muda:** a D4 continua valendo como está — web primeiro —, e o critério de falha do
`mvp-web.md` continua sendo a leitura certa destes números. Se a próxima janela repetir o quadro,
a pergunta deixa de ser "quando o mobile" e passa a ser a mecânica: é isso que o critério manda
repensar antes de qualquer plataforma nova.

## Revisar quando

Quando uma janela de 30 dias em `/progresso` mostrar os quatro critérios atingidos. Aí esta
decisão cai, a #137 é refeita com os números novos e o épico segue a partir da #138. Números abaixo da
meta não reabrem esta decisão: reabrem a mecânica, pelo critério de falha do `mvp-web.md`

Revisitada na [D67](D67-o-mobile-comeca-aprender-a-plataforma.md): o mobile começa por aprendizado, sem
que os critérios mudem.

---

[Índice das decisões](../README.md)
