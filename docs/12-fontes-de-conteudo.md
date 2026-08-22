# Fontes de Conteúdo

O currículo inteiro — 46 conceitos, 91 perguntas — foi escrito sem registro de fonte. Isso está
declarado como risco aceito na D12 (`07-decisoes.md`): "currículo montado sem revisão de graduado",
e funciona enquanto o uso é pessoal. O que muda é a escala — o épico #55 reescreve os 46 conceitos
e multiplica as perguntas por três ou quatro — e nessa escala, escrever de memória ou pesquisar sem
anotar de onde veio tem dois custos que não existiam antes: um erro pedagógico deixa de ser
localizável (não dá para saber quais outros nós beberam da mesma fonte errada), e texto de terceiro
passa a poder entrar no repositório sem ninguém ter decidido que entrou. O projeto é rígido com
direito de vídeo (D7, `08-curadoria-videos.md`) e frouxo com texto — não há motivo para a diferença.

Este documento é a régua. Aplicá-la nos 46 nós é trabalho de outra issue (B e C do épico #55); este
aqui não revisa uma linha do que já está escrito, e não fecha o risco da D12 — só torna o rastro
auditável a partir de agora.

**Fora de escopo, de propósito:** campo de fonte no JSON do currículo ou na tela do nó. O registro é
de curadoria, não de produto — um nó exibindo bibliografia é exatamente o app parecendo curso (D1).
Se isso mudar um dia, é decisão própria, não consequência deste documento.

## O que conta como fonte

Em ordem de preferência:

1. **O professor e o treino.** É a fonte que o app existe para reter (D1) — o FOS organiza o que já
   foi ensinado no tatame, não substitui quem ensinou.
2. **Livro-referência de currículo de BJJ.** Manual amplo, com autoria reconhecida e edição —
   não um resumo de blog que cita um.
3. **Canal instrucional estabelecido.** O mesmo crivo que `08-curadoria-videos.md` já aplica para
   escolher vídeo: instrucional e não highlight, autor identificável, risco menor de sair do ar.
4. **Regulamento oficial**, onde a pergunta é de regra e não de técnica — IBJJF ou CBJJ.
5. **Enciclopédia ou artigo genérico** — só para nomenclatura (como se chama a posição, a
   finalização). Nunca para "quando usar" ou "erro comum": isso exige fonte técnica de verdade.

## O que não conta

Nenhum destes sustenta uma afirmação de "isto é erro comum" ou "é assim que se faz":

- Fórum (Reddit, fóruns de BJJ, grupos de Facebook)
- Resposta de rede social, mesmo de atleta conhecido
- Resumo gerado por IA
- Blog sem autor identificável
- Vídeo de highlight ou compilação de competição

## Como texto de terceiro pode e não pode ser usado

Regra sem ambiguidade: **o texto do nó é escrito com as próprias palavras.** Se houver citação
literal, ela é curta, vem entre aspas e com crédito. **Tradução de um trecho de terceiro é texto de
terceiro** — traduzir um parágrafo de um livro ou artigo não o transforma em texto próprio, pelo
mesmo motivo que recortar um vídeo não vira vídeo próprio (D7).

## Canais já usados nos canônicos de M0 e M1

Estes oito canais já foram julgados uma vez — o crivo é o de `08-curadoria-videos.md` (instrucional,
Gi, canal estabelecido) — e por isso entram direto na lista, sem precisar ser retriados.

| Canal | Cobre bem | Já usado em |
|---|---|---|
| [Keenan Cornelius](https://www.youtube.com/@fritzdagger) | Etiqueta e cultura de tatame, com tom de segurança e não de tradição decorativa | M0.1 |
| [Knight Jiu-Jitsu](https://www.youtube.com/@KnightJiuJitsu) | Fundamentos de queda segura (breakfall/ukemi) explicados como mecânica, não como acrobacia | M0.2 |
| [Ritchie Yip](https://www.youtube.com/@RitchieYip) | Fundamentos para iniciante com tom conservador — é o canal mais cauteloso da leva, o que importa em M0.3 | M0.3, M1.2 |
| [SBG PDX & Vancouver BJJ and MMA Videos](https://www.youtube.com/@sbgipdx) | Postura e base como conceito estrutural, não como postura isolada de uma posição | M0.4 |
| [RVV BJJ](https://www.youtube.com/@RVVBJJ) | Pegadas de gi explicadas por capítulo (manga, gola, calça), com o porquê de cada uma | M0.5 |
| [Jordan Teaches Jiujitsu](https://www.youtube.com/@JordanTeachesJiujitsu) | Sobrevivência sob pressão e recuperação de guarda como conceito, não como sequência decorada | M1.1, M1.5 |
| [Stephan Kesting](https://www.youtube.com/@StephanKesting) | Fugas (100kg, montada) com a lógica por trás do movimento, não só a execução | M1.3, M1.4 |
| [Matt Arroyo Jiu Jitsu](https://www.youtube.com/@MattArroyoMMA) | Sistema de fuga de costas, com a ordem de prioridade (pescoço antes de gancho) explícita | M1.6 |

**[Guiabasicodejiujitsu](https://www.youtube.com/@Guiabasicodejiujitsu)** também já é fonte de fato
do projeto — os sete complementares da D32. Pela ordem de preferência acima, é fonte de primeira
categoria (professor e treino: é a academia do autor) para **procedência**, e não substitui fonte
instrucional para **conceito** — um complementar lembra a técnica, não a ensina
(`08-curadoria-videos.md`).

## Fontes específicas de M0

M0 não é técnica — é etiqueta, higiene e a regra do tap — e por isso tem uma fonte que os outros
módulos não têm:

- **M0.1** (etiqueta e hierarquia): o **regulamento oficial** é fonte adequada para a parte que é
  regra e não costume — [IBJJF, "Books and Videos"](https://ibjjf.com/books-videos) publica o
  rulebook (v6.0 no momento em que este documento foi escrito); a
  [CBJJ](https://cbjj.com.br/) é a confederação brasileira e adota o mesmo regulamento. Regulamento
  não substitui o canal já usado (Keenan Cornelius) para o tom — ele resolve "o que é regra",
  não "por que a hierarquia existe".
- **M0.3** (tap e reconhecer perigo) ⚠️: `08-curadoria-videos.md` já marca este como "o nó mais
  sensível do currículo" e manda escolher o vídeo mais conservador entre dois, mesmo que isso
  signifique deixar vazio. A mesma régua vale para a fonte do texto: entre uma fonte que trata o
  tap como segurança do parceiro e uma que o trata como "desistência", a primeira vence mesmo que
  a segunda seja mais completa. Foi o critério que já escolheu Ritchie Yip para o vídeo deste nó.

## Fontes por módulo — M8

M8 ainda não tem vídeo catalogado (`08-curadoria-videos.md`, estado atual cobre M0 a M7) — logo, não
tem canal instrucional julgado. Isto é estado normal, o mesmo que `video: null`: M8.2 e M8.3 têm
motivo estrutural (o próprio `concept` diz que a habilidade não vem de vídeo assistido); M8.1 ficou
sem candidato à altura apesar da busca — ver a nota em `08-curadoria-videos.md`.

| Módulo | Fonte instrucional |
|---|---|
| M8 — Jogo Integrado | nenhuma catalogada ainda |

## Canais usados nos canônicos de M2 a M7

Treze canais, triados pela régua acima (Gi, instrucional, canal estabelecido, ensina o conceito) e
pelo critério de idioma da D45, ao catalogar os blocos M2–M7 pela #61. Dois já vinham aprovados da
lista de leads do épico #55 e só foram **usados** agora — **FEU BJJ** e **Lawrence Luna**, ambos
triados na D45/#60. Os outros onze surgiram da busca dirigida por técnica, nó a nó.

| Canal | Cobre bem | Já usado em |
|---|---|---|
| [Cientistas do Jiu Jitsu](https://www.youtube.com/@Cientistasdojiujitsu) | Fundamentos com professor identificado (Cristiano Carvalho); canal de porte modesto (~29 mil inscritos) | M2.1 |
| [Muito Mais Ação Jiu Jitsu](https://www.youtube.com/@muitomaisacaojiujitsu) | Técnicas fundamentais em vídeos capitulados, um por golpe, com estrutura de introdução → princípio → passo a passo → recapitulação | M2.2, M2.5, M2.6, M7.4 |
| [Felipe Siqueira Jiu Jitsu](https://www.youtube.com/@FelipeSiqueiraJiuJitsu) | Professor identificado (faixa-preta 3º grau, Gracie Barra), catálogo amplo de técnicas isoladas | M2.3, M3.3, M3.4, M3.6, M4.4, M6.1 |
| [Manual do Jiu-Jitsu](https://www.youtube.com/@ManualdoJiuJitsu) | Aulas completas por posição, capituladas, com foco declarado em conceito antes de execução | M2.4, M2.7, M3.2, M3.5, M4.1, M4.5, M5.1, M6.2 |
| [Marcelo Galdino](https://www.youtube.com/@MarceloGaldinoBJJ) | Professor identificado, 215 mil inscritos, catálogo extenso organizado por curso (faixa branca/azul/roxa) | M3.1, M4.2 |
| [Micael Galvão](https://www.youtube.com/@micagalvaobjj) | Atleta faixa-preta competidor; canal mistura competição e instrução — só os vídeos claramente instrucionais entram como canônico | M3.7 |
| [FEU BJJ](https://www.youtube.com/@FEUBJJ) | 936 mil inscritos, mais de uma década de aulas em vídeo (já triado na D45/#60) | M4.3, M4.6, M5.3 |
| [Felipe Pena Preguiça](https://www.youtube.com/@FelipePenaPreguiça) | Multicampeão mundial ADCC, série instrucional própria "FP CLASS" | M5.2 |
| [Fabio Gurgel](https://www.youtube.com/@FabioGurgel) | Faixa coral, cofundador da Alliance Jiu-Jitsu, um dos treinadores mais respeitados do Brasil | M5.4 |
| [Lawrence Luna (@lawluna)](https://www.youtube.com/@lawluna) | Curso completo faixa branca (já triado na D45/#60); primeiro uso em vídeo canônico | M6.3 |
| [André Machado BJJ](https://www.youtube.com/@AndréMachadoBJJ) | Professor faixa-preta identificado, canal modesto (~19,5 mil inscritos), sem equivalente melhor achado | M6.4 |
| [Voūk Brasil](https://www.youtube.com/@voukbrasil) | Canal oficial de marca de kimono, 125 mil inscritos, conteúdo instrucional com instrutor identificado (Gabriel) | M7.1, M7.2 |
| [Dojjo Confere](https://www.youtube.com/@Dojjo_tv) | Ex-programa de TV (Canal Combate), 61 mil inscritos; vídeo usado é Roger Gracie ensinando diretamente | M7.3 |

**Três leads foram levantados na abertura do épico #55.** Triados pelos critérios de
`08-curadoria-videos.md` (Gi, instrucional, canal estabelecido, ensina o conceito) mais o critério de
idioma da D45 — sem assistir aos vídeos por inteiro, mesma ressalva da D21 e do restante deste
documento:

| Canal | Cobre bem | Situação |
|---|---|---|
| [FEU BJJ](https://www.youtube.com/@FEUBJJ) | 936 mil inscritos, mais de uma década de aulas em vídeo além dos podcasts recentes — ex.: "3 Raspagens e 3 Finalizações para Iniciantes", 503 mil visualizações, descrito como aula básica | **Aprovado** — candidato a canal instrucional para M2–M8 |
| [Lawrence Luna (@lawluna)](https://www.youtube.com/@lawluna) | 34 mil inscritos, 505 vídeos; curso "Curso Completo da Faixa Branca de Jiu-jitsu" em vídeo-aulas encadeadas por capítulo (posicionamento, rolamento, ...), formato que casa com "ensinar o conceito antes da execução" | **Aprovado** — candidato a canal instrucional para M2–M8 |
| [Jiu Jitsu Channel (@jiu-jitsu)](https://www.youtube.com/@jiu-jitsu) | — | **Reprovado**: descrição do canal em inglês ("BJJ & No-Gi Training"), uploads recentes são clipe de competição/reação ("Tiago vs Lincoln REMATCH", "Kid celebrates like a champion") — falha em instrucional-não-highlight e mistura no-gi (D6); não é efetivamente canal em português apesar do nome ter sido sugerido como lead |

Aprovação aqui é "passou no crivo do canal", não "vídeo já escolhido": qual vídeo específico de FEU
BJJ ou de @lawluna vira canônico de qual nó de M2–M8 é trabalho da #61, nó a nó.

## Tabela: nó → fontes consultadas

Preenchida à medida que B e C (escrita de conceito e de quiz) avançarem. Ao escrever ou revisar um
nó, adicione a fonte efetivamente consultada nesta linha; "nenhuma além do professor" também é um
registro válido.

Quando #58 reescreveu M2–M8, nenhum desses módulos tinha vídeo canônico catalogado
(`docs/08-curadoria-videos.md`), então o texto não teve canal instrucional para citar como fonte. As
linhas registram isso com honestidade — texto reestruturado a partir do conceito que já estava no
currículo, sem pesquisa nova por trás (D43) — em vez de fingir uma fonte que não existiu ou deixar a
linha em branco. M2 ganhou vídeo depois, pela #61 — a fonte do texto continua sendo D43 (não mudou
retroativamente), e a linha de cada nó passou a citar também o canal do vídeo, separadamente.

### M0 — Fundamentos e Segurança

| Nó | Fontes consultadas |
|---|---|
| M0.1 | Canal Keenan Cornelius (vídeo canônico); regulamento IBJJF/CBJJ para a parte de regra; +5 perguntas novas: mesma base do `concept` do nó, sem fonte externa adicional |
| M0.2 | Canal Knight Jiu-Jitsu (vídeo canônico); +5 perguntas novas: mesma base do `concept` do nó, sem fonte externa adicional |
| M0.3 ⚠️ | Canal Ritchie Yip (vídeo canônico) — escolha conservadora, ver seção acima; +4 perguntas novas: mesma base do `concept` do nó, sem fonte externa adicional |
| M0.4 | Canal SBG PDX & Vancouver BJJ and MMA Videos (vídeo canônico); +5 perguntas novas: mesma base do `concept` do nó, sem fonte externa adicional |
| M0.5 | Canal RVV BJJ (vídeo canônico); +5 perguntas novas: mesma base do `concept` do nó, sem fonte externa adicional |

### M1 — Sobrevivência e Fuga

| Nó | Fontes consultadas |
|---|---|
| M1.1 | Canal Jordan Teaches Jiujitsu (vídeo canônico); +4 perguntas novas: mesma base do `concept` do nó, sem fonte externa adicional |
| M1.2 | Canal Ritchie Yip (vídeo canônico); +5 perguntas novas: mesma base do `concept` do nó, sem fonte externa adicional |
| M1.3 | Canal Stephan Kesting (vídeo canônico); Guiabasicodejiujitsu (complementares, procedência); +5 perguntas novas: mesma base do `concept` do nó, sem fonte externa adicional |
| M1.4 | Canal Stephan Kesting (vídeo canônico); +5 perguntas novas: mesma base do `concept` do nó, sem fonte externa adicional |
| M1.5 | Canal Jordan Teaches Jiujitsu (vídeo canônico); Guiabasicodejiujitsu (complementares, procedência); +5 perguntas novas: mesma base do `concept` do nó, sem fonte externa adicional |
| M1.6 | Canal Matt Arroyo Jiu Jitsu (vídeo canônico); Guiabasicodejiujitsu (complementares, procedência); +5 perguntas novas: mesma base do `concept` do nó, sem fonte externa adicional |

### M2 — Guarda Fechada

| Nó | Fontes consultadas |
|---|---|
| M2.1 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Cientistas do Jiu Jitsu (#61) |
| M2.2 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Muito Mais Ação Jiu Jitsu (#61) |
| M2.3 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Felipe Siqueira Jiu Jitsu (#61) |
| M2.4 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Manual do Jiu-Jitsu (#61) |
| M2.5 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Muito Mais Ação Jiu Jitsu (#61) |
| M2.6 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Muito Mais Ação Jiu Jitsu (#61) |
| M2.7 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Manual do Jiu-Jitsu (#61) |

### M3 — Guarda Aberta e Passagem

| Nó | Fontes consultadas |
|---|---|
| M3.1 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Marcelo Galdino (#61) |
| M3.2 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Manual do Jiu-Jitsu (#61) |
| M3.3 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Felipe Siqueira Jiu Jitsu (#61) |
| M3.4 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Felipe Siqueira Jiu Jitsu (#61) |
| M3.5 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Manual do Jiu-Jitsu (#61) |
| M3.6 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Felipe Siqueira Jiu Jitsu (#61) |
| M3.7 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); +4 perguntas novas, mesma base (D43). Vídeo: canal Micael Galvão (#61) |

### M4 — Controle Superior

| Nó | Fontes consultadas |
|---|---|
| M4.1 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Manual do Jiu-Jitsu (#61) |
| M4.2 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Marcelo Galdino (#61) |
| M4.3 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal FEU BJJ (#61) |
| M4.4 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Felipe Siqueira Jiu Jitsu (#61) |
| M4.5 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Manual do Jiu-Jitsu (#61) |
| M4.6 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal FEU BJJ (#61) |

### M5 — Costas (Back Control)

| Nó | Fontes consultadas |
|---|---|
| M5.1 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Manual do Jiu-Jitsu (#61) |
| M5.2 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Felipe Pena Preguiça (#61) |
| M5.3 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal FEU BJJ (#61) |
| M5.4 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Fabio Gurgel (#61) |

### M6 — Quedas e Entrada em Combate

| Nó | Fontes consultadas |
|---|---|
| M6.1 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Felipe Siqueira Jiu Jitsu (#61) |
| M6.2 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Manual do Jiu-Jitsu (#61) |
| M6.3 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Lawrence Luna (#61) |
| M6.4 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal André Machado BJJ (#61) |

### M7 — Finalizações Encadeadas

| Nó | Fontes consultadas |
|---|---|
| M7.1 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Voūk Brasil (#61) |
| M7.2 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Voūk Brasil (#61) |
| M7.3 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Dojjo Confere (#61) |
| M7.4 | Texto: reestruturado do já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas, mesma base (D44). Vídeo: canal Muito Mais Ação Jiu Jitsu (#61) |

### M8 — Jogo Integrado

| Nó | Fontes consultadas |
|---|---|
| M8.1 | Reestruturado do texto já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas derivado do mesmo `concept`, sem fonte externa nova (D44) |
| M8.2 | Reestruturado do texto já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas derivado do mesmo `concept`, sem fonte externa nova (D44) |
| M8.3 | Reestruturado do texto já existente no currículo, sem fonte externa nova (D43); banco de 8 perguntas derivado do mesmo `concept`, sem fonte externa nova (D44) |
