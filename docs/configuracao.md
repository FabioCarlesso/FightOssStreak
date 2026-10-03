# Configuração

Toda variável de ambiente do app, com o valor que ela tem em cada ambiente. As imagens não carregam
host, porta nem credencial fixos (D22): o que muda entre Compose e Railway entra por aqui.

A aplicação **sobe sem segredo nenhum** — é assim que dev e CI rodam. Credencial ausente desliga o
recurso que depende dela (provedor de login, cadastro por senha, demonstração), nunca a subida.

## Variáveis

| Variável | Serviço | Compose local | Railway |
|---|---|---|---|
| `PORT` | web / backend | `80` / `8080` | `8080` nos dois, definida à mão |
| `WEB_HOST_PORT` | web (só Compose) | `8081` | — (porta publicada no host) |
| `BACKEND_ORIGIN` | web | `http://backend:8080` | `http://<serviço-backend>.railway.internal:8080` |
| `NGINX_RESOLVER` | web | `127.0.0.11 ipv6=off` | `[fd12::10] ipv6=on` |
| `SPRING_PROFILES_ACTIVE` | backend | `postgres` | `postgres` |
| `FOS_DB_URL` | backend | `jdbc:postgresql://db:5432/fos` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `FOS_DB_USER` | backend | `fos` | `${{Postgres.PGUSER}}` |
| `FOS_DB_PASSWORD` | backend | `fos` | `${{Postgres.PGPASSWORD}}` |
| `SERVER_ADDRESS` | backend | `0.0.0.0` | `::` |
| `TZ` | web / backend | `America/Sao_Paulo` | `America/Sao_Paulo` |
| `FOS_PROXY_TRUSTED_HOPS` | backend | `1` | `3` — **medido**, ver [segurança](seguranca.md#endereço-de-quem-chama-fos_proxy_trusted_hops) |
| `FOS_COOKIE_SECURE` | backend | `false` | `true` — marca `Secure` no cookie de sessão, ver [segurança](seguranca.md#cookie-de-sessão-fos_cookie_secure) |
| `VITE_PUBLIC_URL` | web (**build**) | — | URL pública do app, ex. `https://fos.up.railway.app` |
| `FOS_OWNER_EMAILS` | backend | vazia | semente de administração: e-mails que viram `ADMIN` na subida, separados por vírgula |
| `FOS_AUTH_PROVIDERS_GOOGLE_CLIENT_ID` | backend | — | id do app no Google |
| `FOS_AUTH_PROVIDERS_GOOGLE_CLIENT_SECRET` | backend | — | segredo do app no Google |
| `FOS_AUTH_PROVIDERS_FACEBOOK_CLIENT_ID` | backend | — | id do app no Meta for Developers |
| `FOS_AUTH_PROVIDERS_FACEBOOK_CLIENT_SECRET` | backend | — | segredo do app no Meta for Developers |
| `FOS_EMAIL_API_KEY` | backend | — | chave do provedor de envio (Resend) |
| `FOS_EMAIL_FROM` | backend | — | remetente, em domínio verificado |
| `FOS_PUBLIC_URL` | backend | `http://localhost:8081` | origem pública dos links de e-mail, ex. `https://fos.fabiocarlesso.com` — **antes** do deploy, ver [segurança](seguranca.md#links-de-e-mail-fos_public_url) |
| `PUBLIC_HOST` | web | `localhost` | domínio que o nginx atende, ex. `fos.fabiocarlesso.com`; mais de um separado por espaço |
| `FOS_DEMO_TEMPLATE_EMAIL` | backend | — | e-mail verificado da conta-modelo da demonstração |
| `FOS_MOBILE_TOKEN_IDLE_DAYS` | backend | `90` | dias sem uso até o token do app mobile vencer (D68) |
| `FOS_MOBILE_MIN_VERSION` | backend | vazia | versão mínima do app que a API atende, ex. `1.2.0`; vazia = qualquer versão |
| `FOS_MOBILE_GOOGLE_CLIENT_IDS` | backend | — | client IDs do app no Google (Android e iOS), separados por vírgula; vazia = sem login Google no app |
| `FOS_MOBILE_APPLE_BUNDLE_ID` | backend | — | bundle id do app iOS; com os três abaixo liga o Sign in with Apple |
| `FOS_MOBILE_APPLE_TEAM_ID` | backend | — | Team ID da conta Apple Developer |
| `FOS_MOBILE_APPLE_KEY_ID` | backend | — | Key ID da chave de Sign in with Apple |
| `FOS_MOBILE_APPLE_PRIVATE_KEY` | backend | — | a chave `.p8`, em PEM — **segredo** |
| `FOS_USAGE_ENABLED` | backend | `true` | `false` desliga a coleta de uso (D50) por inteiro: nada é gravado **e** o endpoint responde 503, que é como o navegador para de mandar evento |
| `FOS_USAGE_GEOIP_DATABASE` | backend | vazia | caminho do CSV local de faixas de IP → país; vazia = país desconhecido |
| `FOS_USAGE_RETENTION_DAYS` | backend | `90` | retenção da tabela **crua** de eventos; o agregado não expira |
| `FOS_USAGE_DAILY_CAP` | backend | `5000` | teto de acessos gravados por dia. Uma linha custa 273 bytes medidos, então 5 000 × 90 dias ≈ 123 MB no pior caso — baixe se o disco for apertado |
| `FOS_USAGE_CRON` | backend | `0 17 3 * * *` | quando o job agrega e expurga; `-` desliga só o agendamento |
| `FOS_HEALTH_WINDOW_MINUTES` | backend | `15` | janela que o alerta de incidente observa (#86); lida da memória do processo |
| `FOS_HEALTH_ERROR_RATE_PERCENT` | backend | `10` | taxa de 5xx na janela que caracteriza incidente |
| `FOS_HEALTH_MIN_REQUESTS` | backend | `20` | piso de requisições para a taxa querer dizer algo — sem ele, 1 erro em 1 requisição é "100%" |
| `FOS_HEALTH_AUTH_REJECTS` | backend | `50` | quantas respostas 401/403 na janela caracterizam pico |
| `FOS_HEALTH_RETENTION_DAYS` | backend | `90` | retenção de `http_stat_hourly` e `app_start` |
| `FOS_HEALTH_CRON` | backend | `0 */5 * * * *` | quando a medição é gravada e o incidente verificado; `-` desliga as duas |
| `FOS_HEALTH_PURGE_CRON` | backend | `0 27 3 * * *` | quando o expurgo das tabelas de saúde roda |
| `FOS_STREAK_FREEZES_PER_MONTH` | backend | `2` | quantos dias perdidos o mês perdoa sem quebrar a sequência (#99, D55); `0` desliga o perdão e devolve o comportamento anterior |

As credenciais `fos/fos/fos` do Compose são de conveniência local. Não reaproveitar.

## Onde cada variável é explicada

| Assunto | Variáveis | Documento |
|---|---|---|
| Portas, perfil, banco, rede, fuso, geolocalização | `PORT`, `SPRING_PROFILES_ACTIVE`, `FOS_DB_*`, `SERVER_ADDRESS`, `NGINX_RESOLVER`, `TZ`, `VITE_PUBLIC_URL`, `FOS_USAGE_GEOIP_DATABASE` | [`deploy.md`](deploy.md#detalhes-que-não-são-óbvios) |
| Endereço de quem chama, cookie, host aceito, links de e-mail | `FOS_PROXY_TRUSTED_HOPS`, `FOS_COOKIE_SECURE`, `PUBLIC_HOST`, `FOS_PUBLIC_URL` | [`seguranca.md`](seguranca.md) |
| Login, cadastro, administração, demonstração e app mobile | `FOS_AUTH_PROVIDERS_*`, `FOS_EMAIL_*`, `FOS_OWNER_EMAILS`, `FOS_DEMO_TEMPLATE_EMAIL`, `FOS_MOBILE_*` | [`autenticacao.md`](autenticacao.md#configuração) |
| Coleta de uso | `FOS_USAGE_*` | [`privacidade/coleta-de-uso.md`](privacidade/coleta-de-uso.md) |
| Saúde do site e alerta | `FOS_HEALTH_*` | [`operacao.md`](operacao.md) |
| Streak | `FOS_STREAK_FREEZES_PER_MONTH` | [`regras-de-negocio.md`](regras-de-negocio.md#streak-e-freeze) |

O valor padrão de cada propriedade, e o nome `fos.*` correspondente, está em
`backend/src/main/resources/application.yml`.
