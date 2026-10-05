# Documentação

Para instalar e rodar, veja o [README](../README.md). Este arquivo é o índice da `docs/` e diz
**onde cada mudança se documenta**.

## Índice

**Referência — como o sistema é hoje**

| Arquivo | O que contém |
|---|---|
| [`arquitetura.md`](arquitetura.md) | Stack, estrutura do monorepo, web e landing, e o porquê das escolhas |
| [`regras-de-negocio.md`](regras-de-negocio.md) | Desbloqueio, SRS, diário, streak e freeze, aceite, modo demonstração |
| [`api.md`](api.md) | Mapa de rotas, erros e CORS |
| [`autenticacao.md`](autenticacao.md) | Cadastro, login, papéis, bloqueio, exclusão de conta e demonstração pública |
| [`seguranca.md`](seguranca.md) | Endereço de quem chama, cookie de sessão, host aceito, links de e-mail, cabeçalhos e auditoria |
| [`configuracao.md`](configuracao.md) | Todas as variáveis de ambiente, por ambiente |
| [`deploy.md`](deploy.md) | Compose local e Railway |
| [`operacao.md`](operacao.md) | Monitoramento, alerta, painel de uso e feedback |
| [`banco-de-dados.md`](banco-de-dados.md) | Migrations, regras para migration nova e perfis |
| [`privacidade/`](privacidade/README.md) | O que se guarda de cada pessoa, por quanto tempo e como apagar |
| [`repositorio.md`](repositorio.md) | `main` protegida, checks obrigatórios, Dependabot |
| [`desenvolvimento/`](desenvolvimento/README.md) | Modo dev, testes, lint, app mobile, contas de teste e prints da landing |

**Produto e conteúdo**

| Arquivo | O que contém |
|---|---|
| [`produto/visao.md`](produto/visao.md) | Posicionamento (revisão, não ensino), motivação e escopo |
| [`produto/curriculo.md`](produto/curriculo.md) | Árvore de currículo: módulos, nós e pré-requisitos |
| [`produto/mvp-web.md`](produto/mvp-web.md) | Escopo do MVP web e critérios de sucesso |
| [`produto/disclaimer.md`](produto/disclaimer.md) | Textos do aviso de responsabilidade e versões do aceite |
| [`produto/feedback.md`](produto/feedback.md) | Plano da fila de feedback dentro do app |
| [`produto/publicacao-ios.md`](produto/publicacao-ios.md) | Fase futura: o que publicar no iOS exige |
| [`conteudo/fontes.md`](conteudo/fontes.md) | Régua de fonte para conceito e quiz |
| [`conteudo/fontes-por-no.md`](conteudo/fontes-por-no.md) | De onde veio o texto de cada nó |
| [`conteudo/videos.md`](conteudo/videos.md) | Política de vídeo, critérios, como catalogar e manutenção |
| [`conteudo/videos-por-no.md`](conteudo/videos-por-no.md) | O que o vídeo de cada nó precisa mostrar |
| [`conteudo/videos-catalogo.md`](conteudo/videos-catalogo.md) | O que já está catalogado e o que falta conferir |

**Decisões**

| Arquivo | O que contém |
|---|---|
| [`decisoes/`](decisoes/README.md) | Log de decisões (D1…), um arquivo por decisão, com justificativa e critério de revisão |

## Onde documentar cada mudança

Um assunto tem **um** dono. Quando ele aparece em outro lugar, o segundo vira link — nunca cópia. A
regra fica no arquivo de referência; o motivo dela fica na decisão, e um aponta para o outro.

| Se você mudou… | Documente em |
|---|---|
| Como instalar ou rodar | [`README.md`](../README.md) — e só nesse caso |
| Uma variável de ambiente | [`configuracao.md`](configuracao.md), e o detalhe no dono do assunto |
| Compose, Railway, imagem Docker | [`deploy.md`](deploy.md) |
| Uma rota (nova, removida, parâmetro, código de erro) | [`api.md`](api.md) |
| Login, cadastro, papéis, bloqueio, demonstração pública | [`autenticacao.md`](autenticacao.md) |
| Proxy, cookie, host, cabeçalhos do nginx | [`seguranca.md`](seguranca.md) |
| Achado de auditoria de segurança, ou a correção de um | [`seguranca.md`](seguranca.md#auditoria-de-segurança-setembro-de-2026) — sem roteiro de exploração |
| Streak, SRS, desbloqueio, diário, quiz | [`regras-de-negocio.md`](regras-de-negocio.md) |
| Monitoramento, alerta, painel | [`operacao.md`](operacao.md) |
| Uma migration | [`banco-de-dados.md`](banco-de-dados.md) |
| O que se coleta, guarda ou apaga de alguém | [`privacidade/`](privacidade/README.md) — a promessa está lá por escrito |
| Camada, pacote, estrutura do monorepo | [`arquitetura.md`](arquitetura.md) |
| Testes, lint, ferramenta de desenvolvimento | [`desenvolvimento/`](desenvolvimento/README.md) |
| Ruleset, checks obrigatórios, jobs de CI | [`repositorio.md`](repositorio.md) |
| Aparência de tela que aparece na landing | [`desenvolvimento/prints-da-landing.md`](desenvolvimento/prints-da-landing.md) (refazer o print) |
| Nó, módulo ou pré-requisito | o JSON em `backend/src/main/resources/curriculum/` e [`produto/curriculo.md`](produto/curriculo.md) |
| Conceito ou quiz de um nó | [`conteudo/fontes-por-no.md`](conteudo/fontes-por-no.md) (fontes consultadas) |
| Vídeo catalogado ou critério de vídeo | [`conteudo/`](conteudo/videos.md) |
| Texto do aviso de responsabilidade | [`produto/disclaimer.md`](produto/disclaimer.md) — mudança material sobe a versão do aceite |
| **Uma decisão estrutural e o porquê dela** | um arquivo novo em [`decisoes/`](decisoes/README.md) |

Arquivo novo em `docs/` só quando o assunto não couber em nenhum existente — e ele entra nas tabelas
acima no mesmo PR. Arquivo que passar de ~200 linhas se divide por assunto.

## O que não se documenta à mão

| Não mantenha aqui | Onde a resposta está |
|---|---|
| Corpo de request e response por rota | `backend/openapi.json`, gerado do código, e o Swagger UI |
| Tipos do cliente | `shared/types/generated/`, gerado do OpenAPI (`npm run gen:types`) |
| Lista de nós, quizzes e pré-requisitos | `backend/src/main/resources/curriculum/*.json` |
| Árvore de arquivos classe a classe | O próprio repositório |

## Links quebrados

`node scripts/verificar-links-docs.mjs` confere os links relativos dos `.md` (arquivo e âncora) e
toda menção a `docs/….md` no repositório, inclusive em comentário de código. Roda no job `web` e no
`npm test`.
