# D25 — O nginx zera o `Origin` quando ele aponta para o próprio app, e só nesse caso

## Justificativa

O Chrome manda `Origin` em todo POST, inclusive same-origin, e o Spring 6 trata *qualquer*
requisição com `Origin` como CORS — sem checar se a origem é a própria. Como o `WebCorsConfig` só
libera o dev server do Vite, aceitar o disclaimer pelo browser em container devolvia 403 "Invalid
CORS request". Só o browser expunha isso: curl não manda `Origin`, e em dev o Vite manda
`http://localhost:5173`, que está na lista. A alternativa seria pôr a origem pública na allowlist,
mas aí a URL de produção viraria configuração do backend, e a promessa de "o fluxo em container não
precisa de CORS" cairia. Zerar o header em requisição que o próprio app originou não perde
informação: quem a recebeu foi o nginx que serve a página. Requisição de outra origem chega com o
`Origin` intacto e o Spring segue recusando — o `map` compara o host do `Origin` com o `Host` da
requisição, então vale igual atrás do TLS da Railway, onde o `Origin` é `https://` e o nginx recebe
`http`

## Revisar quando

Ao introduzir login. Aí passa a existir sessão para forjar, e a defesa certa é token de CSRF, não
inferir origem através de proxy — **atingido pela D36**: agora há sessão e token de CSRF
(`CookieCsrfTokenRepository`), que é a defesa certa. O `map` do `Origin` **permanece**, e não por
inércia: sem ele o `WebCorsConfig` volta a recusar o POST same-site do browser em container com
"Invalid CORS request", que é o defeito que a D25 conserta. Um resolve CORS, o outro resolve CSRF

---

[Índice das decisões](../README.md)
