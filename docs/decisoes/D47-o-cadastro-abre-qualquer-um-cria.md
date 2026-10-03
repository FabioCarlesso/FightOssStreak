# D47 — O cadastro abre: qualquer um cria conta com e-mail e senha, confirmada por link — a fila de aprovação da D36/D37 perde a razão de existir

## Justificativa

A D36 e a D37 previram este momento na própria coluna de revisão: *"se o app deixar de ser de uso
pessoal — aí a pergunta deixa de ser 'quem o autor libera' e passa a ser cadastro aberto"*. É esse
gatilho que está sendo puxado, e por isso a entrada é reversão consciente e não faxina.

**O portão virou o gargalo.** Rodando no ar, os dois caminhos estorvavam justamente quem o app quer
alcançar: o login por provedor entra direto mas exclui quem não tem conta Google, e para essa pessoa
sobrava pedir acesso, esperar o autor decidir e então usar um link de **15 minutos** — prazo de
credencial, não de e-mail que chega de madrugada, cai em promoções e é aberto no celular no dia
seguinte. Nenhuma dessas fricções filtra alguém; todas atrasam todo mundo.

**A senha entra, e a D37 tinha razão em recusá-la.** O argumento dela era que o app nunca precisa
ver credencial, e ele valia enquanto o universo era o autor: sem senha não há hash para vazar,
recuperação para sequestrar conta nem força bruta para frear. Os três riscos são reais e agora são
**assumidos com todas as letras** — mitigados por hash com `DelegatingPasswordEncoder` (prefixo
`{bcrypt}`, para troca de algoritmo ser rehash no login e não migration), por mínimo de 12
caracteres sem regra de composição (linha do NIST SP 800-63B, porque exigir maiúscula e símbolo
produz `Senha@2026`), por freio de tentativas erradas por e-mail **e** por IP, e por redefinição que
queima os links pendentes e derruba as sessões abertas. O que se ganha em troca é a única coisa que
a D37 não conseguia dar: uma porta que não depende de terceiro nem da atenção do autor.

**O magic link não morre — muda de papel.** Deixa de ser meio de login e passa a ser o que confirma
e-mail (24h) e redefine senha (1h), com validade compatível com e-mail de verdade; o `login_token`
ganhou `purpose` e o propósito é **conferido no consumo**, senão o link de 24 horas valeria pelo de
1 hora.

**A metade mais delicada é o vínculo entre Google e senha no mesmo endereço.** A D36 fixou que a
chave da identidade é `(provider, subject)` e que e-mail nunca funde contas — a justificativa era
Apple relay e Facebook sem e-mail, e ela continua verdadeira. O que mudou foi a frequência: com
Google + senha própria, a colisão deixou de ser exceção e virou o caso comum, e manter a regra
intacta produziria exatamente o defeito que ela existia para evitar — quem usa o app pelo Google se
cadastra com o mesmo endereço e encontra a árvore em branco. A exceção é mínima e tem nome:
`app_user.primary_email`, UNIQUE, **sempre verificado**. Identidade nova cujo e-mail verificado já
pertence a uma conta se anexa àquela conta; e-mail **não** verificado nunca vincula nada, que é a
metade da D36 que segura o ataque óbvio (digitar o endereço de outra pessoa num provedor que não
verifica). Nada de progresso é fundido: contas que já existiam com o mesmo endereço verificado
seguem separadas, e a mais antiga fica com o vínculo.

**Se a Apple entrar depois, a regra precisa de outra passada** — relay é um endereço verificado que
não é o da pessoa.

**A D39 fica de pé e fica estranha**: a exceção que ela abre (conta de demonstração nasce `APROVADO`
sem aprovação) perde o sentido quando ninguém mais espera aprovação — o que sustenta a demonstração
deixa de ser a exceção e passa a ser só o prazo, a ausência de identidade e o descarte.

**Envio de e-mail deixa de ser opcional em produção**, e só ali: sem credencial do Resend o cadastro
por senha responde indisponível, do mesmo jeito que o provedor sem `client-id` não aparece — dev e
CI continuam subindo sem segredo nenhum, sem essa porta (regra 4 do CLAUDE.md, preservada). Detalhe
que só apareceu implementando: os três logins que a aplicação faz por conta própria (link de e-mail,
demonstração, senha) montam o `Authentication` dentro de um controller e por isso **não** ganham a
rotação de id de sessão que o Spring aplica sozinho nos logins que passam pelos filtros; o
`SessionLogin` centralizou os três deveres — rotacionar, gravar o contexto e registrar a sessão —
porque o terceiro é do que a redefinição de senha depende para expulsar alguém.

**Abrir o link não confirma; confirmar é um clique** (ajuste feito na revisão da PR): a URL que vai
no e-mail é a do app, o `GET` apenas *consulta* se o link ainda vale e quem o gasta é o `POST` do
botão. O motivo é o mesmo que já tinha feito a redefinição não consumir na abertura, e aqui é mais
concreto ainda: varredor de link corporativo e antivírus de caixa de entrada seguem toda URL que
chega, e com a confirmação no `GET` bastava um deles para a pessoa receber "este link já foi usado"
sem nunca o ter usado.

## Revisar quando

Se a taxa de conta criada e nunca confirmada virar problema (aí entra expurgo de cadastro não
confirmado), se surgir abuso de cadastro em massa (aí entra desafio no formulário), ou ao habilitar
login pela Apple — o relay reabre a regra de vínculo por e-mail

---

[Índice das decisões](../README.md)
