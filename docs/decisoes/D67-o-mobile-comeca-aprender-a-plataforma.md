# D67 — O mobile começa: aprender a plataforma é objetivo próprio, e a D66 cai

## Justificativa

A D4 e a D66 partem do mesmo pressuposto: o app nativo serve para **distribuir** um produto já
validado, e por isso só vale o custo quando os critérios do MVP web forem atingidos. Com os quatro
abaixo da meta (D66), o mobile ficou adiado.

O pressuposto não descreve o projeto inteiro. O FOS é de uso pessoal, e desenvolver e publicar
para Android e iOS — o que o autor nunca fez — é um objetivo por si só, que não depende das
métricas de retenção. Esperar o gatilho da D66 adiaria esse aprendizado por uma condição que mede
outra coisa. O épico mobile (#138 a #144) segue a partir da #138.

**O que esta decisão não muda:**

- **Os critérios do MVP e o critério de falha continuam valendo como estão.** Os números da D66
  seguem sendo a leitura de que a mecânica ainda não provou retenção, e começar o mobile não os
  torna verdade. O app não vira argumento para afrouxar meta.
- **O mobile não pode ser a resposta à métrica.** A notificação da #142 é sobre revisão vencida,
  nunca sobre o streak: com 36% das revisões atendidas, lembrar "não perca a sequência" seria o
  critério de falha construído de propósito.
- **A web segue sendo o produto.** Nenhuma regra de negócio nasce no app (D17): domínio puro em
  `shared/domain`, verdade no backend, e o app só reaproveita.

**O custo assumido:** tempo investido em plataforma antes de a mecânica estar validada. Se ela
falhar, parte do que é específico do app (telas, notificação, fichas das lojas) vai junto. O que
fica é o aprendizado, que é justamente o motivo desta decisão, e o trabalho de backend, que serve
a qualquer cliente.

## Revisar quando

Se o trabalho no mobile passar a tirar tempo do uso e da curadoria que movem os critérios do MVP,
ou se a próxima janela de 30 dias em `/progresso` mostrar o critério de falha do `mvp-web.md`. Aí a
pergunta volta a ser a mecânica, e o épico mobile pausa de novo

---

[Índice das decisões](../README.md)
