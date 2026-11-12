# D76 — Posição é entidade do currículo, e o texto dela identifica sem ensinar

## Justificativa

Saber reconhecer as posições (guardas, imobilizações, costas, quatro apoios) é requisito comum das
primeiras graduações, e é como a aula é lembrada: "hoje treinamos meia-guarda". O FOS não tinha o
conceito. A árvore é organizada por módulo, e vincular técnica a uma sessão do diário exigia digitar
o código do nó. Esse atrito cai no passo que alimenta o SRS. A #194 é a fase 2 de 4 do plano de
graduação (#193 a #196). Quatro escolhas carregam o desenho.

**(a) Posição é dado do currículo (D11), não código nem tabela de usuário.** As 15 posições vivem em
`curriculum/positions.json`, declarado no `modules.json`, e cada nó diz em `positions` de onde a
técnica parte. A ingestão projeta as duas coisas no banco (`curriculum_position`, `node_position`,
V21) e apaga posição que saiu do JSON. Isso é seguro porque nenhum dado de usuário aponta para
posição: o nó guarda o **código**, e o `CurriculumValidator` confere no build que todo código
existe. Posição sem técnica é estado válido: a tela diz "ainda não há técnica no currículo", e a
fase 3 (#195) preenche as lacunas.

**(b) `identify` diz o que se vê, nunca o que se faz (D1).** São uma ou duas frases de **como
reconhecer** a posição. "Um está deitado de costas com as pernas cruzadas em volta da cintura do
outro" serve; "cruze as pernas e puxe a gola" não serve. O validador cobra a faixa de 60 a 280
caracteres e um parágrafo só. O piso barra o rótulo que não descreve nada, e o teto é o ponto em
que a descrição começa a virar aula. Identificar é o limite: chegar na posição e o que fazer dali
continuam sendo aula, e o vídeo e o conceito do nó tratam do porquê. Como o `concept` (D41), a
ausência de imperativo é item de revisão humana do PR, não heurística do validador. O texto é
próprio, segue `docs/conteudo/fontes.md` e não vem de manual de academia.

**(c) Apelido é termo de busca, e termo de busca aponta para uma posição só.** A busca do diário
compara sem acento, sem caixa e com hífen valendo espaço (`searchKey`, em `shared/domain`,
espelhada no validador, D17). Por isso o validador recusa apelido repetido entre posições e apelido
igual ao nome de outra posição. "Lateral" levando a duas posições faria a busca devolver as duas
para quem pensou em uma. O apelido do nó só repete o que o título não diz ("armbar", "RNC"), porque
o título já é pesquisado.

**(d) As posições viajam dentro da árvore, não em rota própria.** `GET /api/curriculum/tree` ganhou
`positions`, e cada nó ganhou `positionCodes` e `aliases`. As duas telas que usam posição, o mapa
`/posicoes` e a busca de técnica do diário, precisam também dos nós e do status de cada um, que só
a árvore traz. Uma rota separada custaria uma segunda requisição a cada tela, e montar a lista
"nós desta posição" dos dois lados criaria uma segunda verdade. A busca é regra pura em
`shared/domain` (`searchTechniques`), para o app mobile usar a mesma quando ganhar a tela.

## Revisar quando

- Se aparecer pedido de quiz "que posição é essa?" ou de imagem de posição: depende de imagem, e
  imagem de posição tem o mesmo problema de direito que vídeo (D7). Decisão própria.
- Se um `identify` precisar passar do teto para fazer sentido: é sinal de que a posição está mal
  recortada (duas posições numa só) ou de que o texto está ensinando. Não suba o teto antes de
  descartar as duas hipóteses.
- Se a busca do diário continuar sem uso, ou seja, sessões seguindo sem técnica vinculada mesmo com
  a busca disponível: o atrito não era o `<select>`, e a fase 4 (#196) não deve apostar nela.

---

[Índice das decisões](../README.md)
