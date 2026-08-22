# Curadoria de Vídeos

Etapa 3 de `05-mvp-web-plano.md`. É o gargalo real do projeto (`01-stack-tecnica.md`): mapear cada
nó a um bom vídeo exige **assistir**, e isso não se automatiza.

Este documento existe para que a parte automatizável já esteja pronta, e a parte humana seja só
decidir com critério.

## Política (D7) — o que vale e o que não vale

**Permitido:** incorporar vídeo público pelo player oficial do YouTube.

**Não fazer:** baixar e re-hospedar; recortar clipes; encobrir a marca do player; incorporar vídeo
marcado como não-incorporável pelo autor.

**Obrigatório:** creditar canal e autor visivelmente. O `CurriculumValidator` recusa vídeo
catalogado sem canal — o crédito não é opcional.

## Como catalogar

```bash
node scripts/catalogar-video.mjs M1.2 https://www.youtube.com/watch?v=XXXXXXXXXXX
node scripts/catalogar-video.mjs M1.2 <url> --start 42     # começa em 0:42
node scripts/catalogar-video.mjs M1.2 <url> --dry-run      # só mostra, não grava
```

O script consulta o YouTube e **preenche título e canal a partir da fonte**, em vez de confiar no
que você digitou. Ele recusa se o vídeo não existe, é privado, ou tem incorporação desativada.

Depois de catalogar, valide:

```bash
cd backend && ./mvnw test
```

Requer acesso de rede ao `youtube.com`. Onde ele não existe, o script falha em vez de gravar — e o
campo continua `null`, que é estado normal.

## Critérios gerais de escolha

- **Somente Gi** (D6). Vídeo no-gi ensina pegada diferente e confunde no M0.5 e no M3.
- **Instrucional, não highlight.** Compilação de competição não serve para revisão.
- **Prefira canal estabelecido.** Risco conhecido em `07-decisoes.md`: vídeo sai do ar e o nó fica
  órfão. Canal grande e antigo cai menos.
- **Curto vence longo.** O nó é uma unidade de revisão, não uma aula. Se o vídeo bom tem 25 min e o
  trecho útil começa em 3:10, use `--start 190`.
- **O vídeo precisa ensinar o *conceito* do nó**, não só executar a técnica. O posicionamento do
  produto é retenção do porquê (D1).
- **Entre candidatos equivalentes, ganha o em português** (D45). Desempate, não passe livre: idioma
  não promove vídeo que falha em outro critério acima — vídeo em português que é highlight, que é
  no-gi (D6) ou que não ensina o conceito continua descartado. Onde não existir vídeo em português à
  altura, cataloga-se o em inglês e o nó fica marcado como **candidato a revisita** na tabela de
  estado atual, em vez de ficar vazio: nó sem vídeo é pior que nó com vídeo em inglês.

## O que cada nó precisa mostrar

### Módulo 0 — Fundamentos e Segurança

| Nó | O vídeo precisa cobrir | Descarte se |
|---|---|---|
| **M0.1** Etiqueta e hierarquia | Higiene, protocolo de entrada/saída do tatame, avisar lesão, regular intensidade com faixas mais baixas | For desabafo sobre "cultura de academia" sem conteúdo prático |
| **M0.2** Quedas seguras | Distribuir impacto por área grande, queixo no peito, **não** postar o braço esticado | For compilado de projeções de judô sem ensinar a queda em si |
| **M0.3** Tap e reconhecer perigo ⚠️ | Tocar **antes** do dano; tap verbal e com o pé; soltar imediatamente ao perceber o tap; por que estrangulamento tem margem menor que chave | Tratar o tap como "desistência" ou glorificar aguentar até o limite |
| **M0.4** Posturas base | As três: quadrupedia (cotovelos colados), base de guarda sentado, postura em pé | Cobrir só a postura em pé |
| **M0.5** Pegadas de gi | Manga, gola e calça; posição do cotovelo perto do corpo | For no-gi, ou listar pegadas sem explicar por que a pegada substitui força |

