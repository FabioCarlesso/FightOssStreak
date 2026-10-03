# Documentação

Para rodar o projeto, veja o [README](../README.md). Este arquivo é o índice da `docs/` e diz **onde
cada mudança se documenta**.

> A documentação está sendo reorganizada em arquivos curtos, um por assunto
> ([#135](https://github.com/FabioCarlesso/FightOssStreak/issues/135)). Enquanto isso, parte da
> referência ainda mora no README da raiz — a tabela abaixo aponta para onde ela está **hoje**.

## Índice

| Arquivo | O que contém |
|---|---|
| [`00-visao-geral.md`](00-visao-geral.md) | Posicionamento (revisão, não ensino), motivação e escopo |
| [`01-stack-tecnica.md`](01-stack-tecnica.md) | Stack escolhida e o porquê; grafo de pré-requisitos; OpenAPI |
| [`02-publicacao-ios-desafios.md`](02-publicacao-ios-desafios.md) | Plano futuro: o que publicar no iOS exige |
| [`03-estrutura-projeto.md`](03-estrutura-projeto.md) | Estrutura do monorepo |
| [`04-arvore-curriculo-bjj.md`](04-arvore-curriculo-bjj.md) | Árvore de currículo: módulos, nós e pré-requisitos |
| [`05-mvp-web-plano.md`](05-mvp-web-plano.md) | Plano do MVP web e critérios de sucesso |
| [`06-disclaimer-responsabilidade.md`](06-disclaimer-responsabilidade.md) | Textos do aviso de responsabilidade e versões do aceite |
| [`07-decisoes.md`](07-decisoes.md) | Log de decisões (D1…) com justificativa e critério de revisão |
| [`08-curadoria-videos.md`](08-curadoria-videos.md) | Política de vídeo, como catalogar e estado do catálogo |
| [`09-regras-repositorio.md`](09-regras-repositorio.md) | `main` protegida, checks obrigatórios e como aplicar a ruleset |
| [`10-prints-da-landing.md`](10-prints-da-landing.md) | Quando e como refazer os prints da landing |
| [`11-privacidade.md`](11-privacidade.md) | Dado pessoal guardado, coleta de uso, dado de saúde, saúde do site |
| [`12-fontes-de-conteudo.md`](12-fontes-de-conteudo.md) | Régua de fonte para conceito e quiz; tabela `nó → fontes` |
| [`13-feedback-usuarios.md`](13-feedback-usuarios.md) | Plano da fila de feedback dentro do app |
| [`14-contas-de-teste-local.md`](14-contas-de-teste-local.md) | Como entrar no app em `localhost` sem provedor |

## Onde documentar cada mudança

Um assunto tem **um** dono. Quando ele aparece em outro lugar, o segundo vira link — nunca cópia.

| Se você mudou… | Documente em |
|---|---|
| Como instalar ou rodar localmente | [README › Rodando](../README.md#rodando) |
| Uma variável de ambiente | [README › Deploy na Railway](../README.md#deploy-na-railway) (tabela de variáveis) |
| Deploy, Compose ou Railway | [README › Deploy na Railway](../README.md#deploy-na-railway) |
| Login, cadastro, papéis, bloqueio ou rota de admin | [README › Acesso e contas](../README.md#acesso-e-contas) |
| Testes, lint ou formatação | [README › Testes](../README.md#testes) e [Lint e formatação](../README.md#lint-e-formatação) |
| Estrutura do monorepo | [`03-estrutura-projeto.md`](03-estrutura-projeto.md) |
| Nó, módulo ou pré-requisito do currículo | O JSON em `backend/src/main/resources/curriculum/`; mudança pedagógica também em [`07-decisoes.md`](07-decisoes.md) |
| Conceito ou quiz de um nó | [`12-fontes-de-conteudo.md`](12-fontes-de-conteudo.md) (fontes consultadas) |
| Vídeo catalogado ou critério de vídeo | [`08-curadoria-videos.md`](08-curadoria-videos.md) |
| Texto do aviso de responsabilidade | [`06-disclaimer-responsabilidade.md`](06-disclaimer-responsabilidade.md) — mudança material sobe a versão do aceite |
| O que se coleta, guarda ou apaga de alguém | [`11-privacidade.md`](11-privacidade.md) |
| Ruleset, checks obrigatórios ou jobs de CI | [`09-regras-repositorio.md`](09-regras-repositorio.md) |
| Aparência de tela que aparece na landing | [`10-prints-da-landing.md`](10-prints-da-landing.md) (refazer o print) |
| Conta de teste local | [`14-contas-de-teste-local.md`](14-contas-de-teste-local.md) |
| **Uma decisão estrutural e o porquê dela** | [`07-decisoes.md`](07-decisoes.md) |

Arquivo novo em `docs/` só quando o assunto não couber em nenhum existente — e ele entra nas duas
tabelas acima no mesmo PR.

## O que não se documenta à mão

| Não mantenha aqui | Onde a resposta está |
|---|---|
| Corpo de request e response por rota | `backend/openapi.json`, gerado do código |
| Tipos do cliente | `shared/types/generated/`, gerado do OpenAPI (`npm run gen:types`) |
| Lista de nós, quizzes e pré-requisitos | `backend/src/main/resources/curriculum/*.json` |
| Árvore de arquivos classe a classe | O próprio repositório |

## Links quebrados

`node scripts/verificar-links-docs.mjs` confere os links relativos dos `.md` (arquivo e âncora) e
toda menção a `docs/….md` no repositório, inclusive em comentário de código. Roda no job `web` e no
`npm test`. Renomeou um arquivo ou o título de uma seção? Atualize quem aponta para ele no mesmo PR.
