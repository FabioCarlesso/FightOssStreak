# Coleta de uso do app (D50)

O projeto não sabia se alguém usa o app. Com o cadastro aberto, "quantas pessoas chegaram esta
semana", "de qual link vieram" e "isso está sendo aberto no celular ou no desktop" deixaram de ser
curiosidade e viraram a única forma de saber se a abertura funcionou. Esta seção existe porque a
resposta custou uma promessa: a frase "nem analytics" que estava no [quadro geral](README.md) **caiu**.

O desenho inteiro existe para alterá-la o mínimo possível. **Nada sai do banco do projeto, nenhum
script de terceiro entra na página, nenhum cookie de rastreio é criado, e nenhum endereço de IP é
gravado em lugar nenhum.**

## O que é coletado

| Dado | De onde vem | Por que existe |
|---|---|---|
| Caminho da rota, normalizado contra a lista de rotas do app | do navegador, a cada mudança de tela | é o acesso: responde quantas telas são abertas e quais |
| Host de onde você veio, e `utm_source`/`utm_medium`/`utm_campaign` quando houver | do navegador | responde de qual link a pessoa chegou |
| Celular, tablet ou desktop; família do navegador; família do sistema | **derivado** do `User-Agent` da requisição | responde em que tipo de aparelho o app é usado |
| Idioma | **derivado** do `Accept-Language` | mesma pergunta, para texto |
| País e região | **derivados do IP, que não é guardado** (ver abaixo) | responde de onde as pessoas chegam |
| Chave de visita | hash de (IP + `User-Agent` + **sal do dia**) | separa "100 acessos de uma pessoa" de "100 pessoas" — e nada mais |
| Id da conta, **só quando há sessão** | do próprio app | é o que faz `DELETE /api/me` alcançar estes registros |
| Cinco degraus de funil: demonstração aberta, cadastro criado, e-mail confirmado, primeiro drill, retorno em 7 dias | do **backend**, no ponto em que o fato acontece | responde "a abertura funcionou?" — vindos do navegador seriam forjáveis |

## A chave de visita não identifica ninguém, e isso é verificável

Para contar pessoas em vez de cliques é preciso algum agrupamento. O jeito comum é um cookie de
visitante, e ele traz junto o que este projeto não quer: identificador estável, banner de
consentimento e a possibilidade de reconstruir a navegação de alguém ao longo de meses.

Aqui a chave é `hash(sal do dia + IP + User-Agent)`, e **o sal é sorteado por dia e nunca é
gravado**. Três consequências, as três de propósito:

- O hash **não é reversível**, nem por quem tenha o banco inteiro.
- A mesma pessoa em dois dias diferentes produz chaves **diferentes**: não há como ligar sua visita
  de ontem com a de hoje.
- **Reiniciar a aplicação** sorteia outro sal, então nem o próprio app consegue recomputar a chave
  de um evento passado.

É por isso que o app continua **sem precisar de banner de consentimento**. Isso é resultado do
desenho, não sorte, e é a razão de o preço estar pago por escrito: o dado é mais grosso, não há
sessão entre dias, e não há funil por pessoa.

## O IP é usado e descartado

O endereço de IP é lido na requisição, serve para **derivar país e região** e para **compor a chave
de visita**, e é descartado ali mesmo. Ele vem de onde a **infraestrutura** diz — o proxy do próprio
app escreve a origem da conexão, e o backend conta a partir do salto mais próximo dele —, e não do
header que quem faz a requisição pode escrever. Isso importa aqui por dois motivos: um endereço
escolhido por quem chama poria um país inventado na estatística, e faria a chave de visita mudar a
cada requisição, transformando uma pessoa em cem. **Não existe coluna de IP em tabela nenhuma, e ele não vai
para log de aplicação.** Há teste automatizado que varre o schema migrado e o texto de todas as
migrations e reprova o build se uma coluna com cara de endereço aparecer — hoje ou daqui a dois
anos.