> **M0.3 é o nó mais sensível do currículo.** É literalmente sobre não se machucar. Se estiver em
> dúvida entre dois vídeos, escolha o mais conservador — e prefira deixar vazio a catalogar um que
> trate tap como fraqueza.

### Módulo 1 — Sobrevivência e Fuga

| Nó | O vídeo precisa cobrir | Descarte se |
|---|---|---|
| **M1.1** Sobrevivência sob pressão | Prioridade: pescoço primeiro, cotovelos colados, respirar; por que explodir cedo é erro | Pular direto para a fuga sem tratar a fase de sobreviver |
| **M1.2** Shrimping | Quadril criando espaço; **não** empurrar com o braço; drill solo *e* aplicação | For só drill de aquecimento, sem mostrar para que serve |
| **M1.3** Fuga de 100kg | A ordem: enquadrar → shrimp → ocupar o espaço com o joelho | Mostrar a fuga sem o enquadramento como pré-condição |
| **M1.4** Fuga de montada | Upa **com o braço bloqueado** (senão ele posta a mão); elbow escape; as duas como par | Ensinar upa sem explicar por que o braço precisa estar preso |
| **M1.5** Recuperação de guarda | Reconstruir a barreira de pernas e retomar a distância | Tratar como "voltar à guarda fechada" apenas |
| **M1.6** Fuga de costas | Pescoço primeiro; ombros ao chão **do lado do braço que estrangula** | Ensinar a tirar os ganchos antes de defender o pescoço |

### Módulo 2 — Guarda Fechada

| Nó | O vídeo precisa cobrir | Descarte se |
|---|---|---|
| **M2.1** Guarda fechada: postura, controle e quebra | As pegadas que impedem a postura de voltar a subir; por que atacar antes de quebrar a postura é o erro central do nó | Ensinar raspagem ou estrangulamento contra oponente ainda ereto, sem mostrar a quebra de postura primeiro |
| **M2.2** Raspagem de tesoura | Joelho atravessado no peito e a perna varrendo; o desequilíbrio criado antes do movimento, não força das pernas | Tratar a tesoura como técnica de força, sem mostrar o momento de desequilíbrio que a antecede |
| **M2.3** Raspagem pêndulo | Prender o braço do oponente do lado do giro antes de sentar; usar o balanço do quadril | Sentar para o pêndulo sem bloquear o braço do lado do giro |
| **M2.4** Estrangulamento cruzado | Profundidade da pegada de gola como o que dá alavanca; a ameaça constante abrindo espaço de raspagem | Ensinar o aperto com pegada rasa, sem mostrar a profundidade necessária |
| **M2.5** Armlock da guarda | Controle de postura e ângulo *antes* de puxar o braço | Pular direto para puxar o braço sem controle de postura/ângulo primeiro |
| **M2.6** Triângulo | Exigência de um braço dentro e outro fora; girar o quadril para criar o ângulo | Mostrar o aperto de frente, sem o giro de quadril que cria o ângulo |
| **M2.7** Omoplata | A perna como alavanca no ombro; como o rolamento de fuga do oponente vira raspagem | Tratar como sequência decorada, sem explicar o ângulo compartilhado com o triângulo |

### Módulo 3 — Guarda Aberta e Passagem

