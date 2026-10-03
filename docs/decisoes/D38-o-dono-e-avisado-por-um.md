# D38 — O dono é avisado por um resumo horário da fila, das 10h às 22h — não a cada pedido

## Justificativa

A D37 fechou a porta certa e deixou um silêncio atrás dela: o pedido entrava na fila e **nada
avisava quem decide**. A tela chegava a admitir isso, mandando a pessoa "avisar pelo mesmo canal por
onde chegou até aqui" — o produto terceirizava para fora dele a única notificação que fazia o portão
andar. Na prática a fila só andava quando o autor lembrava de abrir `/solicitacoes`, e um pedido
podia ficar dias parado sem ninguém saber que existia.

**O atrito não era o portão, era o silêncio em volta dele** — daí a correção ser um aviso, e não
abrir o cadastro.

**Resumo em janela, e não e-mail por pedido**: um disparo por clique resolveria o esquecimento e
traria junto um canal que escreve no horário que quiser, inclusive de madrugada, para uma decisão
que nunca é urgente. Treze janelas por dia (10h..22h, horário de Brasília) põem o atraso máximo em
uma hora dentro do dia e nada fora dele; pedido das 22h30 entra no resumo das 10h.

**Só sai quando há novidade, e mostra a fila inteira.** As duas metades importam por motivos
opostos: sem a guarda de novidade, uma fila parada geraria treze e-mails idênticos por dia até
alguém decidir — o oposto do problema; e listar só o delta faria um pedido não decidido nunca mais
ser mencionado, que é o esquecimento de novo, só adiado.

**A marca de "já anunciado" mora na linha do pedido** (`app_user.queue_notice_sent_at`), não num
relógio global de última execução: assim ela sobrevive a reinício e a deploy sem depender de o
agendador ter rodado, e as pendentes que já existirem no dia do deploy nascem não anunciadas —
entram no primeiro resumo, que é o certo para uma fila que ninguém tinha visto.

**E a marca só é gravada depois de pelo menos um envio ter passado**: provedor fora do ar deixa tudo
como estava e a janela seguinte tenta de novo, porque marcar antes de entregar perderia o pedido em
silêncio.

**Ler, enviar e marcar são três passos, e o envio fica fora de transação**: chamada de rede dentro
de transação segura a conexão do banco pelo tempo que o outro lado quiser, e o outro lado aqui é API
externa rodando num agendador de uma thread só. Pelo mesmo motivo o `RestClient` do envio ganhou
timeout — o default é esperar para sempre, e uma conexão pendurada apagaria em silêncio todas as
janelas seguintes do dia, que é justamente o defeito que este resumo existe para não ter.

**A regra da D37 continua valendo onde ela importa**: o destinatário vem de `fos.auth.owner-emails`,
nunca do formulário, então `/solicitar` segue sem servir para escrever a endereço arbitrário — que é
a razão inteira da regra —, e quem pediu continua sem receber nada até a aprovação. Dois detalhes
que só apareceram implementando: o `@EnableScheduling` ficou **condicionado à credencial de envio**,
senão o cron rodaria em dev e em CI, onde não existe o que enviar; e o resumo precisou de
`fos.public-url`, porque e-mail disparado por agendador não tem requisição de onde deduzir o
endereço do app — sem a variável ele sai sem link, e nada mais muda.

**Uma instância só**: `@Scheduled` dispara em cada processo, e com réplica o dono receberia em
duplicata; o deploy é de instância única (D22) e isso basta.

## Revisar quando

Se o app deixar de ser de instância única — aí o resumo precisa de trava no banco, não de ser
desligado. Ou se a janela virar incômodo: volume alto de pedidos pede resumo diário, e vontade de
decidir sem abrir o app pede aprovar pelo próprio e-mail, que exige token de ação com validade e uso
único — outra decisão

---

[Índice das decisões](../README.md)
