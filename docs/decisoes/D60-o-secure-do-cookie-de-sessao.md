# D60 — O `Secure` do cookie de sessão é variável de ambiente, e a razão é a mesma que prende `forward-headers-strategy: framework`

## Justificativa

Em produção os dois cookies da mesma resposta discordavam: o `XSRF-TOKEN` saía com `Secure` e o
`JSESSIONID` não. A discordância é o diagnóstico — não é configuração esquecida, é **diferença de
camada**. O `XSRF-TOKEN` nasce no Spring, a partir do request já embrulhado pelo
`ForwardedHeaderFilter`, onde `isSecure()` vale `true` porque o nginx encaminhou `X-Forwarded-Proto:
https`; o `JSESSIONID` nasce no **Tomcat**, que lê o request do conector, e ali a conexão que veio
do nginx é `http` puro, porque o TLS termina na borda da plataforma. `forward-headers-strategy:
framework` é filtro de servlet: conserta o que o Spring enxerga e não alcança a criação do cookie
pelo Tomcat. A estratégia `native` alcançaria, via `RemoteIpValve`, e **por isso foi recusada**: ela
existe como `framework` justamente porque é dela que depende a montagem do `redirect_uri` do OAuth
(D36), e trocá-la para resolver um flag traria de volta o provedor recusando login. Sobra ser
**ambiente e não constante**, e isso não é meio-termo: `true` é errado onde não há TLS, e dev
(`:8080`) e o Compose (`:8081`) servem em `http`.

**O quanto isso quebra só apareceu medindo**, e a medição corrigiu a primeira redação desta entrada:
em `localhost` não quebra nada, porque o navegador trata `localhost` como origem confiável e guarda
o cookie `Secure` mesmo em `http` — a sessão sobrevive ao F5. Quebra em **qualquer outro host
`http`**: abrindo o mesmo Compose pelo IP da máquina na rede (que é como se testa no celular), o
cookie é descartado, o login não completa e toda chamada responde 401. O default `false` continua
sendo o certo, mas pelo motivo verdadeiro, e não pelo que parecia óbvio. Default `false`, valor de
produção pelo ambiente, sem perfil `prod` novo só para isso.

**O 301 da borda não tornava isto cosmético**: redirecionar `http://` para `https://` é uma
**resposta**, e a requisição que a provoca já viajou em texto claro com o cookie anexado — link
`http://`, endereço digitado sem esquema ou downgrade na rede bastam, e sem HSTS nada impede. O que
**não** entrou junto: `SameSite` segue `Lax` pelo motivo já documentado no `application.yml` (o
retorno do provedor é navegação cross-site), e cabeçalhos de segurança são issue própria. O risco
que fica era o mesmo da D51/#96: **código e variável entram por caminhos diferentes**, e o default é
valor válido — um deploy sem a variável voltaria ao defeito em silêncio, e teste automatizado não
cobre isso, porque o que quebra não é o código. Por isso a mesma resposta que a D53 deu ao
`trusted-hops`: o `CookieSecureTopology` confronta o flag **declarado** com o esquema **observado**
e escreve `WARN` quando os dois não batem — uma vez por hora, nomeando a variável, sem endereço nem
identificador de sessão, e sem impedir a subida.

**São duas divergências, e a revisão da PR mostrou que vigiar só uma era o erro**: `https` com o
cookie sem `Secure` é o defeito que abriu a issue, e o app *funciona* — por isso passou despercebido
até uma revisão de segurança; `http` com o flag ligado é o contrário, e o sintoma é pior, porque o
app **não** funciona (é o 401 medido acima, em todo host que não seja `localhost`) e sem aviso o log
não diria por quê. Vigiar só o primeiro deixaria mudo justamente o estado em que alguém acabou de
quebrar o ambiente — inclusive obedecendo ao primeiro aviso. Nenhum dos dois **manda mexer de olhos
fechados**, e isso não é cautela decorativa: o `X-Forwarded-Proto` atravessa o nginx vindo de quem
chama quando ninguém na frente o saneia (o `map` do `nginx.conf.template` só recorre ao `$scheme`
quando o header chega vazio), então o esquema observado é afirmação de quem está na frente, não
fato. Quem confirma que acabou é o `curl` anotado no README. O teste que existe protege outra coisa:
`ServerProperties` ignora chave desconhecida, então um erro de digitação sob `cookie:` deixaria o
flag em `null` — que é "não marque" — sem quebrar a subida.

## Revisar quando

Se o TLS deixar de terminar na borda, ou se a montagem do `redirect_uri` deixar de depender do
filtro — aí `forward-headers-strategy: native` volta à mesa e o flag deixa de precisar de variável.
Quando os cabeçalhos de segurança entrarem com HSTS, revisitar se o default `false` ainda é o certo
para o Compose

---

[Índice das decisões](../README.md)