| Nó | O vídeo precisa cobrir | Descarte se |
|---|---|---|
| **M3.1** Guarda aberta: grips, distância, enquadramento | A regra longe-o-bastante/perto-o-bastante; pegadas e enquadramento com pés e joelhos | Não tratar a distância intermediária como o erro central a evitar |
| **M3.2** Passagem torreando | Deslocar as pernas do oponente para o lado e correr para o espaço livre; controle de calça; mudança de direção | Mostrar passagem em linha reta, sem trocar de ângulo |
| **M3.3** Passagem por cima (over-under/smash) | Pressão contínua — nunca as duas mãos livres ao mesmo tempo; avanço gradual sem devolver espaço | Mostrar troca de pegada que solta a pressão por completo |
| **M3.4** Guarda de gancho (butterfly) | Conexão de tronco com o oponente perto; ganchos como alavanca de elevação, não barreira | Butterfly sentada longe do oponente, sem contato de tronco |
| **M3.5** Raspagem de butterfly | Inclinar o próprio tronco para o lado, não só empurrar com a perna | Ensinar a raspagem com tronco ereto, como "força de perna" |
| **M3.6** Meia-guarda: conceito e enquadramento | O enquadramento do tronco (antebraço) como o que importa, não a perna presa | Tratar meia-guarda como só "perna presa", sem enquadramento de tronco |
| **M3.7** Raspagem de meia-guarda (old school) | Ombro por baixo do oponente **e** controle do tornozelo ao mesmo tempo | Controlar o tornozelo com o ombro ainda ao lado do oponente, não embaixo |

### Módulo 4 — Controle Superior

| Nó | O vídeo precisa cobrir | Descarte se |
|---|---|---|
| **M4.1** 100kg: manutenção e pressão | Peso projetado sobre o oponente, não sobre os próprios braços/joelhos; ajuste contínuo ao movimento dele | Ensinar postura fixa de 100kg, sem ajuste ao movimento do oponente |
| **M4.2** Joelho na barriga | Pressão desconfortável que provoca reação; girar e trocar ângulo continuamente | Tratar a posição como descanso, parada |
| **M4.3** Transição 100kg → montada | Bloquear o quadril do oponente *antes* de mover a própria perna | Mostrar a perna se movendo antes do quadril estar bloqueado |
| **M4.4** Montada: manutenção e base | Peso alto, joelhos apertados nas axilas, negar o espaço lateral | Joelhos abertos ou peso caindo para trás, sem correção |
| **M4.5** Americana e Ezequiel | Finalizações que nascem da posição já mantida, sem abrir mão do controle | Ensinar como se exigisse abrir mão do 100kg/montada para armar o ataque |
| **M4.6** Armlock da montada | O gatilho é o oponente empurrando para tirar o peso de cima | Buscar o armlock sem esse gatilho, sem mostrar o risco de perder a montada ao errar |

### Módulo 5 — Costas (Back Control)

| Nó | O vídeo precisa cobrir | Descarte se |
|---|---|---|
| **M5.1** Pegar as costas a partir da montada | Acompanhar o giro que o oponente já inicia tentando escapar, sem forçar | Ensinar como forçar a virada do oponente por conta própria |
| **M5.2** Controle de costas: hooks e seat belt | Ganchos e seat belt como elementos independentes, cada um falhando de um jeito diferente | Tratar os dois como a mesma coisa, com a mesma resposta de recuperação |
| **M5.3** Mata-leão ⚠️ | Neutralizar as mãos de defesa *antes* de atacar o pescoço; por que este estrangulamento tolera menos demora no tap (D-M0.3) | Atacar o pescoço direto, ignorando a defesa das mãos |
| **M5.4** Manutenção de costas contra defesa | Acompanhar o giro trocando de lado, mantendo contato de tronco, sem travar parado | Ensinar a travar a posição parada contra defesa de verdade |

> **M5.3 herda a sensibilidade da M0.3**: é estrangulamento sem aviso de dor. Mesma regra — na dúvida
> entre dois vídeos, o mais conservador quanto ao tap.

### Módulo 6 — Quedas e Entrada em Combate

