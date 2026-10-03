# D63 — E-mail de provedor só é verificado quando o provedor afirma; o Facebook passa a entrar sem vínculo

## Justificativa

A auditoria (FOS-03) achou o `ProviderLogin.isEmailVerified` devolvendo `true` quando o atributo
`email_verified` **não vinha** — o caso do Facebook, com a justificativa, só num comentário, de que
ele "só devolve e-mail já confirmado". Desse booleano saem as duas decisões mais sensíveis da conta:
o vínculo de identidade nova à conta dona do e-mail (D47) e a semente de administração (D49). A
garantia era do provedor, não estava escrita em lugar nenhum, e a regra 4 ("e-mail não verificado
nunca vincula nada") valia com "não informado" contando como verificado.

**Agora ausente é não verificado**: `flag != null && parseBoolean(flag)`.

**Lista de provedores confiáveis foi recusada**: ela duplicaria o que o atributo já diz — o Google o
afirma no id token — e um provedor novo na lista seria um segundo lugar para esquecer; a regra fica
sendo uma só, a do atributo.

**A experiência no Facebook**: o login funciona igual, mas a identidade nasce não verificada, a
conta nasce **separada** e sem `primary_email`, e por isso não se anexa à conta Google ou de senha
do mesmo endereço, não vira `ADMIN` pela semente e não pode ser promovida pela tela *Usuários*, que
exige e-mail verificado. Quem quiser uma conta só entra pelo Google ou se cadastra com senha — a
confirmação do próprio app é o que verifica o endereço. Confirmar pelo app para depois anexar o
Facebook seria fluxo novo de vínculo, fora do escopo.

**O que já estava gravado** se corrige na `V18`, e não no próximo login: a semente lê
`email_verified` na subida, antes de login nenhum. Ela zera `email_verified` das identidades
`facebook` e o `primary_email` da conta que só o tinha por elas — mantido, ele faria o próximo login
Google ou cadastro por senha daquele endereço cair dentro da conta Facebook, o mesmo vínculo pela
outra ponta. Identidade Facebook **já anexada** a uma conta continua nela (desfazer criaria conta
vazia para quem já usa), e **papel não é tocado**: a semente nunca rebaixa, e uma conta que tenha
virado `ADMIN` só pelo Facebook se rebaixa pela tela *Usuários*.

## Revisar quando

Se o Facebook passar a mandar um atributo de verificação, ou se entrar provedor (Apple) que o mande
com outro nome ou formato — o lugar de reconhecer é o `ProviderLogin`, e "ausente é não" continua
valendo para o resto. Se a conta separada no Facebook gerar pedido de vínculo, o caminho é
confirmação pelo app antes de anexar, não confiar no provedor

---

[Índice das decisões](../README.md)
