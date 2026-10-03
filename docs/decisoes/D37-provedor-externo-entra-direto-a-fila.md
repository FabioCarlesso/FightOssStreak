# D37 — Provedor externo entra direto; a fila passa a valer para quem não tem provedor

## Justificativa

A D36 tratou autenticar e entrar como coisas separadas, e isso continua certo — mas ela pôs **todo
mundo** na fila, inclusive quem já teve a identidade verificada por um terceiro. Rodando o app no
ar, ficou claro que a fricção estava no lugar errado: o Google era o único provedor, então a fila
cobrava aprovação de quem menos precisava e **não existia caminho nenhum** para quem não tem conta
Google — essa pessoa não conseguia nem pedir. A decisão inverte: login por provedor dá acesso
direto, e a aprovação do autor passa a valer para o pedido por e-mail.

**A recusa continua valendo**: conta `RECUSADO` não é reaberta pela regra nova.

**Ser dono deixa de se confundir com ser aprovado** — `fos.auth.owner-emails` agora significa só "vê
a fila e decide", e continua exigindo e-mail verificado.

**Magic link, e não senha**, porque a promessa da tela de login é que o app nunca vê senha, e
guardá-la traria hash, recuperação e vazamento como risco novo; o link é credencial de 15 minutos e
uso único, guardado só como hash.

**Nenhum e-mail sai no pedido**: um endpoint público que dispara e-mail para qualquer endereço é
canal de spam com o domínio do app no remetente, e queima entregabilidade. O primeiro e-mail sai na
aprovação — daí decorre que pedir acesso com o endereço de outra pessoa não dá acesso a ninguém,
porque o link vai para a caixa do dono do endereço.

**`/entrar` responde igual** para conta inexistente, pendente, recusada e aprovada, para não virar
consulta de quem tem conta no app. Um detalhe que só apareceu implementando: com a conta de provedor
nascendo aprovada, a adoção do progresso semeado passou a poder **apagar uma conta que já tem dado**
— a guarda nova só adota quando a conta está vazia.

## Revisar quando

Se o app deixar de ser de uso pessoal, ou se a entrada por e-mail virar porta de entrada principal
em vez de exceção. Aí a pergunta deixa de ser "quem o autor libera" e passa a ser cadastro aberto

---

[Índice das decisões](../README.md)