| Nó | O vídeo precisa cobrir | Descarte se |
|---|---|---|
| **M6.1** Puxar guarda com segurança | Pegada firme antes de sentar; descer controlando o peso do oponente junto, não caindo sozinho | Soltar o corpo/sentar sem controlar o oponente antes — entrega queda por cima |
| **M6.2** Queda de mão única (single leg) | Nível baixo por flexão de joelho, não por curvar a coluna | Ensinar a entrada curvando a coluna em vez de flexionar os joelhos |
| **M6.3** Queda de dois pés (double leg) | Mudança de nível e penetração real com o passo; levar o parceiro ao chão com controle, nunca projetar | Mostrar projeção sem controle do parceiro na queda — risco de lesão |
| **M6.4** Sprawl e defesa de queda | Jogar o quadril para baixo e as pernas para trás | Ensinar a reagir à entrada recuando ou levantando o quadril |

### Módulo 7 — Finalizações Encadeadas

| Nó | O vídeo precisa cobrir | Descarte se |
|---|---|---|
| **M7.1** Kimura: da guarda, do 100kg e das costas | A mesma figura-quatro funcionando nas três posições; valor como controle mesmo sem a torção completa | Mostrar só uma das três posições, ou soltar a figura-quatro assim que não finaliza de primeira |
| **M7.2** Front headlock e guilhotina | O front headlock como o que antecede a guilhotina, nascendo do sprawl — encadeamento, não técnica isolada | Ensinar a guilhotina do zero, contra qualquer postura, sem o front headlock antes |
| **M7.3** Defesa de guilhotina | Aliviar a pressão no pescoço antes de tentar sair; ganhar o lado correto | Ensinar a puxar a cabeça para fora antes de aliviar a pressão |
| **M7.4** Encadeamento triângulo → armlock → omoplata | As três como sistema — a defesa de uma abre exatamente uma das outras duas | Ensinar as três como técnicas isoladas, sem mostrar a transição entre elas |

### Módulo 8 — Jogo Integrado

| Nó | O vídeo precisa cobrir | Descarte se |
|---|---|---|
| **M8.1** Encadeamento de raspagens | A segunda raspagem nascendo da defesa da primeira (ex.: tesoura → pêndulo), não duas técnicas soltas | Mostrar raspagens isoladas sem encadeamento, ou insistir na mesma já defendida |
| **M8.2** Leitura de pressão e antecipação de reação ⚠️ | Agir na intenção/pressão do oponente antes da ação estar completa | Conteúdo que só ensina reagir à ação já consumada, sem tratar leitura antecipada |
| **M8.3** Construção de jogo A-B-C (game plan pessoal) ⚠️ | — | — |

> **M8 é o bloco mais difícil, e M8.2 e M8.3 são os nós mais prováveis de ficar sem candidato à
> altura.** O próprio `concept` de M8.2 diz que a leitura de pressão "amadurece com repetição, não
> com mais um vídeo assistido", e o de M8.3 diz que jogo pessoal "não se pode copiar de um vídeo,
> porque depende do seu corpo". Um vídeo genérico de "como ler pressão" ou "como montar seu jogo" que
> não reconheça isso está vendendo o que o próprio nó nega — `video: null` nesses dois é o resultado
> esperado, não uma lacuna a forçar.

## Complementares: outros critérios, de propósito

Introduzidos pela **D32**. O `video` do nó é a referência que ensina; `extraVideos` são clipes que
ajudam a lembrar dela. Aplicar a esses clipes os critérios de canônico da seção acima **reprovaria
todos** — e reprovaria por motivos que, aqui, não são defeito.

| Critério | Canônico | Complementar |
|---|---|---|
| Ensina o conceito do nó | **exigido** | não — quem ensina é o canônico |
| Instrucional, não fragmento | **exigido** | fragmento serve; é o formato esperado |
| Canal estabelecido | preferido (risco de sair do ar) | não importa: o nó mantém o canônico se o clipe cair |
| Curto vence longo | sim, entre 6 e 25 minutos | nove segundos é normal, não sinal de má escolha |
| Gi (D6) | **exigido** | **exigido** — igual |
| Crédito ao canal (D7) | **exigido** | **exigido** — igual |
| Incorporável (D7) | **exigido** | **exigido** — igual |

O que um complementar precisa ser, então:

