# D50 — Analytics próprio, sem cookie de rastreio, sem terceiro e sem guardar IP — e a promessa de `docs/privacidade/README.md` reescrita em vez de contornada

## Justificativa

O projeto não sabia se alguém usa o app. Havia `GET /api/metrics/mvp`, mas ele mede os quatro
critérios do MVP **para uma conta** e nasceu quando o usuário era um só; nada respondia "quantas
pessoas chegaram esta semana", "de qual link vieram" ou "isso está sendo aberto no celular". Com o
cadastro aberto (D47), essas perguntas deixaram de ser curiosidade e viraram a única forma de saber
se a abertura funcionou.

**O que esta decisão custa está escrito primeiro, porque é o que dói**: `docs/privacidade/README.md`
afirmava, com todas as letras, que *"não há rastreador de terceiros, nem analytics"*. Era uma
promessa feita a quem usa o app, e esta entrada a altera. Todo o desenho abaixo existe para
alterá-la o **mínimo** possível.

**Próprio, e não Plausible auto-hospedado nem SaaS.** SaaS está fora por definição: seria mandar o
IP e a navegação de quem usa o app para outra empresa, que é exatamente a frase que o documento
prometia. Plausible auto-hospedado não tem esse defeito e ainda assim ficou de fora por dois motivos
concretos: é um serviço a mais para subir, migrar e manter em um projeto que hoje roda com uma
réplica e um Postgres (D22), e — o que decidiu — os **eventos de funil precisam vir do backend**, do
ponto onde o cadastro é criado e o e-mail é confirmado. Nenhum analytics externo enxerga isso sem
que o backend fale com ele, e nesse momento a economia de escrever a coleta própria some. O que se
ganha em troca: o dado é do banco do projeto, e o painel (fatia 2) é uma tela do app, não um login
em outro lugar.

**Sal diário em vez de cookie.** Para separar "100 acessos de uma pessoa" de "100 pessoas" é preciso
algum agrupamento. Cookie de visitante é o jeito comum e traz junto o que o projeto não quer:
identificador estável, banner de consentimento e a possibilidade de reconstruir a navegação de
alguém ao longo de meses. A chave é `hash(sal do dia + IP + User-Agent)`, com o sal sorteado por dia
e **nunca persistido**. Consequências, todas deliberadas: o hash não é reversível nem por quem tenha
o banco inteiro; a mesma pessoa em dois dias diferentes **não é ligável**; e reiniciar a aplicação
impede que ela recompute a chave de um evento passado. É por isso que o app segue **sem precisar de
banner de consentimento** — resultado do desenho, não sorte.

**País derivado, IP descartado.** O IP entra em dois lugares — derivar país/região e compor a chave
— e morre no mesmo método. Não existe coluna de IP, não vai para log, e há teste que varre o schema
migrado **e o texto de todas as migrations** e reprova o build se uma coluna com cara de endereço
aparecer, hoje ou daqui a dois anos. A base de geolocalização é um **arquivo local** (DB-IP Lite, CC
BY 4.0), **baixado no build da imagem** (`backend/Dockerfile`): consulta a serviço externo por
requisição seria mandar o IP para fora, que é o que se está evitando — no build quem fala com o
db-ip.com é a máquina que constrói a imagem, e nenhum IP de quem navega sai daqui. Versionar o
arquivo foi descartado (alguns MB de dado de terceiro entrando no histórico do git a cada
atualização mensal) e volume também (custo e upload manual num projeto de uma réplica). O download
**nunca derruba o build**: terceiro fora do ar vira base ausente, que já é o caso normal de dev e
CI. O crédito exigido pela CC BY 4.0 está no Dockerfile, no README e em `docs/privacidade/README.md`, e
precisa aparecer no painel da #85 quando a tela existir. Base ausente é o caso normal — dev e CI
sobem sem ela e coletam tudo menos país, que vira `ZZ` e é **categoria própria, não erro**.

