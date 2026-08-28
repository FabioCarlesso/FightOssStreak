# Privacidade e dados pessoais

> **Nota:** redigido por não-advogado, como os textos de `06-disclaimer-responsabilidade.md`. Antes
> de publicar o app em loja ou de abri-lo além do círculo pessoal, revisar com profissional.

Até a #24 o app não tinha o que documentar aqui: sem login (D9), não havia dado pessoal — havia um
usuário único e o progresso dele. Com login (D36) o app passou a guardar dado de pessoas, e com o
cadastro aberto (D47) passou a guardar também **hash de senha**. Este documento diz o quê, por quê e
por quanto tempo.

## O que é coletado

| Dado | De onde vem | Por que existe |
|---|---|---|
| Provedor e identificador da conta no provedor (`sub`/`id`) | do provedor, no login | é a identidade: é o par que diz de quem é o progresso |
| Nome de exibição | do provedor, no primeiro login | identificar a conta na tela |
| E-mail | de você, no cadastro; ou do provedor, no primeiro login | identifica a conta, é o que vincula Google e senha no mesmo endereço, e é o que quem administra lê para reconhecer uma conta |
| Data do primeiro e do último login | do próprio app | saber se a conta ainda é usada |
| Progresso, streak, agenda de revisão, drills e anotações | do uso do app | é o produto |
| Aceite do aviso de responsabilidade, com data e versão | do uso do app | requisito de produto (`06-disclaimer-responsabilidade.md`) |
| **Hash da sua senha**, se você criou conta com e-mail e senha | de você, no cadastro | é o que confere a senha na entrada |
| Papel da conta (`role`), com data e id de quem o mudou | de quem administra, ou da semente `fos.auth.owner-emails` na subida | decide quem vê a administração do app (D49) |
| Estado de acesso, com data, id de quem decidiu e **o motivo escrito** | de quem administra, ao bloquear ou desbloquear | é a trilha de uma decisão sobre uma conta — bloqueio sem registro de quem e por quê é pior que bloqueio nenhum (D49) |

