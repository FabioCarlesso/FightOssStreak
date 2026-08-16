# FightOssStreak (FOS)

Ferramenta pessoal de **revisão e retenção** do que é aprendido no tatame, com mecânicas de gamificação inspiradas no Duolingo: lições curtas, quiz conceitual, progressão em árvore de currículo e streaks diários.

## Posicionamento (importante)

**O FOS não ensina jiu-jitsu.** Jiu-jitsu é habilidade motora/procedural — aprende-se no tatame, com professor, drilando e rolando. Um quiz não constrói habilidade motora.

O que o FOS faz é resolver o problema real de quem treina: **você aprende três técnicas na aula de segunda e na sexta já esqueceu duas.** O app organiza o que foi visto em aula, ajuda a reter o conceito e — via repetição espaçada — te diz **o que drillar hoje**.

Essa distinção define tudo:
- Não competimos com professor nem com academia — complementamos
- Não precisamos de autoridade técnica para funcionar, porque o conteúdo é curadoria e organização, não instrução original
- O diferencial contra FlowRoll/BJJ Notes (diários de treino) e contra o YouTube (conteúdo solto) é justamente a **camada de retenção estruturada**

## Motivação
- Não existe hoje uma ferramenta que combine currículo estruturado em árvore + repetição espaçada + gamificação para BJJ.
- Apps existentes (FlowRoll, Jiujify, BJJ Notes, Rollbook) cobrem streak/XP ou diário de treino, mas sem currículo nem retenção.
- BJJ Mental Models ensina conceitos, mas sem gamificação nem progressão.

## Escopo inicial
- **Uso pessoal** — o primeiro (e por enquanto único) usuário é o próprio autor, para organizar o próprio aprendizado
- **Somente Gi** — No-Gi fica para fase posterior (ver `07-decisoes.md`)
- **Vídeo via embed do YouTube** — sem hospedagem própria de mídia nesta fase
- **Sem monetização** — projeto pessoal/open source, possivelmente sob licença MIT
- Validação do currículo com um faixa-preta acontece quando o produto amadurecer, não antes do MVP

## Nome
**FightOssStreak (FOS)**

## Documentos deste planejamento
1. `01-stack-tecnica.md` — decisões de stack e complexidade por camada
2. `02-publicacao-ios-desafios.md` — desafios de publicação iOS
3. `03-estrutura-projeto.md` — estrutura de monorepo
4. `04-arvore-curriculo-bjj.md` — árvore de currículo (módulos e pré-requisitos)
5. `05-mvp-web-plano.md` — plano de validação via app web, com critérios de sucesso
6. `06-disclaimer-responsabilidade.md` — textos de aviso de responsabilidade e caráter instrucional
7. `07-decisoes.md` — log de decisões com data e justificativa

## Status do planejamento
- [x] Posicionamento definido (revisão/retenção, não ensino)
- [x] Stack decidida (React/RN + Spring Boot + Postgres)
- [x] Estratégia de validação decidida (web-first, uso pessoal primeiro)
- [x] Estrutura de monorepo esboçada
- [x] Árvore de currículo esboçada (9 módulos, sobrevivência primeiro)
- [x] Nome definido
- [x] Disclaimers redigidos
- [x] Critérios de sucesso do MVP definidos
- [x] Schema de banco de dados — migrations em `backend/src/main/resources/db/migration/`
- [x] Estrutura de telas do MVP web — home (streak + agenda), árvore, detalhe do nó
- [x] Setup do repositório e workspaces — monorepo npm + Maven, CI path-filtered
- [x] Currículo transcrito como dado versionado (46 nós, ver D14)
- [x] Quiz conceitual escrito para M0 e M1 (11 nós)
- [x] Medição dos critérios de sucesso instrumentada (`/progresso`, ver D20)
- [ ] Quiz conceitual dos módulos M2–M8
- [ ] Escolha e catalogação dos vídeos do YouTube por nó *(nenhum vídeo catalogado ainda)*
- [ ] Uso real por 30 dias para avaliar os critérios de sucesso de `05`