**Cru com retenção curta + agregado permanente.** `usage_event` guarda a linha com chave de visita e
às vezes `user_id`, e vive 90 dias; `usage_daily` guarda contagem por dia × dimensão, não tem nada
que aponte para pessoa nenhuma, e fica. Sem o agregado o painel varreria a tabela grande a cada
abertura, e o expurgo apagaria o histórico junto. `DELETE /api/me` leva os eventos crus da conta na
mesma transação do resto; o agregado permanece, porque apagá-lo faria a exclusão de **uma** conta
reescrever o histórico de uso de todo mundo.

**O cliente não decide nada que dê para derivar.** O web manda quatro coisas que só o navegador sabe
(rota, host do referrer, os três `utm_*`) e nada mais; dispositivo, navegador, sistema, idioma e
país saem da requisição, e os quatro eventos de funil são emitidos pelo backend — vindos do cliente,
"a abertura funcionou?" seria respondida por um número que qualquer um pode inflar. O caminho é
normalizado contra a lista de rotas conhecidas, e isso é guarda de privacidade antes de arrumação:
`/confirmar-email/<token>` cru colocaria credencial de uso único dentro de uma tabela de métrica.

**Detalhes que só apareceram implementando**: o `@EnableScheduling` **não** pôde herdar a condição
de credencial de e-mail da D38 — ambiente sem provedor de envio (dev, CI, instalação só com login
social) nunca agregaria nem expurgaria nada, com a tabela crua crescendo em silêncio; e a coleta é a
**única** escrita fora do CSRF, porque a primeira coisa que o app faz é registrar o acesso à
landing, antes de qualquer resposta ter deixado o cookie de token — o primeiro evento de toda visita
morreria em 403, e não há estado de conta a proteger num endpoint que já aceita requisição sem
sessão de qualquer origem.

**O que se aceita**: dado mais grosso, sem sessão entre dias, sem funil por pessoa. Quem quiser
qualquer uma das três coisas reabre esta decisão.

**O que fica de fora**: painel do admin (fatia 2), saúde técnica e alerta de queda (fatia 3),
histórico de login por conta, e qualquer identificador estável de visitante entre dias. E uma
dependência conhecida: enquanto a #77 não corrigir a origem do IP, o `X-Forwarded-For` que o cliente
manda pode forjar país e chave de visita — por isso a leitura do IP virou ponto único (`ClientIp`),
para que o conserto seja em um lugar só.

**A consequência que só apareceu na revisão da PR**: o freio da coleta é chaveado nessa mesma chave
forjável, então ele não segura quem sabe disso — medido em uma instância de verdade, 400 requisições
do mesmo IP com `User-Agent` rodando gravaram as 400. Um freio "por IP bruto" não resolveria, porque
o IP bruto é justamente o que a #77 deixa forjável. Por isso entrou um **teto diário**
(`fos.usage.daily-cap`, 5 000 — uma linha custa 273 bytes medidos, então 5 000 × 90 dias de retenção
≈ 123 MB no pior caso, e o que se ocupa é a marca d'água, já que o expurgo não devolve disco sem
`VACUUM FULL`) que não depende de chave nenhuma: ele não filtra abuso — quem abusa gasta o orçamento
do dia e a coleta legítima para junto —, é teto de estrago para a tabela não crescer sem limite, e
avisa no log qual dia bateu nele. O conserto continua sendo a #77.

## Revisar quando

Quando a coleta deixar de responder às perguntas que a motivaram — aí a pergunta nova define o que
muda, e provavelmente é dimensão nova em `usage_daily`, que é dado e não migration. Ou quando o
volume fizer o agrupamento em memória de um dia deixar de caber — aí o `UsageAggregator` vira SQL, e
o teste dele já existe para dizer se o SQL novo conta o mesmo. Ou se alguma pergunta exigir ligar
uma pessoa entre dois dias: essa é a decisão inteira sendo revertida, e precisa passar por
reescrever `docs/privacidade/README.md` de novo — nunca por um cookie acrescentado em silêncio

---

[Índice das decisões](../README.md)