- **Gi**, como tudo no currículo (D6).
- **Reconhecível**: dá para ver o que está acontecendo. Clipe tremido, cortado no meio do movimento
  ou filmado de um ângulo que esconde a técnica não serve — não é pista de memória de nada.
- **Da minha academia, ou de fonte que eu confie.** O valor do clipe é a procedência: é a memória do
  treino que ele devolve. Vídeo aleatório da internet que não ensina o conceito **e** não é do meu
  treino não é complementar, é ruído.
- **Sobre a técnica do nó.** O encaixe pode ser mais frouxo que o do canônico, mas não inexistente:
  clipe bom que não corresponde a nenhum nó é clipe descartado, não nó novo.

**Descarte se:** for no-gi (D6), não der para saber o que está sendo mostrado, ou for aquecimento e
movimentação genérica sem técnica identificável.

O teto de **4 por nó** é aplicado pelo `CurriculumValidator`, não pela sua disciplina. Ele existe
porque o risco desta seção é conhecido e está registrado na D1: quanto mais vídeo por tela, menos a
tela é ferramenta de revisão e mais é catálogo.

```bash
node scripts/catalogar-video.mjs M1.3 --extra <url1> <url2>
node scripts/catalogar-video.mjs M1.3 --extra <url> --note "o giro para o lado da cabeça"
```

A `note` é o único campo que você escreve: título e canal continuam vindo do oEmbed, e a
`orientation` é detectada pelo script. Vale a pena preenchê-la — os títulos são taquigrafia de aula
("aula do dia 09/05") e não dizem por que aquele clipe importa para aquele nó.

## Estado atual (M0 e M1)

Os 11 nós estão catalogados. O que foi verificado e o que não foi está em D21 de
`07-decisoes.md` — em resumo: id, título, canal e permissão de incorporação vieram do YouTube;
o encaixe pedagógico foi julgado sem assistir aos vídeos por inteiro.

Os onze são, sem exceção, de canal em inglês — o critério de idioma (D45) chegou depois desta leva,
e nenhum foi trocado retroativamente (troca por troca não é ganho). **M0.3** é o candidato a
revisita de maior prioridade se aparecer equivalente em português claramente melhor: é o nó mais
sensível do currículo, e é onde a barreira do idioma mais pesa.

| Nó | Canal | Idioma | Duração | Observação |
|---|---|---|---|---|
| M0.1 | Keenan Cornelius | Inglês | 23:04 | O mais longo da leva; formato de lista, mas merece um `--start` depois de assistir |
| M0.2 | Knight Jiu-Jitsu | Inglês | 6:36 | — |
| M0.3 ⚠️ | Ritchie Yip | Inglês | 1:41 | Enquadra o tap como segurança do parceiro e cita o tap verbal; escolhido por ser o mais conservador. Candidato a revisita (D45) se surgir equivalente em português à altura |
| M0.4 | SBG PDX (Matt Thornton) | Inglês | 3:42 | Base e postura como conceito; não confirmado que percorre as três bases do nó |
| M0.5 | RVV BJJ | Inglês | 8:05 | Gola, manga e calça em capítulos |
| M1.1 | Jordan Teaches Jiujitsu | Inglês | 5:32 | "T-rex arms" = cotovelos colados; o exemplo é meia-guarda |
| M1.2 | Ritchie Yip | Inglês | 4:36 | — |
| M1.3 | Stephan Kesting | Inglês | 8:11 | — |
| M1.4 | Stephan Kesting | Inglês | 5:20 | Cobre por que a upa falha sem o braço preso; não cobre o elbow escape |
| M1.5 | Jordan Teaches Jiujitsu | Inglês | 8:33 | Gi; conceitos de barreira de pernas, não "voltar à guarda fechada" |
| M1.6 ⚠️ | Matt Arroyo Jiu Jitsu | Inglês | 11:15 | Falta confirmar que ensina pescoço antes de ganchos — a regra de descarte deste nó |

