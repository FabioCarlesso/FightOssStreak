# D31 — Modo demonstração: percorre o currículo inteiro, e não grava nada

## Justificativa

Revisar os 46 nós — conceito, vídeo, redação das perguntas — exigia passar no quiz de cada
pré-requisito na ordem: horas de clique para uma tarefa de curadoria, e com o efeito colateral de
concluir nós de verdade, sujando justamente `nodes_completed`, aderência ao SRS e streak, que são os
números que a `/progresso` existe para responder (D20). Saiu **só no front**: o bloqueio sempre foi
de apresentação — `UnlockService` deriva o `LOCKED`, mas `CurriculumQueryService.nodeDetail` já
entregava conceito, vídeo e quiz de qualquer nó, inclusive bloqueado. Nada de endpoint novo,
parâmetro de query, flag de configuração ou migração.

**A regra que define o modo é "a demonstração não grava"**: em nó que estaria bloqueado o quiz
aparece em leitura (perguntas e alternativas visíveis, envio desabilitado) e o registro de drill não
aparece; em nó genuinamente disponível tudo funciona como antes. Deixar gravar seria mais simples de
implementar e foi descartado por isso mesmo — destravaria nós e distorceria as métricas de 30 dias.
Como `QuizService.submit` e `DrillService.log` não checam bloqueio (só a existência do nó), essa
garantia mora inteira na UI e não tem rede de segurança no servidor; pôr uma é assunto de outra
issue, provavelmente depois do login (#24). O estado fica em `sessionStorage` e há faixa fixa no
topo com "Desligar" à mão, pelo mesmo motivo: um modo que ignora a progressão não pode ficar ligado
por semanas sem que se note. Os contadores da árvore continuam mostrando o progresso real. É
ferramenta de inspeção do dono do app, não recurso de produto — virar "libere tudo" contrariaria a
premissa de revisão progressiva (D1). Junto vieram dois testes de UI, **extensão consciente da
D29**: o da árvore, porque o risco não é de layout e sim de a demonstração vazar para o modo normal
(nó bloqueado clicável sem ninguém ter ligado nada) ou o modo não desligar; e o do nó, porque a
revisão do PR achou ali dois defeitos que só a navegação em sequência expõe — o `QuizForm`
reaproveitado ao trocar de nó exibia o "nó concluído" do anterior justamente num nó bloqueado em
demonstração, e o dado preservado pelo `useAsync` deixava um `DrillForm` ligado ao código anterior
gravável sob a URL nova. Os dois antecediam o modo, mas percorrer nós em sequência é o que os tira
do canto e põe no caminho

## Revisar quando

Se a demonstração começar a ser usada como forma de estudar fora de ordem, e não para revisar
conteúdo — aí a pergunta não é sobre o modo, é se a progressão obrigatória ainda faz sentido (D1). E
se um dia houver login (#24), rever se o bloqueio deve passar a ser validado também no servidor

---

[Índice das decisões](../README.md)
