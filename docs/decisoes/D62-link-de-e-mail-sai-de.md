# D62 — Link de e-mail sai de `fos.public-url`, nunca da requisição; e o nginx só atende o `Host` do app

## Justificativa

A auditoria (FOS-02) achou os links de confirmação e de redefinição montados por
`ServletUriComponentsBuilder.fromCurrentContextPath()` — isto é, do `Host` que o cliente mandou,
porque o nginx aceitava qualquer um (`server_name _`), repassava-o como `X-Forwarded-Host` e o
Spring o honra (`forward-headers-strategy: framework`, que a D60 prende pelo OAuth). Um pedido de
redefinição para o endereço de outra pessoa com `Host` forjado fazia o app mandar à vítima um e-mail
**legítimo** com o link para o domínio de quem pediu, e o clique entregava o token de 1 hora.

**São duas camadas, e nenhuma substitui a outra.** A que resolve é o backend: `FOS_PUBLIC_URL` volta
a existir (a nota do README dizia que ela tinha saído com a D48 e que os links "saem da URL da
própria requisição" — era exatamente o defeito), e o `PasswordAccessService` monta os links só com
ela. Ela é validada como **só a origem**, `https://` — `http://` só em `localhost` —, e valor que
não serve **vale como ausente em vez de derrubar a subida**: ausente, o cadastro e a recuperação
respondem 503 como sem credencial de envio, e um `WARN` na subida nomeia a variável. Um erro de
digitação tira uma porta de entrada, não o site; e **cair para a URL da requisição quando falta a
variável foi recusado**, porque é o defeito de volta no primeiro deploy esquecido. A outra é o
nginx: `server_name ${PUBLIC_HOST}` e um `server` default que responde `444`, o que impede a próxima
rota que montar URL — o `redirect_uri` do OAuth já monta — de herdar a mesma classe de defeito. O
`/healthz` responde no default porque o healthcheck da Railway chega com o `Host` dela, e recusá-lo
reprovaria todo deploy.

**O risco que fica é o de sempre (#96, D53, D60)**: código e variável entram por caminhos
diferentes. Aqui os dois lados falham barulhentos e não em silêncio — sem `FOS_PUBLIC_URL` o
cadastro some da tela com `WARN` no log, sem `PUBLIC_HOST` o site responde `444` e o `saude` (D54)
abre issue —, e é por isso que não houve guarda de topologia nova. Fora de escopo, pela issue: mudar
a estratégia de forwarded headers do OAuth.

## Revisar quando

Se o app ganhar outra rota que mande URL por e-mail ou por qualquer canal fora da requisição — ela
usa `fos.public-url`, não o contexto. Se houver mais de um domínio público com links próprios, a
propriedade vira lista e a escolha do link deixa de ser óbvia

---

[Índice das decisões](../README.md)