**A senha não é guardada — o hash dela é.** Até a D47 o app não via credencial nenhuma: a autenticação acontecia no provedor, e esta seção dizia isso. Com o cadastro aberto (#81) passou a existir uma senha, e o que fica no banco é só o hash (bcrypt, com o prefixo do algoritmo), numa tabela separada da identidade. Do valor que você digita não sobra nada depois da requisição — nem em log, nem em resposta de API. **Quem entra pelo Google continua sem ter senha aqui**, e nada muda para essa pessoa.

**O que a confirmação de e-mail guarda.** Os links de confirmação e de redefinição também vivem só como hash, valem uma vez (24 horas e 1 hora, respectivamente) e são queimados quando a senha muda.

**Nada é vendido, compartilhado ou usado para publicidade.** Não há rastreador de terceiros: nenhum
script de outra empresa entra na página, e nenhum dado sai do banco do projeto. Os números da tela
`/progresso` são calculados sobre esse banco.

**Desde a D50 existe coleta de uso — e ela tem seção própria mais abaixo.** Até essa decisão este
documento dizia "nem analytics", e a frase caiu junto com ela. O que existe agora é medição de
acesso feita pelo próprio app: sem cookie de rastreio, sem terceiro e **sem guardar endereço de
IP**. O quadro completo, incluindo o que **não** é coletado, está em
[Coleta de uso do app (D50)](#coleta-de-uso-do-app-d50).

O e-mail pode não existir: o Facebook pode não devolver e-mail nenhum, e a Apple entrega um endereço
de relay quando a pessoa escolhe esconder o dela. O app funciona igual — e-mail não é identidade.

## Quem se cadastrou e nunca confirmou

Um cadastro não confirmado guarda o mesmo do quadro acima — endereço, hash da senha e as datas — e
nada mais: sem confirmar o e-mail não há sessão, e sem sessão não há progresso, agenda nem drill.

Não há expurgo automático desses cadastros hoje. É uma pendência conhecida, registrada no critério de
revisão da D47: se a quantidade de conta criada e nunca confirmada virar problema, entra uma limpeza
periódica. Enquanto isso, `DELETE /api/me` continua disponível para quem confirmar e quiser sumir.

## Como apagar tudo

Em *Sua conta* → **Excluir minha conta**, ou `DELETE /api/me`. Apaga, em uma transação, a conta, a
identidade externa, o hash da senha, os links pendentes, progresso, agenda de revisão, drills,
anotações, tentativas de quiz e o aceite do aviso. Não há cópia lógica nem lixeira: o que sai, sai.

Ela entrou junto com o login, e não depois, porque a loja da Apple recusa app com login e sem
deleção (`02-publicacao-ios-desafios.md`) — e porque manter dado de quem nunca entrou seria
indefensável.

## Retenção

Enquanto a conta existir. Não há expurgo automático de conta inativa: o app é de uso pessoal e o
volume é pequeno demais para que apagar por inatividade proteja alguém — apagaria progresso de quem
passou dois meses lesionado, que é justamente quem mais precisa da revisão ao voltar.

## O que falta antes de publicar em loja

- Política de privacidade **pública**, fora do repositório, com contato do responsável.
- *Privacy labels* da App Store descrevendo a coleta acima.
- Revisão jurídica dos dois textos (este e o de responsabilidade).

## Cadastro com e-mail e senha (D47)

Quem se cadastra informa o próprio endereço, e ele é a chave da identidade — do mesmo jeito que
`(provider, subject)` é para quem entra por provedor.

Quatro notas sobre esse dado:

- O endereço nasce **não verificado**: até a pessoa abrir o link enviado para ele, ninguém provou
  ser dono daquela caixa. É por isso que digitar o e-mail que está em `fos.auth.owner-emails` não
  torna ninguém administrador, e que cadastrar o endereço de outra pessoa não dá acesso a nada.
- A **senha não é guardada** — o hash dela é, com o prefixo do algoritmo, numa tabela separada da
  identidade. Quem tiver leitura do banco não consegue entrar como ninguém.
- Os **links não são guardados**. O que fica na tabela é o hash do token, com validade e marca de
  uso. Vale para o de confirmação (24h) e o de redefinição (1h).
- **O endereço verificado vincula contas** (D47): se você já entrava pelo Google e se cadastra com o
  mesmo e-mail, é a mesma conta, com o mesmo progresso. Endereço não verificado nunca vincula nada.

Nada disso vai para log: o que se registra é o id da conta, nunca o endereço.

A exclusão de conta apaga a identidade, o hash da senha e os links pendentes, na mesma transação do
resto.

## O que quem administra vê, e o que fica registrado (D49)

Até aqui nenhuma tela do app mostrava dado pessoal de outra pessoa: cada conta via a si mesma, e a
administração só olhava fila de feedback e as próprias métricas. Com a tela **Usuários** isso muda, e
está escrito porque é a mudança que mais mexe com este documento.

**Quem tem papel `ADMIN` vê, de todas as contas do app** — não de uma, não sob pedido, mas da lista
inteira: o rótulo, o **e-mail primário** (ou, para quem se cadastrou e ainda não confirmou, o
endereço que a identidade trouxe), se esse e-mail é verificado, os **provedores vinculados** (Google,
Facebook, senha), o papel, o estado de acesso e a data de criação da conta. Também os campos da
última decisão sobre ela: quando foi e com que motivo. Há busca por trecho de e-mail e rótulo, o que
significa que procurar alguém pelo endereço é uma ação de um campo de texto.

Duas coisas que **não** estão ali, de propósito: nada de progresso, agenda, drill ou anotação de
outra pessoa — a administração enxerga a conta, não o uso dela —, e nenhuma forma de editar dado de
conta alheia. As únicas ações são papel e estado de acesso.

**O bloqueio deixa trilha.** Bloquear ou desbloquear grava, na própria linha da conta afetada,
`decided_at` (quando), `decided_by` (o id da conta que decidiu) e `decided_reason` (o motivo escrito
por quem decidiu, até 500 caracteres). Mudar o papel grava o par próprio, `role_changed_at` e
`role_changed_by`. É registro de uma decisão sobre uma pessoa, e é por isso que existe: bloqueio sem
quem e sem por quê não é auditável nem revisável.

**Por quanto tempo:** enquanto a conta existir. Os campos vivem na linha do `app_user`, então o
`DELETE /api/me` os apaga junto com o resto, na mesma transação — e **conta bloqueada continua
podendo se excluir**, de propósito: bloquear não pode virar sequestro de dado pessoal. O que
sobrevive é o `id` de quem decidiu, guardado como número na linha de **outra** conta; ele não tem
chave estrangeira justamente para que quem administrou possa excluir a própria conta sem que a
trilha alheia quebre — depois disso o número deixa de apontar para alguém.

**Conta de demonstração não aparece nessa lista e não aceita ação** (D39): ela não é de ninguém e
vence sozinha em duas horas.

## Coleta de uso do app (D50)

O projeto não sabia se alguém usa o app. Com o cadastro aberto, "quantas pessoas chegaram esta
semana", "de qual link vieram" e "isso está sendo aberto no celular ou no desktop" deixaram de ser
curiosidade e viraram a única forma de saber se a abertura funcionou. Esta seção existe porque a
resposta custou uma promessa: a frase "nem analytics" que estava acima **caiu**.

O desenho inteiro existe para alterá-la o mínimo possível. **Nada sai do banco do projeto, nenhum
script de terceiro entra na página, nenhum cookie de rastreio é criado, e nenhum endereço de IP é
gravado em lugar nenhum.**

### O que é coletado

| Dado | De onde vem | Por que existe |
|---|---|---|
| Caminho da rota, normalizado contra a lista de rotas do app | do navegador, a cada mudança de tela | é o acesso: responde quantas telas são abertas e quais |
| Host de onde você veio, e `utm_source`/`utm_medium`/`utm_campaign` quando houver | do navegador | responde de qual link a pessoa chegou |
| Celular, tablet ou desktop; família do navegador; família do sistema | **derivado** do `User-Agent` da requisição | responde em que tipo de aparelho o app é usado |
| Idioma | **derivado** do `Accept-Language` | mesma pergunta, para texto |
| País e região | **derivados do IP, que não é guardado** (ver abaixo) | responde de onde as pessoas chegam |
| Chave de visita | hash de (IP + `User-Agent` + **sal do dia**) | separa "100 acessos de uma pessoa" de "100 pessoas" — e nada mais |
| Id da conta, **só quando há sessão** | do próprio app | é o que faz `DELETE /api/me` alcançar estes registros |
| Quatro degraus de funil: demonstração aberta, cadastro criado, e-mail confirmado, primeiro drill | do **backend**, no ponto em que o fato acontece | responde "a abertura funcionou?" — vindos do navegador seriam forjáveis |

### A chave de visita não identifica ninguém, e isso é verificável

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

### O IP é usado e descartado

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

### O que NÃO é coletado

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

### Por quanto tempo

Duas tabelas, duas vidas:

- **`usage_event`** é a linha crua, com chave de visita e às vezes id de conta. Vive **90 dias**, e
  um job diário apaga o que passar disso.
- **`usage_daily`** é a contagem por dia × dimensão. Não tem chave de visita, id de conta nem nada
  que aponte para alguém — e por isso **fica**.

**`DELETE /api/me` apaga os eventos crus da conta**, na mesma transação do resto. O agregado
permanece, e é de propósito: sem identificação nele, apagá-lo faria a exclusão de **uma** conta
reescrever o histórico de uso de todo mundo.

### Quanto é gravado, no máximo

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

### Como desligar

Quem sobe este código e não quer coleta nenhuma define `FOS_USAGE_ENABLED=false`. Nenhum evento é
gravado, o endpoint passa a responder **503 `coleta_desligada`**, e o navegador **para de mandar**
ao ver esse código — desligado significa desligado, e não "grava nada mas continua sendo chamado a
cada navegação". O resto do app funciona igual.

O job diário continua rodando mesmo com a coleta desligada, e é de propósito: desligar a coleta não
pode deixar o que já foi gravado sem expurgo. Para parar só o job, `FOS_USAGE_CRON=-`.

## Conta de demonstração (D39)

O botão *Ver o app funcionando*, na página inicial, abre uma **conta temporária** — não pede
e-mail, não pede login, não pergunta nada. É a única forma de usar o app sem informar quem você é,
e é assim de propósito: ela existe para quem ainda está decidindo se quer uma conta.

O que essa conta guarda:

| Dado | De onde vem |
|---|---|
| Um identificador aleatório, sem relação com você | sorteado na criação |
| Progresso, agenda, drills e anotações **de exemplo** | copiados da conta-modelo do autor |
| O que você fizer durante a demonstração | do seu uso |

O que ela **não** guarda: e-mail, nome, provedor de login, endereço de IP. O IP é usado no momento
do pedido, para o freio que impede abrir demonstrações em série, e vive em memória — não é gravado
em tabela nenhuma.

**Ela some sozinha em duas horas.** Passado o prazo, a sessão deixa de autenticar e a conta é
apagada por inteiro na próxima demonstração que alguém abrir — as mesmas sete tabelas da exclusão
comum, sem cópia lógica nem lixeira. Quem quiser encerrar antes tem o botão em *Sua conta* →
**Encerrar demonstração**, que é a mesma `DELETE /api/me`.

O aceite do aviso de responsabilidade **não** é copiado da conta-modelo: quem entra pela
demonstração passa pelo aviso como qualquer um, e o aceite morre junto com a conta.

### Um aviso para quem cura a conta-modelo

O que vale para o visitante vale ao contrário para o autor: **anotação fixada e nota de drill da
conta-modelo são lidas por qualquer pessoa que abra a demonstração.** É a única superfície do app
onde texto do autor é publicado sem um ato de publicar — não há botão de "tornar público", basta a
conta estar em `FOS_DEMO_TEMPLATE_EMAIL`. Nome, e-mail e identidade do autor não são copiados; o
texto é.
