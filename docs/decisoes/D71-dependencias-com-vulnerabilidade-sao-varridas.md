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

**O resíduo conhecido é a cadeia do Expo SDK 57**: `@expo/cli`, `@expo/code-signing-certificates`,
`node-forge` e `zod`, que dão os 5 high, e `expo-router`, com `query-string` e
`decode-uri-component`, que dão os 3 moderate. Nada disso vai para o bundle do web nem passa pelo
backend. A correção é o SDK 58, e troca de SDK do Expo é feita à parte, porque é o SDK que fixa as
versões nativas e o Dependabot já ignora esses pacotes. O FOS-10, como foi auditado, cobre o
backend e o web, e esses dois estão limpos.

## Revisar quando

Se a aba Security acumular alerta sem dono, ou quando houver uma dependência com correção
publicada que ficou semanas parada, o scanner passou a ser ignorado. Aí a saída é tornar o job
bloqueante para severidade alta com correção disponível, e não acrescentar outro scanner. Na troca
para o Expo SDK 58, conferir que o resíduo acima sumiu do `npm audit`.

---

[Índice das decisões](../README.md)