A geolocalização usa uma **base local**, baixada quando a imagem do backend é construída
([DB-IP Lite](https://db-ip.com), CC BY 4.0). **Não há chamada a serviço externo por requisição**: a
única conversa com o db-ip.com acontece na máquina que constrói a imagem, e o IP de quem navega
nunca sai daqui — consultar terceiro a cada acesso colocaria esse IP na mão de outra empresa, que é
exatamente o que este documento promete que não acontece.

Ambiente sem base — o caso de desenvolvimento e do CI, e também o de um build em que o db-ip.com
estivesse fora do ar — coleta tudo **menos** país, que fica como desconhecido. A base gratuita traz
só país: **região é sempre desconhecida** com ela.

## O que NÃO é coletado

- **Nada de conteúdo.** Anotação fixada, nota de drill e resposta de quiz não entram em evento
  nenhum.
- **Nenhum segmento variável de URL.** O caminho é normalizado contra a lista de rotas conhecidas
  antes de virar linha: `/confirmar-email/<token>` é gravado como `/confirmar-email/{token}`, e
  rota desconhecida vira `/outro`. Token de confirmação e de redefinição **nunca** entram na
  tabela.
- **Query string**, fora os três `utm_*`.
- **Nenhuma impressão digital**: sem canvas, sem lista de fontes, sem resolução de tela, sem
  qualquer sinal além dos da tabela acima.
- **Nenhum cookie novo.** A coleta não cria cookie de visitante nem abre sessão para quem não tem.

## Por quanto tempo

Duas tabelas, duas vidas:

- **`usage_event`** é a linha crua, com chave de visita e às vezes id de conta. Vive **90 dias**, e
  um job diário apaga o que passar disso.
- **`usage_daily`** é a contagem por dia × dimensão. Não tem chave de visita, id de conta nem nada
  que aponte para alguém — e por isso **fica**.

**`DELETE /api/me` apaga os eventos crus da conta**, na mesma transação do resto. O agregado
permanece, e é de propósito: sem identificação nele, apagá-lo faria a exclusão de **uma** conta
reescrever o histórico de uso de todo mundo.

## Quanto é gravado, no máximo

A coleta tem dois limites, e vale saber que eles existem por motivos diferentes.

O primeiro é **por visita**: acima de 300 acessos em dez minutos, a mesma chave de visita para de
ser gravada. É folgado porque navegar rápido pelo app não pode virar evento perdido.

O segundo é um **teto por dia** (`FOS_USAGE_DAILY_CAP`, 5 000 por padrão), e ele existe porque o
primeiro não basta: a chave de visita é derivada do IP e do `User-Agent`. O IP deixou de ser
escolhido por quem faz a requisição com a
[#77](https://github.com/FabioCarlesso/FightOssStreak/issues/77) — ele passou a vir de onde a
infraestrutura diz, e não de um header que qualquer um escreve —, mas o `User-Agent` é do cliente
por definição: quem o variar tem uma chave nova a cada requisição e passa pelo freio por visita
inteiro. O teto do dia não pergunta de quem veio o acesso — conta quantos foram gravados e para.

**O que isso significa para quem lê o número**: um dia que bate no teto tem contagem incompleta, e
sai um aviso no log dizendo qual dia foi. O teto é para a tabela parar de crescer se alguém apontar
um laço para o endpoint; ele não impede o abuso, limita o estrago.

O número tem uma conta atrás dele: **uma linha custa 273 bytes medidos** (tabela mais os três
índices), então 5 000 por dia × 90 dias de retenção ≈ 123 MB no pior caso. E o que se ocupa em
disco é a **marca d'água**, não a contagem de hoje — o expurgo apaga as linhas, mas o Postgres só
devolve o espaço ao sistema com `VACUUM FULL`. Um único dia de abuso fixa disco que os 90 dias não
recuperam sozinhos.

## Quem vê isso, e o que essa pessoa vê (D50, #85)

A coleta existe para ser lida, e quem a lê é a administração do app, na tela *Painel*
(`/admin/painel`, atrás de `GET /api/admin/painel`). Vale a pena ser explícito sobre o que essa
tela **é**, porque é ela que poderia desfazer na prática o cuidado descrito acima.

**O painel é agregado e de ninguém.** Ele lê `usage_daily` — a contagem por dia × dimensão — e
**nunca** `usage_event`, que é a tabela com chave de visita e às vezes id de conta. Nenhuma resposta
dele carrega e-mail, nome ou id de conta; há teste que varre o corpo inteiro da resposta e reprova o
build se uma arroba aparecer nele.

O que ele mostra: quantos acessos e quantos visitantes por dia, com o comparativo do período
anterior; os seis degraus do funil, com a conversão de cada um; de onde as pessoas vieram; com que
dispositivo, navegador, idioma e país; quais telas foram abertas; e três números sobre as contas
(total, criadas no período, ativas no período).

O que ele **não** mostra, e não por falta de tela: lista de pessoas, sessão individual, "últimos
acessos de fulano", ou qualquer recorte que ligue um comportamento a uma conta. Duas coisas que
saem de fora da coleta — os totais de contas e "quantas registraram drill no período" — devolvem
**número**, nunca linha.

Duas leituras que a tela precisa declarar, e declara:

- **Visitante é contado por dia.** A soma do período é a soma dos dias, e não pessoas distintas no
  mês: o sal da chave de visita roda por dia, e ligar a mesma pessoa entre dois dias é justamente o
  que esta coleta não faz.
- **O período termina ontem.** O dia corrente ainda recebe evento e não é fechado — um número
  publicado que muda depois de lido seria pior que um número que falta.

Se um dia o painel precisar de uma visão por pessoa, isso não é ajuste de tela: é a D50 sendo
revertida, e passa por reescrever este documento antes de escrever a consulta.

## Como desligar

Quem sobe este código e não quer coleta nenhuma define `FOS_USAGE_ENABLED=false`. Nenhum evento é
gravado, o endpoint passa a responder **503 `coleta_desligada`**, e o navegador **para de mandar**
ao ver esse código — desligado significa desligado, e não "grava nada mas continua sendo chamado a
cada navegação". O resto do app funciona igual.

O job diário continua rodando mesmo com a coleta desligada, e é de propósito: desligar a coleta não
pode deixar o que já foi gravado sem expurgo. Para parar só o job, `FOS_USAGE_CRON=-`.
