# D42 — A faixa de tamanho do `concept` liga por conjunto de módulos curados, não por "sim/não" global — e M0/M1 entram reescritos juntos, no mesmo PR

## Justificativa

A D41 previu ligar `validateConceptLength` inteiro assim que M0 estivesse pronto, mas isso ignorava
que `validate()` roda sobre o currículo **inteiro** de uma vez: não existe hoje um "ligar só para
M0" sem M1–M8 também precisarem estar na faixa, porque a chamada não tinha como saber qual nó
pertence a um módulo já revisado. A correção foi dar ao validador essa noção:
`CurriculumValidator.CONCEPT_LENGTH_CURATED_MODULES` é um `Set<String>` de códigos de módulo (hoje
`{"M0", "M1"}`), e a faixa de 450–900 só é checada para nó cujo módulo está nesse conjunto — o resto
do currículo permanece livre, exatamente como antes da D41.

**Não é a allowlist de nó por nó que a D41 rejeitou**: o grão é o módulo, que é também o grão de
cada PR de conteúdo do épico #55/#58, e o desenho copia o que `CurriculumIntegrityTest` já fazia
informalmente para quiz (`hasCuratedQuiz`, M0–M3) e vídeo (`hasCuratedVideo`, M0–M1) — só que agora
do lado do validador de produção, não só do teste.

**M0 nunca teve PR próprio**: a PR #65 (a origem da D41) foi só infraestrutura, e a suposição de que
"M0 já estava pronto" era falsa — M0.2 seguia com 431 caracteres, abaixo do piso. Por isso este PR
reescreve M0 e M1 juntos (11 nós): é o menor conjunto que a guarda global consegue validar sem
quebrar, já que M0 sozinho não bastaria para ligar nada com o desenho da D41, e com o desenho por
conjunto de módulos os dois cabem no mesmo PR sem custo extra.

## Revisar quando

Superado pela D43: os 9 módulos entraram juntos, não um PR por vez como esta linha previa — e
`CONCEPT_LENGTH_CURATED_MODULES` **não** é removido ao cobrir todos eles, ao contrário do que esta
linha dizia. Ver D43

---

[Índice das decisões](../README.md)
