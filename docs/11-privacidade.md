# Privacidade e dados pessoais

> **Nota:** redigido por não-advogado, como os textos de `06-disclaimer-responsabilidade.md`. Antes
> de publicar o app em loja ou de abri-lo além do círculo pessoal, revisar com profissional.

Até a #24 o app não tinha o que documentar aqui: sem login (D9), não havia dado pessoal — havia um
usuário único e o progresso dele. Com login social e acesso sob aprovação (D36), o app passou a
guardar dado de pessoas, inclusive de quem **pediu acesso e não entrou**. Este documento diz o quê,
por quê e por quanto tempo.

## O que é coletado

| Dado | De onde vem | Por que existe |
|---|---|---|
| Provedor e identificador da conta no provedor (`sub`/`id`) | do provedor, no login | é a identidade: é o par que diz de quem é o progresso |
| Nome de exibição | do provedor, no primeiro login | identificar a conta na tela e na fila de solicitações |
| E-mail (quando o provedor devolve) | do provedor, no primeiro login | reconhecer quem pediu acesso, e decidir quem é dono (`fos.auth.owner-emails`) |
| Data do primeiro e do último login | do próprio app | saber se a conta ainda é usada |
| Progresso, streak, agenda de revisão, drills e anotações | do uso do app | é o produto |
| Aceite do aviso de responsabilidade, com data e versão | do uso do app | requisito de produto (`06-disclaimer-responsabilidade.md`) |

**O app nunca vê sua senha.** A autenticação acontece no provedor; o que volta é o mínimo acima.

**Nada é vendido, compartilhado ou usado para publicidade.** Não há rastreador de terceiros, nem
analytics: os números da tela `/progresso` são calculados sobre o próprio banco.

O e-mail pode não existir: o Facebook pode não devolver e-mail nenhum, e a Apple entrega um endereço
de relay quando a pessoa escolhe esconder o dela. O app funciona igual — e-mail não é identidade.

## Quem pediu acesso e não foi liberado

Uma solicitação recusada **continua guardada**: sem isso a recusa se apagaria sozinha e a mesma
conta voltaria para a fila a cada login. O que fica é o mesmo do quadro acima (identidade, nome,
e-mail e datas) e nada mais — conta pendente ou recusada não tem progresso, agenda nem drill,
porque o portão impede que ela escreva qualquer coisa.

Quem quiser sumir antes de qualquer decisão tem o botão na própria tela de espera.

## Como apagar tudo

Em *Sua conta* → **Excluir minha conta**, ou `DELETE /api/me`. Apaga, em uma transação, a conta, a
identidade externa, progresso, agenda de revisão, drills, anotações, tentativas de quiz e o aceite
do aviso. Não há cópia lógica nem lixeira: o que sai, sai.

A rota existe para conta aprovada, pendente e recusada. Ela entrou junto com o login, e não depois,
porque a loja da Apple recusa app com login e sem deleção (`02-publicacao-ios-desafios.md`) — e
porque manter dado de quem nunca entrou seria indefensável.

## Retenção

Enquanto a conta existir. Não há expurgo automático de conta inativa: o app é de uso pessoal e o
volume é pequeno demais para que apagar por inatividade proteja alguém — apagaria progresso de quem
passou dois meses lesionado, que é justamente quem mais precisa da revisão ao voltar.

## O que falta antes de publicar em loja

- Política de privacidade **pública**, fora do repositório, com contato do responsável.
- *Privacy labels* da App Store descrevendo a coleta acima.
- Revisão jurídica dos dois textos (este e o de responsabilidade).

## Entrada por e-mail (D37)

Quem não tem provedor externo informa o próprio endereço para pedir acesso. Nesse caminho o app
guarda **o endereço digitado** — e ele é a chave da identidade, do mesmo jeito que `(provider,
subject)` é para quem entra por provedor.

Duas notas sobre esse dado:

- O endereço nasce **não verificado**: até a pessoa abrir um link enviado para ele, ninguém provou
  ser dono daquela caixa. É por isso que digitar o e-mail que está em `fos.auth.owner-emails` não
  torna ninguém dono do app.
- Os **links de entrada não são guardados**. O que fica na tabela é o hash do token, com validade e
  marca de uso. Quem tiver leitura do banco não consegue entrar como ninguém.
- O endereço **vai para a caixa do dono** no resumo da fila (D38), porque decidir sem saber quem
  pediu é impossível. Ele continua fora do log: o que se registra é a contagem, não o endereço.

A exclusão de conta apaga também a identidade de e-mail e os links pendentes, na mesma transação do
resto.

## Conta de demonstração (D39)

O botão *Ver o app funcionando*, na página inicial, abre uma **conta temporária** — não pede
e-mail, não pede login, não pergunta nada. É a única forma de usar o app sem informar quem você é,
e é assim de propósito: ela existe para quem ainda está decidindo se quer uma conta.

O que essa conta guarda:

| Dado | De onde vem |
|---|---|
| Um identificador aleatório, sem relação com você | sorteado na criação |
| Progresso, agenda, drills e anotações **de exemplo** | copiados da conta-modelo do autor |
| O que você fizer durante a demonstração | do seu uso |

O que ela **não** guarda: e-mail, nome, provedor de login, endereço de IP. O IP é usado no momento
do pedido, para o freio que impede abrir demonstrações em série, e vive em memória — não é gravado
em tabela nenhuma.

**Ela some sozinha em duas horas.** Passado o prazo, a sessão deixa de autenticar e a conta é
apagada por inteiro na próxima demonstração que alguém abrir — as mesmas sete tabelas da exclusão
comum, sem cópia lógica nem lixeira. Quem quiser encerrar antes tem o botão em *Sua conta* →
**Encerrar demonstração**, que é a mesma `DELETE /api/me`.

O aceite do aviso de responsabilidade **não** é copiado da conta-modelo: quem entra pela
demonstração passa pelo aviso como qualquer um, e o aceite morre junto com a conta.

### Um aviso para quem cura a conta-modelo

O que vale para o visitante vale ao contrário para o autor: **anotação fixada e nota de drill da
conta-modelo são lidas por qualquer pessoa que abra a demonstração.** É a única superfície do app
onde texto do autor é publicado sem um ato de publicar — não há botão de "tornar público", basta a
conta estar em `FOS_DEMO_TEMPLATE_EMAIL`. Nome, e-mail e identidade do autor não são copiados; o
texto é.
