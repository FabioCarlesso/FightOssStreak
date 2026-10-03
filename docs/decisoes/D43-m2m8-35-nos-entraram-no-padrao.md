# D43 — M2–M8 (35 nós) entraram no padrão de três movimentos no mesmo PR de M0/M1, contra a recomendação da própria issue #58 de um PR por módulo — e sem fonte externa nova, só reestruturação do texto já existente

## Justificativa

Depois de M0/M1 (D42), o autor pediu explicitamente para os sete módulos restantes entrarem juntos,
"para validar tudo depois" — decisão dele, documentada aqui para não parecer que a diretriz de #58
("diff gigante não é lido") foi ignorada por conta própria. `CONCEPT_LENGTH_CURATED_MODULES` passou
a cobrir `{"M0".."M8"}` de uma vez; a guarda de tamanho passa a valer para o currículo inteiro, e
não há mais módulo "livre" — só um módulo futuro (M9 em diante) nasceria fora do conjunto.

**A questão de fonte foi tratada à parte, e de propósito mais conservadora que M0/M1**: nenhum dos
35 nós tem vídeo canônico catalogado (`docs/conteudo/fontes.md`), então não havia fonte instrucional específica para
citar. A escolha foi **não introduzir nenhuma técnica, mecanismo ou erro comum que não estivesse já
no texto anterior** — cada conceito foi reestruturado nos três movimentos e expandido por paráfrase
do que já existia (inclusive reaproveitando o "erro comum" que várias perguntas de quiz já
explicitavam), sem pesquisa nova. Duas imprecisões técnicas apareceram e foram corrigidas antes do
commit: M7.1 (kimura) tinha ganhado a frase "controla... mesmo sem o estrangulamento" — kimura é
torção de ombro, não estrangulamento; e M4.6 (armlock da montada) citava "figura-quatro de M2.5",
mas M2.5 é o armlock reto da guarda (juji-gatame), sem figura-quatro — a figura-quatro é da kimura
(M7.1). Os dois foram pistas de que reescrever tecnicamente em lote, sem fonte para checar cada
frase, tem risco real de erro pedagógico — exatamente o que D40 existe para reduzir. Em
`docs/conteudo/fontes.md`, a tabela `nó → fontes consultadas` de M2–M8 passou a registrar
"reestruturado do texto já existente no currículo, sem fonte externa nova consultada" em vez de
deixar `—`: é honesto sobre a lacuna (não finge fonte que não existe) e ainda assim satisfaz o
critério de aceite de #58 de ter uma linha preenchida por nó.

## Revisar quando

Se um erro técnico for encontrado num nó de M2–M8 depois deste PR — sinal de que reestruturar em
lote sem fonte teve custo real, e a próxima rodada (se houver) deveria ir devagar o bastante para
checar cada afirmação contra uma fonte de verdade, não só contra o texto anterior

---

[Índice das decisões](../README.md)
