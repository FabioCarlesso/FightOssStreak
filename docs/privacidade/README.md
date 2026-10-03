# Privacidade e dados pessoais

> **Nota:** redigido por não-advogado, como os textos de `docs/produto/disclaimer.md`. Antes
> de publicar o app em loja ou de abri-lo além do círculo pessoal, revisar com profissional.

Até a #24 o app não tinha o que documentar aqui: sem login (D9), não havia dado pessoal — havia um
usuário único e o progresso dele. Com login (D36) o app passou a guardar dado de pessoas, e com o
cadastro aberto (D47) passou a guardar também **hash de senha**. Este documento diz o quê, por quê e
por quanto tempo.

## O que é coletado

| Dado | De onde vem | Por que existe |
|---|---|---|
| Provedor e identificador da conta no provedor (`sub`/`id`) | do provedor, no login | é a identidade: é o par que diz de quem é o progresso |
| Nome de exibição | do provedor, no primeiro login | identificar a conta na tela |
| E-mail | de você, no cadastro; ou do provedor, no primeiro login | identifica a conta, é o que vincula Google e senha no mesmo endereço, e é o que quem administra lê para reconhecer uma conta |
| Data do primeiro e do último login | do próprio app | saber se a conta ainda é usada |
| Progresso, streak, agenda de revisão, drills, anotações e os dias sem treino perdoados por freeze (#99) | do uso do app | é o produto |
| Diário de treino: data, tipo, duração, **peso**, **sensação** e o que você escreveu sobre o treino (#114) | de você, ao registrar um treino | é a entrada do app desde a D56 — e peso e sensação são **dado referente à saúde**, com seção própria abaixo |
| Aceite do aviso de responsabilidade, com data e versão | do uso do app | requisito de produto (`docs/produto/disclaimer.md`) |
| **Hash da sua senha**, se você criou conta com e-mail e senha | de você, no cadastro | é o que confere a senha na entrada |
| Papel da conta (`role`), com data e id de quem o mudou | de quem administra, ou da semente `fos.auth.owner-emails` na subida | decide quem vê a administração do app (D49) |
| Estado de acesso, com data, id de quem decidiu e **o motivo escrito** | de quem administra, ao bloquear ou desbloquear | é a trilha de uma decisão sobre uma conta — bloqueio sem registro de quem e por quê é pior que bloqueio nenhum (D49) |

**A senha não é guardada — o hash dela é.** Até a D47 o app não via credencial nenhuma: a autenticação acontecia no provedor, e esta seção dizia isso. Com o cadastro aberto (#81) passou a existir uma senha, e o que fica no banco é só o hash (bcrypt, com o prefixo do algoritmo), numa tabela separada da identidade. Do valor que você digita não sobra nada depois da requisição — nem em log, nem em resposta de API. **Quem entra pelo Google continua sem ter senha aqui**, e nada muda para essa pessoa.

**O que a confirmação de e-mail guarda.** Os links de confirmação e de redefinição também vivem só como hash, valem uma vez (24 horas e 1 hora, respectivamente) e são queimados quando a senha muda.

**Nada é vendido, compartilhado ou usado para publicidade.** Não há rastreador de terceiros: nenhum
script de outra empresa entra na página, e nenhum dado sai do banco do projeto. Os números da tela
`/progresso` são calculados sobre esse banco.

**Desde a D50 existe coleta de uso — e ela tem documento próprio.** Até essa decisão este
documento dizia "nem analytics", e a frase caiu junto com ela. O que existe agora é medição de
acesso feita pelo próprio app: sem cookie de rastreio, sem terceiro e **sem guardar endereço de
IP**. O quadro completo, incluindo o que **não** é coletado, está em
[`coleta-de-uso.md`](coleta-de-uso.md).

O e-mail pode não existir: o Facebook pode não devolver e-mail nenhum, e a Apple entrega um endereço
de relay quando a pessoa escolhe esconder o dela. O app funciona igual — e-mail não é identidade.

## Como apagar tudo

Em *Sua conta* → **Excluir minha conta**, ou `DELETE /api/me`. Apaga, em uma transação, a conta, a
identidade externa, o hash da senha, os links pendentes, progresso, agenda de revisão, drills,
anotações, tentativas de quiz, os dias perdoados por freeze, **as sessões do diário — peso e sensação
inclusive** —, **os feedbacks que você mandou** e o aceite do aviso. Não há cópia lógica nem
lixeira: o que sai, sai.

**Feedback sai, não é anonimizado** (D64). A mensagem é texto livre seu, e tirar só o autor deixaria
na fila o que você escreveu. Se você administra e decidiu feedback de outra pessoa, esse feedback
continua na fila — ele é dela —, e só a marca de que foi você quem decidiu é apagada. Até a D64 a
exclusão de quem tinha mandado ou decidido feedback falhava inteira (FOS-04): a conta ficava, e
nada saía.

Ela entrou junto com o login, e não depois, porque a loja da Apple recusa app com login e sem
deleção (`docs/produto/publicacao-ios.md`) — e porque manter dado de quem nunca entrou seria
indefensável.

## Retenção

Enquanto a conta existir. Não há expurgo automático de conta inativa: o app é de uso pessoal e o
volume é pequeno demais para que apagar por inatividade proteja alguém — apagaria progresso de quem
passou dois meses lesionado, que é justamente quem mais precisa da revisão ao voltar.

## O que falta antes de publicar em loja

- Política de privacidade **pública**, fora do repositório, com contato do responsável.
- *Privacy labels* da App Store descrevendo a coleta acima.
- Revisão jurídica dos dois textos (este e o de responsabilidade).

## Os outros documentos desta pasta

| Arquivo | O que contém |
|---|---|
| [`contas.md`](contas.md) | Cadastro com senha, cadastro não confirmado, o que quem administra vê e a conta de demonstração |
| [`dados-de-saude.md`](dados-de-saude.md) | Peso e sensação do diário: dado referente à saúde (D57) |
| [`coleta-de-uso.md`](coleta-de-uso.md) | A coleta de uso do app (D50) e o painel que a lê (D52) |
| [`saude-do-site.md`](saude-do-site.md) | O que a medição de saúde do site grava, e o que não grava (D54) |
