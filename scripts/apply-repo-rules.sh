#!/usr/bin/env bash
#
# Aplica no GitHub as regras descritas em docs/09-regras-repositorio.md:
#   1. `main` é a branch default
#   2. `main` só muda por pull request
#   3. o PR só pode ser mergeado com o CI verde
#
# A configuração vive em .github/rulesets/main.json — este script apenas empurra o
# arquivo para a API. Editar o JSON e rodar de novo é o caminho para mudar as regras;
# mexer pela interface do GitHub faz o repositório divergir do que está versionado.
#
# Uso:  ./scripts/apply-repo-rules.sh [owner/repo]
# Requer: gh CLI autenticado com permissão de admin no repositório.

set -euo pipefail

REPO="${1:-FabioCarlesso/FightOssStreak}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RULESET_FILE="$ROOT/.github/rulesets/main.json"
RULESET_NAME="main protegida" # precisa bater com o campo "name" do JSON

if ! command -v gh > /dev/null 2>&1; then
  echo "erro: gh CLI não encontrado — https://cli.github.com" >&2
  exit 1
fi

if [ ! -f "$RULESET_FILE" ]; then
  echo "erro: $RULESET_FILE não existe" >&2
  exit 1
fi

echo "==> repositório: $REPO"

# 1. main como branch default. Idempotente: se já for main, a API aceita sem efeito.
echo "==> definindo branch default: main"
gh api -X PATCH "repos/$REPO" -f default_branch=main --silent

# 2 e 3. Ruleset. Rulesets não têm upsert por nome: é preciso descobrir se já existe
# para escolher entre POST (criar) e PUT (atualizar). Sem esse passo, rodar o script
# duas vezes criaria duas rulesets com o mesmo nome, ambas valendo.
echo "==> procurando ruleset existente: $RULESET_NAME"
RULESET_ID="$(
  gh api "repos/$REPO/rulesets" --jq ".[] | select(.name == \"$RULESET_NAME\") | .id" || true
)"

if [ -n "$RULESET_ID" ]; then
  echo "==> atualizando ruleset #$RULESET_ID"
  gh api -X PUT "repos/$REPO/rulesets/$RULESET_ID" --input "$RULESET_FILE" --silent
else
  echo "==> criando ruleset"
  gh api -X POST "repos/$REPO/rulesets" --input "$RULESET_FILE" --silent
fi

echo
echo "==> estado final"
gh api "repos/$REPO" --jq '"branch default: " + .default_branch'
gh api "repos/$REPO/rulesets" \
  --jq '.[] | "ruleset: \(.name) [\(.enforcement)] -> \(.target)"'

cat <<'TXT'

Pronto. Para conferir na prática, tente empurrar direto para main:

    git push origin main
    # esperado: rejeitado com "Changes must be made through a pull request"

Se o push passar, a ruleset não está valendo para o seu usuário — confira se
bypass_actors está vazio em .github/rulesets/main.json.
TXT
