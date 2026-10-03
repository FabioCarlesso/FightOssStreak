# D33 — Landing pública na raiz, e o app passa a viver em `/hoje`

## Justificativa

Quem recebia o link do app caía no `DisclaimerGate`: muro de texto jurídico com "Li e concordo"
antes de saber o que era aquilo. E o portão chama `GET /api/disclaimer` para renderizar, então com o
backend frio a primeira tela não era nem o muro — era "Não foi possível falar com a API". A landing
conserta os dois de uma vez porque é **estática**: nenhuma chamada de API antes de a pessoa decidir
entrar. O aceite não afrouxou em nada — ele continua sendo o primeiro a decidir em todas as rotas do
app, inclusive na de "página não encontrada", que ficou dentro do portão de propósito (URL
desconhecida não é motivo para abrir o app sem aceite).

**O conflito real era outro**: `/` é a porta de quem chega pelo link e também o atalho de quem usa
todo dia, e os dois querem coisas opostas. Resolvido com marca em `localStorage` gravada **depois**
do portão — quem parou no aviso e desistiu não entrou —, com `?ver` trazendo a apresentação de volta
para o link continuar compartilhável. Sem inventar conta para isso (D9).

**A copy não vende o que não existe**: sem monetização (D8) e sem cadastro (D9), o CTA é "Abrir o
app" e "Ver o código", e a seção "o que ele não é" está lá porque a D1 define o produto — landing
que deixasse "app de jiu-jitsu" no ar estaria anunciando outra coisa. Os números da página (46 nós,
91 perguntas, 11 vídeos) são conferidos por teste contra o currículo versionado, porque copy com
número envelhece em silêncio: escrever o quiz de M4 não faz ninguém lembrar de trocar o texto.

**Prints são a página**, e vêm de `scripts/capturar-prints.mjs`: Chrome headless por CDP, sem
Playwright nem Puppeteer entrando pelo `package.json` (a D29 já os recusou nos testes, e script de
captura não é a porta dos fundos). O progresso que aparece neles é semeado pela API pública, sem SQL
— o `drilledOn` do `DrillRequest` aceita data passada, e é isso que produz streak, agenda vencida e
as métricas de `docs/produto/mvp-web.md` em um banco recém-subido. Dois tamanhos por tela e troca por `<picture>`, porque
print de 1280px não se lê em 390px. Detalhes de acessibilidade e de forma que valem registro: o CTA
usa **texto escuro sobre o accent** (branco sobre `#e2703a` dá ~3.2:1, que só passa em AA como texto
grande, e o CTA se repete em corpo normal); `og:url` e `og:image` são injetadas no build a partir de
`VITE_PUBLIC_URL` e simplesmente não são emitidas sem ela, porque domínio cravado no HTML é
configuração de ambiente dentro da imagem (D22); e a seção de CSS da landing teve que ser posta
**antes** dos blocos `@media`, já que a especificidade é igual e quem decide é a ordem do arquivo —
com ela no fim, o hero saía em duas colunas no celular

## Revisar quando

Se a landing começar a prometer o que o app não entrega, ou se o app deixar de ser de uso pessoal —
aí a pergunta deixa de ser sobre a página e passa a ser sobre login (#24), e a marca local vira
sessão de verdade — **atingido em parte pela D36**: o CTA virou "Pedir acesso", porque com aprovação
"Abrir o app" passou a prometer o que a página não entrega. A marca em `localStorage` continua, mas
deixou de ser o único estado: quem já entrou tem sessão de verdade

---

[Índice das decisões](../README.md)
