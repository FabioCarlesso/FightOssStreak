# Regras do repositório

As três regras que valem para este repositório:

1. **`main` é a branch default** — e continua sendo. Nada de branch `master`, `develop` ou similar.
2. **`main` só muda por pull request** — push direto é bloqueado, inclusive para o dono do repositório.
3. **PR só pode ser mergeado com o CI verde** — os checks `backend` e `web` precisam passar.

A configuração está versionada em [`.github/rulesets/main.json`](../.github/rulesets/main.json) e é
aplicada por [`scripts/apply-repo-rules.sh`](../scripts/apply-repo-rules.sh).

## Por que versionar isso

Regra de repositório é configuração de servidor, não código — não existe arquivo que o GitHub leia
sozinho para aplicá-la (diferente de `.github/workflows/`). O JSON aqui é a **fonte da verdade
declarada**; o script empurra para a API. Sem isso, a única cópia da regra ficaria dentro da tela de
Settings, sem histórico, sem revisão e sem forma de restaurar depois de um clique errado.

Consequência prática: **mudar as regras pela interface do GitHub faz o repositório divergir do que
está aqui**. O caminho é editar o JSON, revisar em PR e rodar o script.

## Aplicando

Precisa do [`gh` CLI](https://cli.github.com) autenticado com permissão de admin no repositório:

```bash
./scripts/apply-repo-rules.sh
```

O script é idempotente: cria a ruleset se não existir, atualiza se já existir, e no fim imprime o
estado final. Rodar duas vezes não duplica nada.

<details>
<summary>Alternativa pela interface, sem CLI</summary>

- **Branch default**: Settings → General → Default branch → `main`.
- **Ruleset**: Settings → Rules → Rulesets → New ruleset → *Import a ruleset* → selecione
  `.github/rulesets/main.json`.

</details>

## O que a ruleset faz, regra por regra

| Regra no JSON | Efeito |
|---|---|
| `conditions.ref_name.include: ["~DEFAULT_BRANCH"]` | Vale para a branch default — se um dia a default mudar, a proteção acompanha em vez de ficar apontando para um nome morto |
| `pull_request` | Push direto em `main` é recusado; a mudança tem que entrar por PR |
| `required_status_checks` (`backend`, `web`) | O botão de merge fica travado até os dois checks passarem |
| `deletion` | `main` não pode ser apagada |
| `non_fast_forward` | Sem force-push em `main` — histórico já mergeado não é reescrito |
| `bypass_actors: []` | Ninguém escapa, nem o dono do repositório |

Duas escolhas merecem explicação:

- **`required_approving_review_count: 0`.** O projeto é solo (D5) e o GitHub não deixa ninguém
  aprovar o próprio PR. Exigir 1 aprovação travaria todo PR permanentemente. O portão aqui é o CI,
  não a revisão humana. Se o projeto ganhar um segundo contribuidor, subir para `1` é a mudança.
- **`strict_required_status_checks_policy: false`.** Não exige que a branch esteja atualizada com
  `main` antes do merge. Com poucos PRs simultâneos, o custo de ficar clicando em "Update branch"
  supera o risco de conflito semântico. Com PRs concorrentes de verdade, vale ligar.

## Os checks obrigatórios

O contexto de um required check do GitHub Actions é o **nome do job**, não o nome do workflow:

| Job | Arquivo | O que cobre |
|---|---|---|
| `backend` | [`.github/workflows/backend.yml`](../.github/workflows/backend.yml) | `mvnw verify` (regras, integridade do currículo, fluxo ponta a ponta) e `openapi.json` em dia |
| `web` | [`.github/workflows/web.yml`](../.github/workflows/web.yml) | testes de `shared/domain`, `shared/types` em dia, typecheck e build |

### Workflows que deliberadamente não são portão

O job `videos` ([`videos.yml`](../.github/workflows/videos.yml)) verifica semanalmente se os vídeos
catalogados continuam no ar (`08-curadoria-videos.md`). Ele **não** está na ruleset e não deve
entrar: um vídeo que o autor tirou do ar é problema de curadoria, não defeito do código, e travar o
merge por causa disso pararia o projeto por algo que ninguém aqui controla. Por isso ele nem roda em
`pull_request` — o gatilho é a agenda, e o aviso sai por issue.

**Renomear um desses jobs quebra a proteção em silêncio**: o check exigido deixa de existir e o PR
fica preso em *"Expected — waiting for status to be reported"*. Renomeou o job? Atualize
`.github/rulesets/main.json` e rode o script no mesmo PR.

### Por que os workflows não filtram por caminho em PR

Os dois workflows rodavam só quando arquivos relevantes mudavam. Isso é incompatível com required
check: um workflow que o filtro de path descarta **não reporta status nenhum** — não reporta
"sucesso por não ser aplicável" — e o PR fica travado esperando para sempre. Um PR só de
documentação nunca mais mergearia.

Por isso o filtro `paths` foi mantido no `push` para `main` (onde nada depende do resultado) e
removido do `pull_request`. O custo é rodar os dois jobs em todo PR, inclusive nos que só tocam
`docs/`. Para um projeto solo em repositório público, com minutos de Actions gratuitos, é barato
perto de um portão que trava sozinho.

## Fluxo de trabalho que isso implica

```bash
git switch -c minha-mudanca
# ... commits ...
git push -u origin minha-mudanca
gh pr create --fill        # ou pela interface
# CI roda; com backend e web verdes, o merge libera
```

## Conferindo que está valendo

```bash
git switch main && git commit --allow-empty -m "teste" && git push origin main
# esperado: rejeitado — "Changes must be made through a pull request"
git reset --hard origin/main   # desfaz o commit local de teste
```

Se o push passar, a ruleset não está ativa para o seu usuário: confira `enforcement: "active"` e
`bypass_actors: []` no JSON e rode o script de novo.
