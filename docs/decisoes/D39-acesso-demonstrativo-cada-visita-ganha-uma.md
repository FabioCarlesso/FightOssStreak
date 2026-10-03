# D39 — Acesso demonstrativo: cada visita ganha uma cópia descartável de uma conta-modelo, e não uma conta de demonstração compartilhada

## Justificativa

Depois da D37 havia exatamente dois jeitos de entrar, e os dois pressupõem que a pessoa **quer uma
conta**. Faltava o degrau anterior: quem recebe o link cai na landing (D33), vê quatro prints e, se
clicar, encontra um portão — e print não mostra o que o app faz, porque o produto é o **loop de
revisão** (responder, registrar, ver a revisão ser reagendada, ver o streak andar), que não cabe em
imagem.

**A conta compartilhada foi considerada e descartada**, e por defeitos, não por gosto: com escrita
real, o primeiro visitante conclui nós e o segundo vê a sobra em vez da árvore curada; a anotação
fixada é texto livre visível para o próximo; o streak passa a ser o de quem visitou por último — e
streak é metade do nome do app; `DELETE /api/me` apaga a demonstração inteira; e um link permanente
é credencial que não expira nem se revoga, o oposto do que a D37 decidiu ao fazer o magic link valer
15 minutos. Consertar isso pede reset agendado, e reset pede uma fonte de verdade para restaurar —
ou seja, **já contém o desenho da cópia, só que pior**.

**A conta-modelo é curada pelo app, não por script**: o autor entra nela pelos meios normais,
conclui nós, registra drills e escreve as anotações que o visitante vai ler; por isso a configuração
é um e-mail (`fos.demo.template-email`) e não um id de linha do banco. E ela **nunca recebe
visitante**.

**O rebase de datas é o que faz a demonstração parecer viva**: as datas do progresso são absolutas,
e copiadas cruas uma conta-modelo curada há dois meses entrega uma agenda com tudo vencido — o
oposto de "já carregado". Todas as datas andam pelo mesmo delta, ancorado na última atividade do
modelo, o que preserva a distância entre os eventos em vez das datas.

**Isto abre exceção à D37, e a exceção precisa estar escrita**: a conta nasce `APROVADO` sem
aprovação nenhuma. O que a sustenta são quatro coisas juntas — ela é descartável, não tem identidade
de ninguém (`provider = demo`, subject sorteado, e-mail nenhum), não tem poder além do próprio
progresso (`isOwner` responde falso para conta de demonstração, mesmo quando o modelo está em
`owner-emails`) e tem prazo de duas horas. Tirar qualquer uma delas reabre a decisão.

**A varredura é preguiçosa**, no momento de criar uma demonstração nova, e não `@Scheduled`: o
agendador que existe (D38) só é registrado quando há credencial de envio, e pendurar a limpeza nele
amarraria a demonstração a uma configuração que nada tem a ver com ela. O caso ruim é um punhado de
contas vencidas paradas enquanto ninguém abre outra — não é vazamento.

**A landing passou a falar com a API, e a regra dela ficou melhor definida**: o que a D33 proíbe é a
página *depender* da API para existir (era o `DisclaimerGate` que a derrubava com backend frio), não
trocar uma palavra com ela. A pergunta "este ambiente tem demonstração?" não bloqueia nada — sem
resposta, a página aparece inteira, só sem o botão.

**Duas coisas se chamam demonstração no projeto**: o modo da D31 é inspeção do autor *dentro* do app
e **não grava nada**; esta é uma sessão de verdade *antes* do portão e grava tudo, numa conta que
não é de ninguém. Os nomes na UI e no código dizem qual é qual, e as faixas de topo são visualmente
distintas.

**As métricas não precisam excluir demonstração**: `MvpMetricsService` recorta tudo por `userId`,
então conta de demonstração não contamina os números do autor — registrado aqui para que ninguém
"conserte" depois o que não está quebrado. Três detalhes que só apareceram revisando: **sessão de
gente de verdade não é trocada** por uma demonstração (o rodapé do app leva à apresentação, e um
clique ali trocaria a sessão do autor por uma cópia da própria conta dele — responde **409**);
**demonstração não marca "já entrou no app"**, senão a landing sumiria da raiz justamente para quem
ainda está decidindo se pede acesso; e **o que o autor escrever na conta-modelo é publicado**,
porque anotação fixada e nota de drill vão na cópia — é a única superfície do app onde texto do
autor sai sem um ato de publicar

## Revisar quando

Se a demonstração passar a ser o caminho de entrada principal em vez do degrau anterior ao portão;
se o teto de simultâneas ou o prazo virarem apertados na prática (aí o que se revisa são os números,
não o desenho); ou se alguém pedir "guarde meu progresso" ao fim da demonstração — converter
descartável em conta de verdade é a continuação natural, e é outra decisão

---

[Índice das decisões](../README.md)
