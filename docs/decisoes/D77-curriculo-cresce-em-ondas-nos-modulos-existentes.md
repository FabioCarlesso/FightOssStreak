# D77 — O currículo cresce em ondas, dentro dos módulos existentes, e a contagem de nós é livro-caixa

## Justificativa

A D14 fixou o currículo em 46 nós e disse que a expansão deveria vir do uso, e não do
planejamento. O uso veio. Comparando as técnicas das aulas com a árvore, guarda X, aranha, De La
Riva, norte-sul, crucifixo e quatro apoios não tinham nó. O mapa de posições da D76 deixou isso
visível: cinco posições apareciam com "ainda não há técnica no currículo". Sem nó, a sessão do
diário fica sem vínculo e o SRS não agenda nada. A #195 corrige isso, e quatro escolhas carregam
o desenho.

**(a) Os nós novos entram nos módulos que já existem, não num módulo novo.** Guarda X, aranha e De
La Riva são guardas de distância, ou seja, guarda aberta (M3). Norte-sul e crucifixo são controle
por cima (M4). Atacar os quatro apoios é chegar às costas (M5), e defendê-los é sobrevivência (M1).
Um módulo "guardas de distância" separaria do M3 justamente o que depende dele. E todo módulo
existente já está na lista de conteúdo curado do validador (`CONCEPT_LENGTH_CURATED_MODULES`), de
modo que os nós novos entram valendo a faixa de 450–900 caracteres desde o primeiro dia. Módulo
novo só se justifica quando o grupo de nós não depende do módulo de origem, e esse não é o caso.

**(b) A contagem de nós por módulo é livro-caixa, não teto.** O teste que trava a contagem
continua existindo, porque é o que obriga `docs/produto/curriculo.md` a acompanhar o JSON. Mas a
expectativa de que ele não mude acabou: cada onda atualiza o número, o teste e as tabelas no mesmo
PR. A primeira onda levou o total de 46 a 56.

**(c) Pré-requisito segue a pedagogia, não a urgência da aula.** Cada nó novo depende do nó-base
da sua família: guarda X depende da guarda de gancho (M3.4); aranha e De La Riva, da guarda aberta
(M3.1); a passagem contra a aranha, da passagem torreando (M3.2); norte-sul e crucifixo, do 100kg
(M4.1); as costas a partir dos quatro apoios, do controle de costas (M5.2). Isso não trava o
registro do que aconteceu na aula: o diário vincula técnica bloqueada (D56), e o bloqueio é só de
apresentação (D31). A régua de "sobrevivência antes de ataque" (D10) vale também para as
finalizações novas, e há teste que confere.

**(d) A faixa é do nó, não do módulo.** M5 é um módulo de faixa azul, mas pegar as costas de quem
está nos quatro apoios aparece desde as primeiras aulas. Por isso M5.5 é `BRANCA`.

O texto dos nós novos foi escrito com assistência de IA e sem fonte externa consultada, e está
registrado assim em `docs/conteudo/fontes-por-no.md`. Isso amplia o risco que a D12 já aceitou
(currículo sem revisão de graduado) e não o fecha. A revisão humana do PR, que confere a ausência
de passo a passo exigida pela D1, é a primeira barreira, e não a última.

## Revisar quando

- Se um grupo de nós novos não depender de nenhum nó do módulo de origem, ele é candidato a
  módulo próprio, e essa decisão volta a ser discutida.
- Se M3 passar de uns 15 nós, a árvore fica longa demais para ler na tela do celular. Aí vale
  reabrir a divisão de M3 em guardas de contato e guardas de distância.
- Quando um graduado revisar os nós da #195 (D12), atualizar as linhas deles em
  `docs/conteudo/fontes-por-no.md`.

---

[Índice das decisões](../README.md)
