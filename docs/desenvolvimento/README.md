# Desenvolvimento

Como rodar o projeto para mexer no código, testar e abrir PR. Para só usar o app, o
[README](../../README.md) basta.

## Modo dev (Java 21 + Node 22)

Hot reload; é o fluxo para desenvolver. Não roda em container.

```bash
npm install

# terminal 1 — API em :8080 (perfil dev, H2 em memória, sem precisar de Docker)
npm run dev:backend

# terminal 2 — web em :5173, com proxy de /api
npm run dev:web
```

Abra <http://localhost:5173>.

O perfil `dev` usa H2 em memória — os dados somem ao reiniciar. Para desenvolver contra Postgres sem
subir a stack inteira:

```bash
docker compose up -d db
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

Como o app exige login e `localhost` não tem provedor nem envio de e-mail configurados, use as contas
de teste para chegar a qualquer tela autenticada — inclusive a do dono:

```bash
node scripts/seed-dev-users.mjs                      # uma vez, com o schema já migrado
node scripts/mint-dev-login.mjs aluno@teste.local    # imprime a URL de entrada
```

Roteiro completo, e por que isto não alcança produção, em
[`contas-de-teste.md`](contas-de-teste.md).

## Testes

```bash
npm test                    # regras de shared/domain + scripts + fluxos de UI do web
cd backend && ./mvnw test   # regras, integridade do currículo e fluxo de ponta a ponta
npm run typecheck           # todos os workspaces TypeScript
```

O teste de integridade do currículo falha se houver ciclo de pré-requisitos, referência quebrada,
código duplicado ou quiz malformado — é o que torna seguro editar a árvore em um PR.

Do lado do web, os testes cobrem os três fluxos que decidem se o produto é usável — o aceite do
disclaimer, o quiz e o registro de drill (D29) — mais a árvore em modo demonstração e a troca de nó
dentro de `/no/:code`, onde o risco é o modo vazar para o uso normal e o estado de um nó aparecer no
seguinte (D31). Rodam em jsdom com o cliente de API mockado — nenhum toca a rede. Para iterar em um
deles, `npm run test:watch --workspace @fos/web`.

## Lint e formatação

```bash
npm run lint                          # ESLint + Prettier em web/, shared/* e scripts/
npm run lint:fix                      # corrige o que é corrigível
cd backend && ./mvnw spotless:check   # formatação e imports do Java
cd backend && ./mvnw spotless:apply   # corrige
```

As duas verificações rodam no CI **antes** dos testes, dentro dos jobs `backend` e `web` — falha
rápida e barata primeiro. Ficam de fora do lint os arquivos gerados (`shared/types/generated/`,
`backend/openapi.json`), o currículo (que é dado editorial, D11) e o Markdown, porque o Prettier
alinha colunas de tabela com espaço e várias tabelas da `docs/` têm células que são parágrafos.

O Java usa `googleJavaFormat` na variante **AOSP** (4 espaços), que é o estilo que o código já
tinha; a variante padrão reformataria o backend inteiro para 2 espaços. O `.editorconfig` na raiz
reflete o mesmo padrão, para o editor não desfazer no salvamento o que o CI vai cobrar.

A formatação inicial de todo o repositório está em um commit só, registrado em
[`.git-blame-ignore-revs`](../../.git-blame-ignore-revs). Para o `git blame` local ignorá-lo:

```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

## Tipos gerados

`shared/types/generated/` é gerado a partir do OpenAPI do backend — não se edita à mão:

```bash
npm run gen:types
```

O CI regenera e falha se o resultado divergir do que está commitado.

## Links da documentação

```bash
node scripts/verificar-links-docs.mjs
```

Confere os links relativos dos `.md` (arquivo e âncora) e toda menção a `docs/….md` no repositório,
inclusive em comentário de código. Roda no job `web` e no `npm test`. Renomeou um arquivo de `docs/`
ou o título de uma seção? Atualize quem aponta para ele no mesmo PR.

## Fluxo de PR

`main` é protegida: não aceita push direto e o merge só libera com os jobs `backend` e `web` verdes.

```bash
git switch -c minha-mudanca
# ... commits ...
git push -u origin minha-mudanca
gh pr create --fill
```

Regras do repositório, checks obrigatórios e Dependabot em [`../repositorio.md`](../repositorio.md).
Antes de documentar uma mudança, veja em [`../README.md`](../README.md) qual arquivo é o dono do
assunto.

## Procedimentos

| Arquivo | Quando usar |
|---|---|
| [`contas-de-teste.md`](contas-de-teste.md) | Entrar no app em `localhost` sem provedor configurado |
| [`prints-da-landing.md`](prints-da-landing.md) | PR que mexe na aparência da árvore, do nó, do drill, da tela inicial ou do diário |
| [`prints-historico.md`](prints-historico.md) | O que cada recaptura mudou, e as armadilhas que ela ensinou |
| [`../conteudo/videos.md`](../conteudo/videos.md) | Catalogar ou trocar vídeo de um nó |
