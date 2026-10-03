# D51 — O endereço de quem chama passa a vir da infraestrutura, e a topologia vira variável de ambiente

## Justificativa

O `AccessRateLimiter` estava certo e mesmo assim dois dos três freios públicos eram decoração,
porque a **chave** vinha do lugar errado. Com `forward-headers-strategy: framework` (necessário
desde a D23/D24 para o redirect do OAuth), o `getRemoteAddr()` não devolve o peer da conexão:
devolve o **primeiro** elemento do `X-Forwarded-For` — e como o nginx encaminha
`$proxy_add_x_forwarded_for`, que *acrescenta* ao valor recebido em vez de substituí-lo, o primeiro
elemento é literalmente o que o cliente escreveu. Trocar o header a cada requisição zerava o freio
por IP do cadastro/recuperação e o da demonstração; sobravam o freio por e-mail e o teto de
demonstrações vivas. O arquivo do nginx já tratava `X-Forwarded-Host`, `X-Forwarded-Port` e
`Forwarded` com esse cuidado, com comentário e tudo — só o `X-Forwarded-For` ficara no valor de
acumulação, por ser o idioma habitual do nginx.

**O que se decidiu, e por quê.** O nginx passou a escrever a cadeia inteira num header próprio,
`X-Fos-Forwarded-For`, e o backend lê dele **do fim para o começo**. Header próprio porque o
`ForwardedHeaderFilter` *remove* todos os `X-Forwarded-*` da requisição antes de qualquer código da
aplicação vê-los, e ele é registrado em `Ordered.HIGHEST_PRECEDENCE` — não existe filtro que rode
antes. Do fim para o começo porque o elemento confiável é o posto pelo salto **mais próximo**, nunca
o primeiro. E **quantos** saltos pular é `fos.proxy.trusted-hops`, variável de ambiente: 1 no
Compose, onde só o nginx está na frente; **3 na Railway**.

**O número da Railway foi medido, e a medição contradisse a dedução** — a primeira versão desta
entrada dizia 2, supondo o comportamento habitual de borda (anexar o peer de baixo ao que chegou). O
log de acesso do nginx da imagem oficial já registra `$http_x_forwarded_for` como ele chega, então
bastou ler o deploy log de produção com uma requisição marcada: a borda da Railway **descarta** o
`X-Forwarded-For` de quem chama — a sonda mandou `9.9.9.9` e ele não chegou — e entrega
`<visitante>, <nó de borda>` (`152.233.22.0/23`, rede CDN77 — o `x-railway-edge: mia1` que a própria
resposta anuncia); o nginx anexa o próprio peer (`100.64.0.x`, CGNAT da Railway) e o backend recebe
**três** elementos. Com 2, a chave seria o nó de borda, o mesmo para todo mundo que entra por ele.
Duas consequências que valem registro.

**A primeira**: em produção a #77 **não era explorável hoje**, porque a borda já saneava o header —
o defeito era real no caminho do Compose (e em qualquer instalação sem borda que saneie), e seria
real na Railway no dia em que ela mudasse esse comportamento. O que esta decisão compra é o app
deixar de *depender* do que está na frente dele.

**A segunda**: errar o número é **visível e reversível por variável** — pequeno demais colapsa todo
mundo numa chave só e os freios passam a recusar gente legítima, que é ruído que aparece e não porta
que abre em silêncio —, mas continua sendo degradação de verdade, e por isso a variável precisa
entrar **no mesmo deploy** que este código: sem ela vale o default 1, que é o do Compose.

**E errar para cima degrada igual, não abre**: o *fallback* de cadeia mais curta que o configurado
cai no **último** elemento, não no primeiro — o último foi escrito pelo salto mais próximo, que é
infraestrutura nossa, e o primeiro é o que quem chama escreveu no `X-Forwarded-For`, porque o
`$proxy_add_x_forwarded_for` acrescenta ao que recebeu. A primeira versão caía no primeiro, sob a
justificativa de que "todo mundo divide a chave"; isso só vale quando a cadeia inteira é
infraestrutura, e com `trusted-hops` maior que a topologia real — ou com alguém alcançando o nginx
sem passar pela borda suposta na frente — ela devolvia a chave para o cliente **em silêncio**, que é
o defeito que esta decisão existe para eliminar. Apareceu na revisão da PR e foi medido: com
`FOS_PROXY_TRUSTED_HOPS=3` e a cadeia de dois elementos do Compose, sete requisições com endereço
forjado passaram sem um `429`.

**O que não mudou**: o `AccessRateLimiter` (ele sempre recebeu uma `String` e sempre esteve certo),
o freio por e-mail, e o `X-Forwarded-For` que o nginx continua encaminhando para o Spring montar
URL.

**O que ficou de fora, de propósito**: freio em endpoint autenticado, backoff progressivo, freio
distribuído (uma réplica, D22) e `CF-Connecting-IP` — não há CDN na frente do domínio hoje.

**Detalhes que só apareceram implementando**: o `ClientIp` deixou de ser utilitário estático e virou
componente, porque agora depende de configuração — o `UsageCollector` passou a recebê-lo por
construtor; e o *fallback* de quando não há nginx na frente (dev, teste, chamada direta) não pode
ser o `getRemoteAddr()` da requisição que chega ao controller, que é justamente o valor envenenado
pelo filtro — ele desce pelos wrappers até o request de baixo, que é a conexão TCP e ninguém
escreve. É o que faz o teste de `MockMvc` significar alguma coisa: seis requisições com
`X-Forwarded-For` diferente em cada uma **precisam** bater no 429.

**Adendo medido depois, na #97**: a variável *não* entrou no mesmo deploy que este código — o merge
subiu e o backend ficou com o default `1`, em silêncio, porque `1` é valor perfeitamente válido. E o
estrago foi **pior do que esta entrada previu**. Ela dizia que errar para baixo colapsa todo mundo
numa chave só; mas o terceiro elemento da cadeia é o peer do nginx, e **na Railway ele rotaciona**:
no log de acesso, seis requisições seguidas do mesmo cliente saíram de `100.64.0.6`, `.13`, `.14` e
`.15`, e o nó de borda também varia entre `.193` e `.194` — só o primeiro elemento, o visitante, é
estável. Com o número errado, portanto, os dois lados erram ao mesmo tempo: visitantes distintos
colidindo num punhado de endereços de infraestrutura **e** quem trocasse de conexão ganhando
contador novo, que é o próprio bypass que a #77 existia para fechar. Corrigido à mão (variável
criada, redeploy, e o teste contra produção passou a dar `202, 202, 202, 202, 202, 429` com
`X-Forwarded-For` forjado a cada requisição); o que sobrou do episódio é o modo de falha, e ele
virou a D53.

## Revisar quando

Quando a topologia mudar — CDN na frente do domínio, um proxy a mais, ou a Railway trocando o
comportamento da borda. Aí o valor de `FOS_PROXY_TRUSTED_HOPS` muda junto, no mesmo deploy, e é por
isso que ele é variável e não constante. Se a plataforma passar a oferecer um header próprio e
confiável com o endereço do visitante (`CF-Connecting-IP` e parentes), ele vira a fonte e a contagem
de saltos some. E se um dia houver mais de uma réplica, a decisão a revisitar é a do freio em
memória (D22), não esta

---

[Índice das decisões](../README.md)
