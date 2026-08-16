# Árvore de Currículo — FightOssStreak

Estrutura em módulos com pré-requisitos, para desbloqueio progressivo (skill tree). Cada nó é uma unidade de **revisão** do que foi visto no tatame: conceito curto + vídeo de referência (YouTube) + quiz conceitual + registro de drill.

> **Escopo desta versão:** somente **Gi**. No-Gi fica para versão futura (ver `07-decisoes.md`).

## Princípio de ordenação: sobrevivência antes de ataque

A versão anterior desta árvore ensinava armlock e triângulo antes de fuga de montada — o erro clássico de currículo de BJJ. Corrigido: **o Módulo 1 inteiro é sobrevivência e fuga**, antes de qualquer finalização. Faixa branca é sobre não ser finalizado; finalizar vem depois.

## Como ler
- **Nó** = uma unidade de revisão
- **Pré-requisito** = nó(s) que precisam estar concluídos para desbloquear
- **Faixa** = nível em que a técnica costuma ser introduzida — calibra dificuldade, não trava acesso
- IDs `M{módulo}.{nó}` são as chaves usadas no banco (`node.code`)

---

## Módulo 0 — Fundamentos e Segurança
*Sem pré-requisitos. Porta de entrada.*

| ID | Nó | Faixa |
|---|---|---|
| M0.1 | Etiqueta de tatame e hierarquia | Branca |
| M0.2 | Quedas seguras (breakfalls / ukemi) | Branca |
| M0.3 | Como tocar (tap) e reconhecer perigo | Branca |
| M0.4 | Posturas base: quadrupedia, base de guarda, postura em pé | Branca |
| M0.5 | Pegadas de gi: manga, gola, calça | Branca |

*M0.5 foi movida do antigo módulo de quedas — pegada é pré-requisito conceitual de quase tudo no gi, não de takedown.*

**Desbloqueia:** M1

---

## Módulo 1 — Sobrevivência e Fuga
*Pré-requisito: M0.3, M0.4. **Este módulo vem antes de qualquer ataque.***

| ID | Nó | Pré-req. interno | Faixa |
|---|---|---|---|
| M1.1 | Sobrevivência sob pressão: postura defensiva, proteger o pescoço, respirar | — | Branca |
| M1.2 | Shrimping / fuga de quadril (movimento base) | M1.1 | Branca |
| M1.3 | Fuga de 100kg (side control) | M1.2 | Branca |
| M1.4 | Fuga de montada: upa (bridge) e elbow escape | M1.2 | Branca |
| M1.5 | Recuperação de guarda | M1.3, M1.4 | Branca |
| M1.6 | Fuga de costas e defesa de mata-leão | M1.1 | Branca |

*M1.1 é literalmente a primeira aula de qualquer academia e estava ausente da versão anterior.*

**Desbloqueia:** M2, M6

---

## Módulo 2 — Guarda Fechada
*Pré-requisito: M1.5*

| ID | Nó | Pré-req. interno | Faixa |
|---|---|---|---|
| M2.1 | Guarda fechada: postura, controle e quebra de postura | — | Branca |
| M2.2 | Raspagem de tesoura (scissor sweep) | M2.1 | Branca |
| M2.3 | Raspagem pêndulo (hip bump / flower) | M2.1 | Branca |
| M2.4 | Estrangulamento cruzado (cross collar) | M2.1 | Branca |
| M2.5 | Armlock da guarda (juji-gatame) | M2.1 | Branca |
| M2.6 | Triângulo | M2.1 | Branca |
| M2.7 | Omoplata | M2.6 | Azul |

*M2.4 subiu do antigo módulo de finalizações: cross collar é ataque fundamental da guarda fechada, não conteúdo avançado.*

**Desbloqueia:** M3

---

## Módulo 3 — Guarda Aberta e Passagem
*Pré-requisito: M2.1*

| ID | Nó | Pré-req. interno | Faixa |
|---|---|---|---|
| M3.1 | Guarda aberta: grips, distância e enquadramento | M0.5 | Branca |
| M3.2 | Passagem torreando (toreando) | M3.1 | Branca |
| M3.3 | Passagem por cima (over-under / smash) | M3.1 | Branca |
| M3.4 | Guarda de gancho (butterfly) | M3.1 | Branca |
| M3.5 | Raspagem de butterfly | M3.4 | Branca |
| M3.6 | Meia-guarda: conceito e enquadramento | M3.1 | Branca |
| M3.7 | Raspagem de meia-guarda (old school) | M3.6 | Branca |

**Desbloqueia:** M4

---

## Módulo 4 — Controle Superior
*Pré-requisito: M3.2 ou M3.3 (qualquer passagem)*