### Complementares catalogados (primeira leva, D32)

Sete clipes do canal Guiabasicodejiujitsu, todos verticais e todos Gi. Entraram nos nós de M1 que
**já têm canônico**, que é onde a tela mostra a hierarquia inteira — o vídeo que ensina em cima, os
clipes que lembram embaixo.

| Nó | Clipes | Observação |
|---|---|---|
| M1.3 | 3 | `AkQxiCrsGo4`, `9wEhZ1PdoMI`, `_sZ--Clk218` — saída de 100kg com giro |
| M1.5 | 2 | `7JozpLcKQvc`, `3ZlMiI92TjU` — reposição contra joelho na barriga |
| M1.6 | 2 | `TmzSPs_92bk`, `qHK0HembKi8` — saída da pegada das costas |

**Mesma ressalva da D21, e ela vale aqui também:** id, título, canal, orientação e permissão de
incorporação vieram do YouTube; o encaixe foi julgado por título e thumbnail, **sem assistir**. A
diferença é o custo de corrigir isso — os 37 clipes da #41 somam 8min36s, então conferir a leva
inteira cabe em nove minutos, contra as horas que a mesma conferência custa no canônico.

Nenhuma `note` foi escrita ainda, pelo mesmo motivo: escrever "por que este clipe importa" exige
ter assistido.
## Registro

Ao catalogar, o script grava em `backend/src/main/resources/curriculum/m{n}.json`:

```jsonc
"video": {
  "youtubeId": "...",
  "title": "...",     // vindo do YouTube, não digitado
  "channel": "...",   // vindo do YouTube, não digitado
  "startSeconds": 190 // opcional
}
```

Mudança de currículo é PR revisável (D11) — inclusive troca de vídeo.

## Manutenção

Vídeo do YouTube sai do ar, vira privado ou tem a incorporação desativada depois de catalogado —
risco listado em `07-decisoes.md`. Sem checagem, a descoberta é acidental e acontece no pior
momento possível: abrindo o nó depois do treino.

```bash
node scripts/verificar-videos.mjs
```

O script lê todos os `m*.json`, pega os nós com `video` preenchido e pergunta ao YouTube, um a um,
se cada id continua público e incorporável — a mesma consulta que a catalogação faz, do mesmo
módulo (`scripts/lib/youtube.mjs`). Nós com `video: null` são ignorados, e um currículo inteiro sem
vídeo produz "nada a verificar", não erro. **Não altera arquivo nenhum:** trocar um vídeo é escolha
humana, com os critérios deste documento.

O código de saída distingue três desfechos, e é por ele que o workflow decide o que fazer:

| Código | Significado |
|---|---|
| `0` | Todos disponíveis — inclusive o caso "nenhum vídeo catalogado ainda" |
| `1` | Há vídeo indisponível ou não-incorporável: alguém precisa escolher outro |
| `2` | Não deu para verificar (rede, YouTube fora). Não é conclusão sobre o currículo |

A distinção entre `1` e `2` é o que impede que instabilidade de rede vire aviso falso de vídeo
quebrado.

### O aviso automático

O workflow [`videos.yml`](../.github/workflows/videos.yml) roda a verificação **toda segunda-feira
às 06:00 UTC** e também sob demanda pela aba *Actions* (`workflow_dispatch`). Encontrando problema,
ele abre uma issue com o relatório — ou atualiza a que já estiver aberta, em vez de abrir uma nova a
cada semana — e comenta só quando o relatório muda. Quando todos voltam a estar disponíveis, a issue
é fechada sozinha.

O job `videos` **não é required check de `main`** (`09-regras-repositorio.md`), de propósito: um
vídeo fora do ar é problema de curadoria e não pode travar o merge de código. Ele nem roda em pull
request — a agenda é o gatilho.

Para conferir o formato do aviso sem esperar a segunda-feira, dispare o workflow manualmente em
*Actions → videos → Run workflow*.
