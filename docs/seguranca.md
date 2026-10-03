# Segurança

O que protege o app na borda — endereço de quem chama, cookie de sessão, host aceito, links de
e-mail e cabeçalhos — e como conferir cada um depois do deploy. Login, cadastro e papéis estão em
[`autenticacao.md`](autenticacao.md); o que se guarda de cada pessoa, em
[`privacidade/`](privacidade/README.md).

## Endereço de quem chama (`FOS_PROXY_TRUSTED_HOPS`)

O endereço de quem chama sai do `ClientIp`, **nunca** do `getRemoteAddr()` (D51, #77). Atrás do
nginx o segundo devolve o **primeiro** elemento do `X-Forwarded-For` — que é o que o cliente
escreveu —, e com ele todo freio por IP vira decoração. O que vale é o `X-Fos-Forwarded-For`,
escrito pelo nginx em toda requisição proxiada, lido **do fim para o começo** pulando
`fos.proxy.trusted-hops` saltos. Endpoint público novo que precise de freio por origem chama o
`ClientIp` — não copie a expressão.

A variável diz quantos endereços a cadeia do `X-Forwarded-For` ganha até chegar ao backend — cada
salto anexa um ao fim, e é de trás para frente que se acha quem está navegando. No Compose só o
nginx está na frente (`1`). **Na Railway são `3`, e isto foi medido, não deduzido** — o log de
acesso do nginx registra `$http_x_forwarded_for` como ele chega, e em produção ele chega assim:

```
100.64.0.11 - - [...] "GET /robots.txt" 200 "-" "FOS-SONDA" "104.28.228.100, 152.233.23.193"
                                                             └ visitante ┘  └ borda mia1 ┘
```

A borda da plataforma **descarta** o `X-Forwarded-For` de quem chama (a sonda mandou `9.9.9.9` e
ele não chegou) e entrega dois elementos: o visitante e ela mesma (`152.233.22.0/23`, rede CDN77 —
o `x-railway-edge: mia1` da resposta). O nginx anexa o próprio peer (`100.64.0.x`, CGNAT da
Railway) e o backend recebe três. Com `2` a chave seria o nó de borda — **o mesmo para todo mundo
que entra por ele**.

**Errar o número não é mais silencioso** (D53). O backend conta os elementos que chegam e, quando o
número não bate com o declarado, escreve um `WARN` nomeando `FOS_PROXY_TRUSTED_HOPS`, o valor
configurado e o observado (uma vez por hora, não a cada requisição). Foi o que faltou no deploy da
#96: a variável não subiu junto com o código, o app ficou com o default `1` e nada apareceu. O
aviso **não manda copiar o número observado**, nos dois sentidos: cadeia mais longa é o que se vê
quando alguém escreve `X-Forwarded-For` sem borda que saneie, e cadeia mais curta, num backend
declarado acima da topologia real, também pode trazer um elemento forjado dentro dela. O valor
certo é quantos saltos **seus** a requisição atravessa — confira antes de mudar.

**Errar o número não abre a porta em silêncio**, para nenhum dos dois lados: pequeno demais e todo
mundo cai na mesma chave, e os freios passam a recusar gente legítima; grande demais e a cadeia
fica mais curta que o configurado, e aí vale o **último** elemento — o endereço que o salto mais
próximo escreveu —, nunca a cabeça da lista, que é o que quem chama escreveu. Nos dois casos é
ruído visível, não bypass — mas é degradação de verdade, então **a variável entra no mesmo deploy
que o código que depende dela**: sem ela o default `1` vale, e o default é o Compose. Pôr uma CDN
na frente do domínio acrescenta um salto e pede o número novo. Ver
[D51](decisoes/D51-o-endereco-de-quem-chama-passa.md).

## Cookie de sessão (`FOS_COOKIE_SECURE`)

`FOS_COOKIE_SECURE=true` é o que marca `Secure` no `JSESSIONID`. Quem cria esse cookie é o
**Tomcat**, a partir do request do conector, onde a conexão vinda do nginx é `http` puro — o TLS
termina na borda da plataforma. O `forward-headers-strategy: framework` é filtro de servlet e não
alcança essa camada: é por isso que o `XSRF-TOKEN`, criado pelo Spring, já saía com `Secure` e o
cookie de sessão não. Default `false` porque dev (`:8080`) e o Compose (`:8081`) servem em `http`.

**`localhost` sozinho não mostra o estrago**, e isto foi medido: o navegador trata `localhost`
como origem confiável, então lá o cookie `Secure` é guardado e a sessão sobrevive ao F5 mesmo em
`http`. De qualquer outro host `http` — o IP da máquina na rede, que é como se abre o Compose no
celular — o cookie é descartado e **não há sessão**: o login não completa e toda chamada responde
401. O 301 da borda não substitui o flag: o redirecionamento é uma **resposta**, e a requisição que
o provoca já viajou em texto claro com o cookie anexado.

**Errar a variável não é silencioso, nos dois sentidos**, e é o mesmo remédio do
`FOS_PROXY_TRUSTED_HOPS`: quando o flag declarado não bate com o esquema pelo qual a requisição
chegou, o backend escreve um `WARN` nomeando `FOS_COOKIE_SECURE` — uma vez por hora, sem endereço,
sem rota e sem identificador de sessão. `https` com o cookie sem `Secure` é o defeito que a #74
consertou: o cookie viaja desprotegido e **o app funciona**, então ninguém percebe. `http` com o
flag ligado é o contrário, e o sintoma é pior: o navegador descarta o cookie em todo host que não
seja `localhost`, o login não completa e toda chamada responde 401 — o app **não** funciona.
Nenhum dos dois avisos manda mexer de olhos fechados: o esquema vem do `X-Forwarded-Proto`, que
atravessa o nginx vindo de quem chama quando ninguém na frente o saneia, e é afirmação de quem está
na frente, não fato. Confirme e então confira depois do deploy — com sessão nova, porque as abertas
antes seguem com o cookie antigo até vencerem:

```bash
curl -sS -D- -o /dev/null https://fos.fabiocarlesso.com/api/oauth2/authorization/google | grep -i set-cookie
```

Deve trazer `JSESSIONID=...; Path=/; Secure; HttpOnly; SameSite=Lax`. Esse endpoint serve porque
cria a sessão sem exigir login concluído. Ver [D60](decisoes/D60-o-secure-do-cookie-de-sessao.md).

## Host aceito (`PUBLIC_HOST`)

`PUBLIC_HOST` é o único `Host` que o nginx atende. Qualquer outro recebe `444` (conexão fechada sem
resposta) e não chega ao backend — exceto o `/healthz`, porque o healthcheck da Railway chega com o
`Host` dela. Por isso esquecer a variável **não** reprova o deploy e **derruba o site**: todo acesso
pelo domínio público vira `444`. Domínio próprio e o `*.up.railway.app` juntos vão separados por
espaço. No Compose o default é `localhost`; para abrir pelo IP da máquina na rede (celular),
acrescente o IP: `PUBLIC_HOST="localhost 192.168.0.10"`. Conferência depois do deploy — o primeiro
responde, o segundo fecha a conexão:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' https://fos.fabiocarlesso.com/api/auth/providers
curl -sS -o /dev/null -w '%{http_code}\n' -H 'Host: evil.test' https://fos.fabiocarlesso.com/api/auth/providers
```

Se a borda da plataforma recusar o segundo antes do nginx, ótimo — é o que fecha o "suspeito" do
FOS-02 do lado da Railway.

## Links de e-mail (`FOS_PUBLIC_URL`)

`FOS_PUBLIC_URL` é de onde saem os links de confirmação e de redefinição (D62). Nunca da
requisição: o `Host` é de quem chama, e um pedido de redefinição para o endereço de outra pessoa com
`Host` forjado mandaria à vítima um e-mail legítimo com o link para o domínio de quem pediu. Só a
origem, sem caminho, e `https://` — `http://` só vale para `localhost`. Ausente ou inválida, o
cadastro e a recuperação por senha respondem **503** como sem credencial de envio, e a subida
escreve um `WARN` nomeando a variável; o resto do app funciona igual. **Precisa estar na Railway
antes do deploy do código**, pela lição da #96: código e variável entram por caminhos diferentes.

## Cabeçalhos de segurança

Os cabeçalhos de segurança saem do nginx, e só dele (#75, D65). CSP com `frame-ancestors 'none'`,
`X-Frame-Options`, `nosniff`, `Referrer-Policy` e HSTS valem em toda resposta, 404 incluso; o
`Server:` não mostra versão. **Não há variável a configurar.** Em `/api/` os mesmos três que o
Spring Security manda são escondidos com `proxy_hide_header`, para não saírem duplicados.

Eles vêm do `server` em `web/nginx.conf.template`, e **nenhum `location` do app pode ter
`add_header` próprio**: no nginx isso descarta, só ali, todos os do `server`, sem nada falhar. O
`Cache-Control` vem de um `map` por esse motivo, e `scripts/verificar-cabecalhos.mjs` confere a
imagem no job `web`. Vídeo novo de outro host que não o `youtube-nocookie.com` é bloqueado pela CSP
até entrar no `frame-src` (e imagem nova, no `img-src`) do template, no mesmo PR. Conferência depois
do deploy:

```bash
node scripts/verificar-cabecalhos.mjs https://fos.fabiocarlesso.com --api
```

## Auditoria de segurança (setembro de 2026)

Revisão somente leitura do commit `e9aac9d`, em 2026-09-29, sem execução dinâmica. Não encontrou
SQL injection, IDOR, segredo no repositório ou no histórico, nem endpoint administrativo exposto. O
que ela achou e o que já foi corrigido:

| ID | Achado | Severidade | Estado |
|---|---|---|---|
| FOS-01 | Cadastro de e-mail alheio fixava a senha ativada pelo dono ao confirmar | Alta | Corrigido — [D61](decisoes/D61-confirmar-o-e-mail-exige-a.md) |
| FOS-02 | Link de e-mail montado a partir do `Host` da requisição | Média | Corrigido — [D62](decisoes/D62-link-de-e-mail-sai-de.md) |
| FOS-03 | `email_verified` ausente tratado como verificado (Facebook) | Média | Corrigido — [D63](decisoes/D63-e-mail-de-provedor-so-e.md) |
| FOS-04 | Exclusão de conta falhava para quem enviou ou decidiu feedback | Média | Corrigido — [D64](decisoes/D64-exclusao-de-conta-apaga-o-feedback.md) |
| FOS-05 | HTML do app sem CSP, `X-Frame-Options`, HSTS e `nosniff` | Baixa | Corrigido — [D65](decisoes/D65-cabecalhos-de-seguranca-no-nginx-com.md) |
| FOS-06 | Freio de login chaveado só por e-mail permite travar a conta de outra pessoa | Baixa | Pendente |
| FOS-07 | Cadastro não confirmado nunca expira, apesar de o e-mail dizer que some | Baixa | Pendente |
| FOS-08 | Compose publica backend e Postgres em todas as interfaces, sem passar pelo nginx | Baixa | Pendente |
| FOS-09 | Teto global de demonstrações vivas pode ser esgotado por poucos endereços | Baixa | Pendente |
| FOS-10 | Dependências desatualizadas (Spring Boot 3.4.1, `react-router`, `vite`) | Baixa | Pendente — [#78](https://github.com/FabioCarlesso/FightOssStreak/issues/78) |
| FOS-11 | Feedback sem freio de volume por conta e fila sem paginação | Baixa | Pendente |
| FOS-12 | Workflows sem `permissions:` explícito, actions e imagens base sem pinagem, base de geolocalização sem checksum | Informativa | Pendente |
| FOS-13 | Perfil padrão `dev` quando `SPRING_PROFILES_ACTIVE` falta (ver [`deploy.md`](deploy.md#detalhes-que-não-são-óbvios)) | Informativa | Pendente |
| FOS-14 | Swagger e OpenAPI com `permitAll` — inalcançáveis pelo nginx, expostos onde o backend for alcançável direto | Informativa | Pendente |
| FOS-15 | SQL montado por interpolação nos scripts de dev (`seed-dev-users.mjs`, `mint-dev-login.mjs`) | Informativa | Pendente |

O roteiro de cada achado fica fora do repositório, que é público. Achado corrigido ganha decisão
própria em [`decisoes/`](decisoes/README.md) e muda de estado aqui no mesmo PR.

**Limites da auditoria**: nada foi reproduzido contra o app rodando; o comportamento da borda da
Railway com `Host` divergente e as dependências Java não foram verificados com ferramenta; a
configuração na Railway, nos consoles dos provedores e no Resend ficou fora do escopo.
