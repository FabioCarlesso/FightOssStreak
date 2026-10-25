# D71 — Dependências com vulnerabilidade conhecida são varridas pelo `osv-scanner`, sem travar merge

## Justificativa

A auditoria de outubro registrou o FOS-10 (Baixa · A06:2021 · CWE-1104): Spring Boot 3.4.1 fora do
suporte, `react-router` com open redirect por barra invertida, `vite`, `esbuild` e `vitest` com
aviso, e nenhum scanner olhando o Maven. O achado só apareceu como "suspeito" porque ninguém
varria as dependências Java. A correção foi feita em duas partes, como a #78 pedia.

**Backend** (#175): Spring Boot 4.1.2, que já traz a correção da CVE-2025-22228 (bcrypt acima de 72
bytes). A mitigação pelo `PasswordPolicy` continua, mas deixou de ser a única barreira.

**Web**: `react-router-dom` 7.18.4, `vite` 8, `vitest` 5 e `@vitejs/plugin-react` 6. O open
redirect não era explorável, porque nenhum destino de navegação vem do usuário, mas era o único
item que ia para produção. Na major do router só uma coisa muda para o código: `navigate()` pode
devolver `Promise`, e as quatro chamadas soltas passaram a ser marcadas com `void`. As rotas com
parâmetro (`/no/:code`, `/diario/:id`, `/confirmar-email/:token`, `/senha/redefinir/:token`) não
mudaram. `js-yaml`, `brace-expansion`, `undici` e `@redocly/openapi-core` foram atualizados um a um
com `npm update`. **Nunca com `npm audit fix`**: foi tentado, e ele resolveu a cadeia do Expo
instalando um `expo@44` aninhado dentro do SDK 57.

**Detecção contínua**: o workflow `osv` roda o `osv-scanner` sobre o `pom.xml` (com as transitivas
resolvidas pelo Maven Central) e o `package-lock.json`, em todo PR, em push para `main` e toda
semana. O resultado vai para a aba Security. O scanner foi escolhido em vez do
`dependency-check-maven` porque cobre os dois ecossistemas com uma ferramenta só e não baixa a base
da NVD a cada execução.

**Ele não trava merge** (`fail-on-vuln: false`) e não é required check. Uma CVE sem correção
publicada travaria quem não tem como resolvê-la, e um portão que fica vermelho por motivo alheio
ao PR ensina a ignorá-lo. É o mesmo raciocínio que deixou o `codeql` fora da ruleset. Como o job
fica sempre verde, quem lê o resultado é a aba Security.

**Expo SDK 58 no mesmo PR.** O `npm audit` acusava mais 8 itens (5 high e 3 moderate), todos da
cadeia do Expo SDK 57. A troca para o SDK 58 (React 19.3, React Native 0.88, `expo-router` 58)
resolveu os 3 moderate (`expo-router` → `query-string` → `decode-uri-component`), que iam para o
app. A única mudança de código foi a ref do `ScrollView` no `CartaoStreak`: no RN 0.88 o
`ScrollView` virou componente de função, e a ref passou a ser `ScrollViewInstance`.

**O resíduo conhecido são os 5 high, todos dentro do `@expo/cli`**, que existe só na máquina de
quem desenvolve e no CI e não vai para o bundle do app nem para o backend:
- `node-forge`, usado pelo `@expo/code-signing-certificates` para assinar EAS Update, não tem
  versão corrigida publicada;
- `zod` só foi corrigido na 4.6.6, e o `@expo/cli` 58 fixa `zod ^3`. Forçar a 4 por `overrides` não
  serve, porque ela muda a API importada pela CLI.

O que fecha esses itens é o Expo publicar uma CLI com as dependências corrigidas, e o `osv`
continua apontando os dois até lá.

## Revisar quando

Se a aba Security acumular alerta sem dono, ou quando houver uma dependência com correção
publicada que ficou semanas parada, o scanner passou a ser ignorado. Aí a saída é tornar o job
bloqueante para severidade alta com correção disponível, e não acrescentar outro scanner. Quando
o Expo publicar uma `@expo/cli` com `zod` 4 ou um `node-forge` corrigido, suba e confira que o
resíduo acima sumiu do `npm audit`.

---

[Índice das decisões](../README.md)