| ID | Nó | Pré-req. interno | Faixa |
|---|---|---|---|
| M4.1 | 100kg (side control): manutenção e pressão | — | Branca |
| M4.2 | Joelho na barriga (knee on belly) | M4.1 | Branca |
| M4.3 | Transição 100kg → montada | M4.1 | Branca |
| M4.4 | Montada: manutenção e base | M4.3 | Branca |
| M4.5 | Americana e Ezequiel | M4.1, M4.4 | Branca |
| M4.6 | Armlock da montada | M4.4 | Branca |

**Desbloqueia:** M5, M7

---

## Módulo 5 — Costas (Back Control)
*Pré-requisito: M4.4*

| ID | Nó | Pré-req. interno | Faixa |
|---|---|---|---|
| M5.1 | Pegar as costas a partir da montada | — | Azul |
| M5.2 | Controle de costas: hooks e seat belt | M5.1 | Azul |
| M5.3 | Mata-leão (rear naked choke) | M5.2 | Azul |
| M5.4 | Manutenção de costas contra defesa | M5.2 | Azul |

**Desbloqueia:** M7

---

## Módulo 6 — Quedas e Entrada em Combate
*Pré-requisito: M0.2, M0.5. Trilha paralela — pode ser cursada cedo.*

| ID | Nó | Pré-req. interno | Faixa |
|---|---|---|---|
| M6.1 | Puxar guarda com segurança | — | Branca |
| M6.2 | Queda de mão única (single leg) | — | Branca |
| M6.3 | Queda de dois pés (double leg) | — | Branca |
| M6.4 | Sprawl e defesa de queda | M6.2, M6.3 | Azul |

**Desbloqueia:** M7.2 (front headlock nasce do sprawl)

---

## Módulo 7 — Finalizações Encadeadas
*Pré-requisito: conclusão de M2, M4 e M5*

| ID | Nó | Pré-req. interno | Faixa |
|---|---|---|---|
| M7.1 | Kimura: da guarda, do 100kg e das costas | M2.1, M4.1 | Azul |
| M7.2 | Front headlock e guilhotina | **M6.4** | Azul |
| M7.3 | Defesa de guilhotina | M7.2 | Azul |
| M7.4 | Encadeamento triângulo → armlock → omoplata | M2.5, M2.6, M2.7 | Azul |

*Correção: guilhotina agora depende de front headlock/sprawl (M6.4), não de guarda aberta — que era um pré-requisito arbitrário.*

**Desbloqueia:** M8

---

## Módulo 8 — Jogo Integrado
*Pré-requisito: conclusão de M7*

| ID | Nó | Faixa |
|---|---|---|
| M8.1 | Encadeamento de raspagens (sweep chains) | Roxa |
| M8.2 | Leitura de pressão e antecipação de reação | Roxa |
| M8.3 | Construção de jogo A-B-C (game plan pessoal) | Roxa |

---

## Mapeamento por faixa
- **Branca** — M0, M1, M2 (exceto M2.7), M3, M4, M6.1–6.3 → *sobrevivência, fundamentos e primeiras finalizações*
- **Azul** — M2.7, M5, M6.4, M7 → *expansão de jogo e encadeamento*
- **Roxa+** — M8 → *integração e personalização*

## Estrutura de cada nó no app
1. **Conceito** (texto curto — o *porquê* da posição, não o passo a passo)
2. **Vídeo de referência** (embed do YouTube — ver política em `07-decisoes.md`)
3. **Quiz conceitual** (3–5 perguntas testando compreensão: *quando usar*, *qual o erro comum*, *o que o oponente faz*)
4. **Registro de drill** ("treinei isso hoje") → alimenta streak e agenda o SRS

## Notas de design
- **SRS não revisa teoria, agenda drill.** Esse é o diferencial: o app abre e diz *"hoje revise fuga de montada e raspagem de tesoura"*, priorizando nós antigos e os de quiz com erro.
- Nós com múltiplos pré-requisitos (M1.5, M7.1, M7.4) são bons **checkpoints** de revisão consolidada.
- Total: **43 nós**. Cobre faixa branca com folga e entra em azul. É deliberadamente enxuto — currículo real de faixa branca é maior, e a expansão deve vir do uso, não do planejamento.
- O currículo é dado versionado (ver `03-estrutura-projeto.md`): alterar a árvore é um PR revisável, não uma migração de banco.

## Pendências de validação
Esta árvore foi montada a partir de estrutura pedagógica comum de BJJ, **não por um faixa-preta**. Antes de qualquer distribuição além do uso pessoal, precisa de revisão por alguém graduado — especialmente ordenação dentro de M1 e M3, e a escolha de quais finalizações são "faixa branca".
