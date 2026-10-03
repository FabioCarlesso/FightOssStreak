# D44 — O quiz do nó vira banco rotativo: 8 perguntas por nó, 4 servidas por tentativa, escolhidas por rotação estável — e revisita a D27, não a derruba

## Justificativa

A #59 partiu de um defeito de produto: como o embaralhamento de alternativas (D16) é determinístico
por hash de `(pergunta, alternativa)`, quem revisa o mesmo nó pela sexta vez via as mesmas 4
perguntas, nas mesmas posições — a partir da terceira repetição, 100% deixava de significar
retenção. Ao mesmo tempo 21 dos 46 nós tinham `quiz: []` (M4–M8 inteiros), tirando o módulo inteiro
do ciclo de SRS (D15).

**A D27 continua de pé**: 4 é o número de perguntas de uma prova, porque com nota de corte 70 três
perguntas reprovam com um erro e quatro toleram um (75). O que muda é que 4 deixa de ser também o
número de perguntas que *existem* — passa a ser só o que é *servido*.

**Seleção por rotação, não por sorteio**: sorteio repetiria pergunta por acaso e deixaria parte do
banco sem nunca aparecer; a tentativa `n` (contada em `quiz_attempt`, que já grava uma linha por
submissão, inclusive reprovada e refeita — não foi preciso estado novo) serve a fatia que começa em
`(n × 4) mod tamanhoDoBanco`, dando a volta no fim — o que mistura fim e começo quando o banco não é
múltiplo de 4, comportamento desejado, não defeito.

**A chave de ordenação é o enunciado, não o id**: `CurriculumIngestionService.replaceQuizzes()`
apaga e recria toda pergunta a cada sincronização (D11), então ordenar por id embaralharia a rotação
a cada deploy — hash do enunciado é estável enquanto o enunciado for o mesmo, que é a definição
certa de "a mesma pergunta". A lógica de rotação e o finalizador splitmix64 do embaralhamento de D16
foram fatorados para `QuizRotation` e `SplitMix`, package-private em `dev.fos.service`,
reaproveitados por `QuizService.submit()` e `CurriculumQueryService.nodeDetail()`.

**A correção vale só para o conjunto servido**: `submit()` recalcula a mesma fatia para a tentativa
corrente e exige que as respostas cubram exatamente esse conjunto; divergência (duas abas, submissão
antiga) responde **409** (`QuizStaleException`, código `quiz_stale`) em vez de corrigir contra o
conjunto errado.

**`CurriculumValidator` passou a exigir 8 perguntas mínimas e enunciados distintos** para todo nó
com quiz não vazio — `quiz: []` continua aceito (D15). O mínimo de 8 já entrou como erro rígido, não
como aviso, porque a Fase 3 (escrever as perguntas que faltavam) entrou no mesmo PR: os 21 nós de
M4–M8 ganharam banco do zero e os 25 de M0–M3 foram completados de 3–5 para 8. Como M4–M8 não têm
fonte instrucional própria (D43), as perguntas novas desses módulos vieram do próprio `concept` do
nó — mesma base da D43, registrada em `docs/conteudo/fontes.md` — em vez de inventar uma fonte
técnica que não existe. `quizQuestionCount` do `NodeSummaryView` já era o tamanho do banco (contagem
em `quizCountsByNodeId`, não em perguntas servidas); só o Javadoc foi corrigido para dizer isso
explicitamente, porque antes desta issue os dois números coincidiam e ninguém precisava reparar na
diferença.

## Revisar quando

Se a #23 (gestão de conteúdo pelo app) trouxer id estável por pergunta — aí ele substitui o hash de
enunciado como chave de rotação, com vantagem: sobrevive a edição de texto que o hash não sobrevive.
Ou se aparecer demanda por repetição espaçada por pergunta (não por nó) — é produto diferente, fora
do escopo desta decisão

---

[Índice das decisões](../README.md)
