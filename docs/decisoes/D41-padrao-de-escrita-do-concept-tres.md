# D41 — Padrão de escrita do `concept` (três movimentos) entra por partes: doc e parágrafo já, faixa de tamanho módulo a módulo

## Justificativa

A issue #58 pediu quatro coisas juntas — documentar o padrão de três movimentos (problema,
mecanismo, erro comum) no README do currículo, deixar `NodePage.tsx` renderizar linha em branco como
parágrafo, e o `CurriculumValidator` recusar conceito fora de 450–900 caracteres ou com mais de 3
parágrafos — mas a própria issue também pede que a reescrita dos 46 conceitos vá em um PR por
módulo, não um PR só. Ligar a guarda de tamanho **agora** reprovaria o build de verdade: hoje só
M0.1, M0.3, M0.5 e M1.1 caem dentro de 450–900; o resto de M0/M1 e todo M2–M8 ainda tem o texto
curto anterior ao padrão (a issue já registrava a média de M2–M8 abaixo de 450). A guarda de
**parágrafos** (máx. 3) entrou ativa em `validate()` sem risco — nenhum conceito hoje usa linha em
branco, então não há currículo existente para quebrar. A de **tamanho** ficou escrita, com
`CurriculumValidator.validateConceptLength` público a nível de pacote e teste próprio em
`CurriculumIntegrityTest`, mas **não chamada** por `validate()`: falso-positivo (o método existe,
testado, documentado) é preferível a duas alternativas piores — travar o CI do currículo real sem
ninguém ter reescrito nada, ou inventar uma allowlist de códigos isentos que encolhe PR a PR, dívida
técnica que este projeto não tem em nenhum outro lugar do validador. Fica para o primeiro PR de
conteúdo (M0, por ser o de menor ajuste) ligar a chamada — nesse ponto M0 inteiro já estará na
faixa, e cada módulo seguinte entra reescrito antes de a guarda cobri-lo.

## Revisar quando

Quando o primeiro PR de conteúdo (M0) for aberto — é ele que liga `validateConceptLength` dentro de
`validate()`. Se isso não acontecer em algumas semanas, o risco vira o mesmo da D40: guarda escrita
e esquecida

---

[Índice das decisões](../README.md)
