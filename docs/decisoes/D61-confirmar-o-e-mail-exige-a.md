# D61 — Confirmar o e-mail exige a senha do cadastro, e recadastro pendente troca a senha

## Justificativa

A auditoria de segurança (FOS-01) achou um pré-sequestro de conta: o link de confirmação provava que
quem clica controla a caixa, mas não que foi quem cadastrou. Recadastro de e-mail não verificado só
reenviava o link, mantendo a senha do **primeiro** cadastrante; na confirmação, a identidade de
senha era anexada à conta dona do `primary_email` (D48). Bastava cadastrar antes o endereço de
alguém: se a vítima depois se cadastrasse — ou, já tendo conta Google, clicasse no e-mail "Falta um
passo" —, a senha do atacante passava a abrir a conta dela, sem que ele jamais visse a caixa. As
duas metades fecham o caminho juntas: a senha na confirmação exige **as duas provas na mesma
pessoa**, e a troca no recadastro faz o link valer para quem pediu por último. Com as duas, a fusão
automática por e-mail da D48 **continua** — quem confirma provou caixa e senha, que é o que a fusão
sempre supôs; bloqueá-la trocaria um defeito por um atrito sem ganho. Senha errada **não gasta** o
link (quem tem a caixa pode errar a digitação) e conta no mesmo freio do login, por e-mail e por IP.
A saída de quem não lembra da senha é a redefinição, que já confirma o endereço com a senha de quem
tem a caixa. O custo: recadastro de terceiro troca a senha pendente e faz o link da pessoa legítima
pedir uma senha que ela não sabe — vira incômodo limitado pelo freio de e-mail, e a tela aponta a
redefinição. E dev perdeu o login "só com link": as contas do `seed-dev-users` ganharam senha fixa
(`{noop}`, só no container local)

## Revisar quando

Se aparecer um caminho de confirmação sem senha (magic link, confirmação por provedor), ele precisa
de outra prova de que quem confirma é quem cadastrou — senão é esta decisão revertida. Se a troca de
senha no recadastro virar vetor de assédio medido (reclamações, picos no freio de e-mail), revisar:
guardar a senha pendente **no token**, e não na credencial

---

[Índice das decisões](../README.md)
