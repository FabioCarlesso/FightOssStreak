# D65 — Cabeçalhos de segurança no nginx, com CSP bloqueante sem `'unsafe-inline'`

## Justificativa

O HTML e os bundles saíam do nginx sem cabeçalho de segurança nenhum (#75; FOS-05 na auditoria de
outubro). Só o `/api/` tinha os do Spring Security. Entram no `server` do app, todos com `always`:

**CSP** `default-src 'self'` com duas exceções, ambas do embed do YouTube (D7): `frame-src
https://www.youtube-nocookie.com`, o único host que `CurriculumQueryService` monta, e `img-src
https://i.ytimg.com` para a miniatura dos vídeos extras. Mais `frame-ancestors 'none'`, `object-src
'none'`, `base-uri` e `form-action 'self'`. Completam o conjunto `X-Frame-Options: DENY`, que diz o
mesmo que `frame-ancestors` a quem não lê CSP e por isso não cria verdade divergente, `nosniff`,
`Referrer-Policy: strict-origin-when-cross-origin`, **HSTS** de um ano com `includeSubDomains` e sem
`preload` (compromete o domínio inteiro, é decisão à parte) e `server_tokens off`.

**`style-src` ficou sem `'unsafe-inline'`**: o Vite emite CSS em arquivo, e o `style={{...}}` do
React escreve pelo CSSOM, que a CSP não alcança. Conferido no console, sem violação nenhuma, na
landing, em `/hoje`, na árvore, no nó com vídeo e vídeos extras e no registro de treino.

**Bloqueante direto, sem etapa `Report-Only` em produção**: a issue pedia não promover sem olhar o
console, e o console foi olhado contra a mesma imagem que vai para a Railway. Uma etapa a mais só
valeria se produção servisse algo que o Compose não serve, e não serve: a borda da Railway não
injeta script.

**A herança de `add_header`** (`location` com `add_header` próprio descarta todos os do `server`)
foi resolvida tirando o `Cache-Control` dos `location`: ele sai de um `map $uri`, e nenhum
`location` do app pode voltar a ter `add_header`. Quem guarda isso é
`scripts/verificar-cabecalhos.mjs`, que roda no job `web` contra a imagem recém-construída e reprova
o template anterior.

**Em `/api/` o nginx esconde os três que o Spring Security também manda** (`X-Frame-Options`,
`nosniff`, HSTS) com `proxy_hide_header`, para não saírem duplicados: `X-Frame-Options` repetido é
inválido em parte dos navegadores. A fonte única é o nginx.

## Revisar quando

Se entrar vídeo, fonte ou imagem de outro host, a CSP precisa crescer no mesmo PR, e o console é a
conferência. Se algum estilo inline vier a exigir `'unsafe-inline'`, a troca certa é hash ou nonce,
não abrir a diretiva. Se o domínio passar a servir subdomínio sem TLS, o `includeSubDomains` precisa
sair antes

---

[Índice das decisões](../README.md)
