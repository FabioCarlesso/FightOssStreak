# API

Mapa das rotas e das regras que valem para todas. O contrato completo (corpo de request e response)
é gerado do código: `backend/openapi.json`, e o Swagger UI em
<http://localhost:8080/swagger-ui.html> com o backend de pé. Os tipos do front saem desse spec
(`npm run gen:types`) — não se escrevem à mão.

Tudo vive sob `/api`, que é o único caminho que o nginx encaminha ao backend (D23/D24). Sem sessão,
qualquer rota fora da lista pública responde `401`.

## Rotas públicas

Não exigem sessão, porque servem justamente a quem ainda não tem uma.

| Rota | O que faz |
|---|---|
| `GET /api/auth/providers` | provedores de login habilitados neste ambiente |
| `POST /api/auth/cadastro` | cria a conta e manda o link de confirmação |
| `GET` / `POST /api/auth/verificar/{token}` | consulta o link de confirmação / confirma o e-mail (com a senha) e abre a sessão |
| `POST /api/auth/verificacao/reenviar` | manda outro link de confirmação |
| `POST /api/auth/login` | entra com e-mail e senha |
| `POST /api/auth/senha/esquecida` | manda o link de redefinição |
| `GET` / `POST /api/auth/senha/redefinir/{token}` | consulta o link de redefinição / troca a senha |
| `GET /api/oauth2/authorization/{provedor}` | inicia o login por provedor (Spring Security) |
| `GET /api/login/oauth2/code/{provedor}` | retorno do provedor (redirect URI) |
| `POST /api/demo/sessao` | abre uma demonstração (D39); `404` se não houver conta-modelo |
| `POST /api/telemetria/evento` | registra um acesso a uma rota do app (D50) |

Cadastro, reenvio e recuperação respondem **igual** para e-mail que existe e que não existe. Fluxos
e regras em [`autenticacao.md`](autenticacao.md).

## Rotas da conta

| Rota | O que faz |
|---|---|
| `GET /api/me` | conta autenticada, com o estado do acesso e o `role` |
| `DELETE /api/me` | exclui a conta e todo o dado dela |
| `GET /api/disclaimer` · `POST /api/disclaimer/accept` | estado do aceite do aviso / registra o aceite com data e versão |
| `GET /api/curriculum/tree` | árvore completa com o bloqueio resolvido para a conta |
| `GET /api/nodes/{code}` | detalhe do nó: conceito, vídeo, quiz e agenda de revisão |
| `POST /api/nodes/{code}/quiz` | submete o quiz e devolve nota e explicações |
| `POST /api/nodes/{code}/drill` | registra "treinei isso hoje" — alimenta streak e reagenda o SRS |
| `PUT /api/nodes/{code}/note` | grava a anotação fixada do nó; em branco limpa |
| `GET /api/reviews/today` | o que drillar hoje, do mais atrasado para o menos |
| `GET /api/streak` | streak atual, recorde, dias ativos e saldo de freeze do mês — e **grava** o freeze gasto (D55) |
| `GET /api/streak/historico` | dias com registro no período — insumo do heatmap (D59) |
| `GET` / `POST /api/sessoes` | linha do tempo do diário / registra um treino (só a data é obrigatória) |
| `GET` / `PATCH /api/sessoes/{id}` | detalhe / edição da sessão; as técnicas vinculadas não são tocadas |
| `POST /api/sessoes/{id}/tecnicas` | vincula uma técnica — alimenta o SRS pelo caminho de sempre |
| `DELETE /api/sessoes/{id}/tecnicas/{code}` | desvincula a técnica; o drill permanece no histórico do nó |
| `POST /api/feedback` | envia um feedback, opcionalmente amarrado a um nó |
| `GET /api/metrics/mvp` | os quatro critérios de sucesso do MVP, com valor e meta |

As regras por trás de streak, diário e SRS estão em [`regras-de-negocio.md`](regras-de-negocio.md).

## Administração

Todas sob `/api/admin/**` e portanto `403` para quem não administra.

| Rota | O que faz |
|---|---|
| `GET /api/admin/painel` | acessos, funil, origem e perfil de uso; `dias` é 7, 30 ou 90 (qualquer outro valor é `400`) |
| `GET /api/admin/saude` | requisições, erro e latência do próprio app; `horas` é 24, 72 ou 168. Não responde se o site ficou fora do ar — app parado não mede |
| `GET /api/admin/usuarios` | lista as contas, da mais nova para a mais antiga; filtros `status`, `role` e `verificado`, busca por trecho de e-mail ou rótulo, `page`/`size` com teto de 100 |
| `POST /api/admin/usuarios/{id}/role` | promove a `ADMIN` ou rebaixa a `USUARIO` |
| `POST /api/admin/usuarios/{id}/status` | bloqueia (`RECUSADO`) ou devolve o acesso (`APROVADO`), com motivo |
| `GET /api/admin/feedback` | fila de feedback |
| `POST /api/admin/feedback/{id}/status` | decide um feedback |

A conta de demonstração (D39) não aparece na lista e não aceita nenhuma dessas ações.

## Erros

- **`401`** sem sessão; **`403`** sem papel, ou `acesso_recusado` para conta bloqueada (o portão
  relê o estado a cada requisição); **`409`** ao bloquear ou rebaixar a si mesmo ou a última conta
  de administração.
- **Entrada inválida responde `4xx`, nunca `500`.** O `@ExceptionHandler(Exception.class)` do
  `ApiExceptionHandler` tem precedência sobre o resolvedor do Spring, e o desvio de 4xx no começo
  dele só reconhece quem implementa `ErrorResponse`. `TypeMismatchException` e
  `HttpMessageNotReadableException` não implementam, e por isso têm handler próprio — foi assim que
  `?dias=abc` e corpo malformado deixaram de responder 500. **Rota nova com parâmetro tipado ou com
  corpo exige conferir que entrada inválida responde 4xx.**
- **`500` carrega um identificador de correlação** — oito caracteres, no corpo e no log. A mensagem
  da exceção fica só no log: erro não previsto é justamente aquele cujo texto ninguém revisou.

## CORS

Em produção web e API são a mesma origem e a lista de CORS nunca é consultada. Atrás do proxy do
Vite ela é: **verbo novo em rota sob `/api` precisa entrar na lista de métodos do
`SecurityConfig`**, senão responde `403 Invalid CORS request` antes do `ApiExceptionHandler` — sem
corpo, e sem quebrar teste de MockMvc nem de jsdom. Foi o defeito do `PATCH` do diário; o
`CorsMetodosTest` agora confere a lista contra os verbos que os controladores declaram.
