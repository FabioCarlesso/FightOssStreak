# Log de Decisões

Registro de decisões com justificativa, para que o "porquê" não se perca — e para que reversões futuras sejam conscientes.

| # | Decisão | Justificativa | Revisar quando |
|---|---|---|---|
| D1 | **Posicionamento: revisão, não ensino** | Jiu-jitsu é habilidade motora; quiz não constrói habilidade motora. O problema real é esquecer na sexta o que se aprendeu na segunda | — (decisão estruturante) |
| D2 | **React (web) → React Native (mobile)** | Um código-base, reaproveita TypeScript, curva menor que Flutter/nativo | Se surgir necessidade de UI com performance de jogo |
| D3 | **Backend Spring Boot + Postgres** | Stack dominada; sem motivo para experimentar aqui | — |
| D4 | **Web-first, mobile depois** | Elimina fricção de loja na fase de validação; custo zero | Após critérios de sucesso do MVP serem atingidos |
| D5 | **Monorepo** | Projeto solo; `shared/` justifica o formato | Se houver mais de um contribuidor ativo |
| D6 | **Somente Gi nesta versão** | Reduz escopo do currículo pela metade; No-Gi tem árvore parcialmente distinta | Depois do MVP validado |
| D7 | **Vídeo via embed do YouTube** | Elimina custo de CDN, produção e o problema de autoridade técnica | Se o embed limitar a experiência ou se houver conteúdo próprio |
| D8 | **Sem monetização; licença MIT** | Projeto pessoal; não há interesse comercial no momento | Se houver adoção fora do círculo pessoal |
| D9 | **Sem login no MVP** | Usuário único (o autor). Login traz obrigação de deleção de conta (Apple) e complexidade de schema | Ao abrir para outros usuários |
| D10 | **Sobrevivência antes de ataque na árvore** | Versão anterior ensinava armlock antes de fuga de montada — invertia a pedagogia real de faixa branca | Na validação com faixa-preta |
| D11 | **Currículo como dado versionado, não código** | Alterar a árvore vira PR revisável; facilita a futura validação por um graduado | — |
| D12 | **Validação com faixa-preta adiada** | Uso é pessoal; validar antes de existir produto é prematuro | Antes de qualquer distribuição pública |
| D13 | **`unlockRule` por nó: `ALL` (padrão) ou `ANY`** | `04` descreve o Módulo 4 como "pré-requisito: M3.2 **ou** M3.3" — semântica de OU que o modelo "todos os pré-requisitos concluídos" de `01` não expressa. Em vez de duplicar nós ou inventar arestas falsas, o nó declara como combinar seus pré-requisitos | Se aparecer regra mais complexa que ALL/ANY — aí é sinal de que o currículo precisa de outra modelagem, não de mais um enum |
| D14 | **O currículo tem 46 nós, não 43** | `04` afirma "Total: 43 nós", mas a soma das tabelas do próprio documento dá 46 (5+6+7+7+6+4+4+4+3). As tabelas são o conteúdo real; o total era um erro de contagem. O código segue as tabelas e há teste travando a contagem por módulo | — (corrigido) |
| D15 | **Nó sem quiz é concluído pelo registro de drill** | A curadoria de quiz é incremental (M0 e M1 primeiro). Se conclusão dependesse só de quiz, os 35 nós sem quiz travariam a árvore inteira e o app seria inutilizável além do M1. Onde há quiz, ele continua sendo o que conclui | Quando todos os nós tiverem quiz escrito |
| D16 | **Alternativas do quiz são embaralhadas ao servir** | No JSON versionado a alternativa correta é escrita em primeiro lugar — é o que torna o currículo legível em um PR. Servir nessa ordem entregaria o gabarito. A ordem é embaralhada por hash de (pergunta, alternativa): estável entre requisições, sem relação com a posição original | — |
| D17 | **Regras puras duplicadas em Java e TypeScript** | `03` pede `shared/domain` com streak, SRS e desbloqueio para reaproveitar em mobile; o backend precisa das mesmas regras porque é quem persiste. O backend é a fonte da verdade; o TS serve a preview otimista na UI. Os casos de teste de SRS são espelhados nos dois lados, com valores fixados, para que divergência quebre o build | Se a duplicação passar a divergir com frequência — aí vale mover o cálculo para um só lado e aceitar o round-trip |
| D18 | **`main` protegida: default fixa, só muda por PR, merge só com CI verde** | Projeto solo tende a virar push direto em `main`, e aí o CI vira relatório em vez de portão — quebra chega em `main` e o histórico não tem ponto de revisão. A ruleset é declarada em `.github/rulesets/main.json` e aplicada por `scripts/apply-repo-rules.sh`, porque configuração de servidor sem versionamento não tem histórico nem forma de restaurar. Sem exigir aprovação humana (`required_approving_review_count: 0`): ninguém aprova o próprio PR, e com um contribuidor só isso travaria todo merge. Detalhes em `09-regras-repositorio.md` | Ao entrar um segundo contribuidor — aí a aprovação passa a ser possível e vale exigir 1 |
| D19 | **CI sem filtro por caminho em pull request** | Workflow descartado por filtro de `paths` não reporta status algum, e required check que não reporta trava o PR em "waiting for status" para sempre — um PR só de `docs/` nunca mergearia. O filtro fica no `push` para `main`, onde nada depende do resultado. Custo: os dois jobs rodam em todo PR | Se o tempo de CI incomodar — aí a saída é detecção de mudança em job, com job pulado por `if:` (job pulado reporta sucesso; workflow filtrado não reporta nada) |
| D20 | **Aderência ao SRS é gravada no momento do drill, não inferida depois** | Duas das quatro métricas de `05` não eram calculáveis: `srs_review.next_review_on` é sobrescrito pelo próprio drill, então "o nó estava vencido?" desaparece no instante do registro, e `user_progress.last_quiz_score` guarda só a última nota, tornando um quiz refeito indistinguível de um respondido uma vez. `drill_log` passou a carregar `was_due`/`due_on` e as submissões viraram log em `quiz_attempt`. O denominador da aderência soma o atendido com o que segue vencido dentro da janela; janela sem nada agendado responde "sem agenda" em vez de 0%, porque não houve sugestão a ignorar | Se a aderência passar a ser lida como nota de desempenho em vez de sinal sobre a mecânica — aí o problema é de leitura, não de cálculo |

## Política de uso de vídeo (D7) — limites

**Permitido:** incorporar (embed) vídeos públicos usando o player oficial do YouTube. O criador mantém visualizações e monetização; é o uso previsto pela plataforma.

**Não fazer:**
- Baixar e re-hospedar vídeos de terceiros
- Extrair trechos e recortar em clipes próprios
- Remover ou encobrir a marca do player
- Incorporar vídeos marcados como não-incorporáveis pelo autor

**Boa prática:** creditar canal e autor visivelmente em cada nó. Além de correto, é o que preserva a relação caso o projeto cresça.

## Riscos conhecidos em aberto

| Risco | Impacto | Estado |
|---|---|---|
| Currículo montado sem revisão de graduado | Pode ensinar ordem ou conceito errado | Aceito conscientemente enquanto o uso for pessoal (D12) |
| Vídeos do YouTube saem do ar / ficam privados | Nós ficam sem referência | Não mitigado — considerar checagem periódica de disponibilidade |
| Gamificação sustentar-se sozinha sem gerar aprendizado | Produto vira streak vazio | Coberto pelo critério de falha em `05-mvp-web-plano.md` |
| App ser visto como wrapper de YouTube na review da Apple | Rejeição | Fase futura; mitigação em `02` |
