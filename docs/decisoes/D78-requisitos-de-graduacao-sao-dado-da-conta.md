# D78 — Requisito de graduação é dado da conta, ligado à árvore, e o "Continuar" prefere a checklist sem reordenar o SRS

## Justificativa

Cada academia tem uma lista de requisitos por grau, e essa lista é a resposta mais concreta à
pergunta que o app existe para responder: o que drillar agora? Só que ela costuma ser material
protegido, e o repositório é público. A #196 é a fase 4 de 4 do plano de graduação (#193 a #196).
Quatro escolhas carregam o desenho.

**(a) A lista é dado da conta, nunca currículo (D11).** A pessoa cola o texto que a academia
entregou, um requisito por linha, e ele vira checklist privada (`graduation_requirement`). O app
não traz lista de academia nenhuma, nem como exemplo: teste, fixture e documentação usam listas
fictícias. A checklist é uma por conta, como a meta da D75, porque o FOS acompanha o próximo grau e
não um histórico. Colar de novo **acrescenta** ao fim, e não substitui, porque colar por engano não
pode apagar vínculo que a pessoa já confirmou; apagar a checklist inteira é um gesto separado, com
segundo toque. A lista fica só na conta, `DELETE /api/me` a leva junto, e a coleta da D50 e o
painel da D52 nunca a leem. Há teste que varre a resposta do painel atrás dela.

**(b) A ligação com a árvore é sugerida pela busca e confirmada pela pessoa.** A sugestão sai de
`suggestTechniques`, em `shared/domain`, que pontua as palavras do requisito contra título, apelido
e posição dos nós (D76). Ela é generosa de propósito: uma palavra significativa em comum basta, e a
ordem separa o provável do possível. Por isso vem **desmarcada**, e o vínculo só é gravado quando a
pessoa confirma. Requisito sem técnica correspondente (comportamento, tempo de faixa) fica sem nó e
se marca à mão.

**(c) O status é derivado do progresso gravado, nunca gravado.** Com nós ligados, o requisito está
concluído quando todos os nós estão `COMPLETED`, em andamento quando algum foi drillado ou concluído,
e pendente nos outros casos. A regra lê o progresso **gravado**, e não o status exibido: um nó
drillado pelo diário mas ainda travado na árvore aparece como `LOCKED`, e mesmo assim foi treinado.
Concluir um nó no quiz ou pelo drill muda o requisito sem nenhuma ação na checklist, pela mesma
lógica do streak e da contagem de aulas (D55, D75). A marcação à mão só vale para requisito sem nó,
e é apagada quando ele ganha nós: guardada debaixo do vínculo, voltaria a valer sozinha no dia em
que a pessoa desligasse os nós.
A regra pura é `requirementStatus`, em `shared/domain`, espelhada no backend (D17).

**(d) Na home, a checklist marca e prefere, mas não reordena.** Na agenda de revisão, os nós
ligados ganham a marca "próximo grau", e a ordem da fila continua a do SM-2. Sem revisão vencida, o
atalho *Continuar* (`nextNodeToResume`) olha primeiro os nós da checklist que estejam em andamento
ou liberados, e só depois a árvore inteira. Reordenar a agenda pela graduação seria mudar a D56c, e
fica fora de escopo, porque exige decisão própria e medição antes.

## Revisar quando

- Se as revisões atendidas caírem abaixo dos 60% de `docs/produto/mvp-web.md` depois da checklist:
  a marca "próximo grau" está competindo com a agenda, e ela é a primeira coisa a sair.
- Se aparecer pedido de compartilhar a checklist entre contas ou de "trilhas de academia"
  públicas: isso é distribuir material de terceiro, e só cabe com autorização expressa da
  academia, em decisão própria.
- Se a pessoa precisar acompanhar mais de um grau ao mesmo tempo: a checklist única por conta
  deixa de servir, e a tabela ganha um grupo de verdade.
- Se a sugestão de técnica errar muito (vínculos desfeitos logo depois de confirmados): a
  pontuação de `suggestTechniques` precisa de outro critério, como apelido de requisito.

---

[Índice das decisões](../README.md)
