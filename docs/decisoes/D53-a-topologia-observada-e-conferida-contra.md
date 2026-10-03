# D53 — A topologia observada é conferida contra a declarada em toda requisição proxiada, e a divergência vira `WARN`

## Justificativa

O conserto da D51 depende de código e configuração que **só funcionam juntos** e entram por caminhos
diferentes: o código por PR com CI, a variável `FOS_PROXY_TRUSTED_HOPS` por clique no painel da
Railway. Nada verificava o par, e no deploy da #96 o par não veio junto — o app rodou o conserto sem
que ele consertasse nada, por ~20 minutos, sem uma linha de log, porque o default `1` é um valor
válido.

**É exatamente o formato de falha da regra 7 do `CLAUDE.md`** (renomear job de CI quebrava a
proteção de `main` em silêncio), e a resposta é a mesma que o `scripts/verificar-ruleset.mjs` deu
lá: parar de confiar em memória.

**O que se decidiu**: o `ProxyTopology` recebe do `ClientIp` quantos elementos a cadeia trouxe e
emite `WARN` quando esse número não bate com o declarado, nomeando a variável, o valor configurado e
o observado.

**O que se decidiu não fazer, e por quê.** Não detectar a topologia sozinho: adivinhar de dentro da
requisição é precisamente o que o `getRemoteAddr()` fazia, e a #77 existe para o número ser
*declarado*. Não recusar a subida: o app tem que atender, e degradar com aviso é melhor que não
atender — quem sobe isto pela primeira vez, sem nginx na frente, não pode ser recebido por um app
que não sobe. Não alertar para fora: `WARN` no log basta, e alerta é a #86.

**Nenhum dos dois lados manda copiar o número, e essa é a parte que só apareceu implementando — em
duas rodadas.** Cadeia mais **longa** pode ser proxy novo na frente *ou* quem chama escrevendo
`X-Forwarded-For` onde nenhuma borda saneia (o `$proxy_add_x_forwarded_for` acrescenta ao valor
recebido), e um aviso mandando copiar o número que chegou seria um aviso mandando entregar a chave
dos freios para quem chama. A primeira versão desta implementação concluiu daí que só o lado
**curto** podia trazer o número pronto, "porque forjar header só alonga a cadeia" — e isso está
errado pelo mesmo raciocínio: com `trusted-hops` declarado *acima* da topologia real, um elemento
forjado ainda cabe embaixo do declarado (cadeia de 2 contra 3 configurados pode ser "nginx e mais
nada" com um elemento escrito por quem chama), e seguir o observado poria a chave justamente nesse
elemento — a #77 de volta. Os dois textos, portanto, pedem conferência da topologia; o que muda
entre eles é quanto se pode dizer sobre o número. Efeito colateral aceito: em instalação sem borda
que saneie, **qualquer** requisição com `X-Forwarded-For` dispara o aviso do lado longo — é
advertência, não incidente, e a janela de uma hora é o que a mantém legível.

**Pelo mesmo motivo o silêncio é por janela e não por número**: se cada contagem nova valesse um
aviso, bastaria variar quantos elementos se manda para encher o log de quem lê. Uma hora — some do
fluxo normal, sobrevive ao deploy.

**O que não mudou**: a escolha do elemento no `ClientIp` é a mesma linha de antes; a #97 só
acrescentou a contagem.

**Privacidade**: o aviso carrega dois números e o nome de uma variável de ambiente, e há teste que
reprova endereço no texto — contar elementos não é registrar endereço, e a promessa do
`docs/privacidade/README.md` continua de pé.

## Revisar quando

Quando o par código-configuração deixar de ser verificado só no log: se este `WARN` for ignorado na
prática, o passo seguinte é a #86 (alerta) e não um aviso mais barulhento. E se um dia a
configuração de produção passar a ser versionada no repositório — sincronizada da Railway, ou ela
deixando de ser a plataforma —, esta conferência vira redundante e o lugar de falhar passa a ser o
deploy, não o log

---

[Índice das decisões](../README.md)
