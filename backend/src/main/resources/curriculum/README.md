# Currículo como dado versionado

Fonte da verdade da árvore de currículo (ver `docs/04-arvore-curriculo-bjj.md` e decisão **D11**).
Estes arquivos são ingeridos no banco na subida da aplicação — **nada aqui é hardcoded em Java**.

Alterar a árvore é editar JSON e abrir PR. É isso que torna a futura validação por um faixa-preta
um diff legível em vez de um dump de SQL.

## Arquivos

Um arquivo por módulo (`m0.json` … `m8.json`), mais o índice `modules.json` que define a ordem.

## Formato de um nó

```jsonc
{
  "code": "M1.2",              // chave estável — é o node.code do banco
  "title": "Shrimping / fuga de quadril",
  "belt": "BRANCA",            // BRANCA | AZUL | ROXA — calibra dificuldade, não trava acesso
  "order": 2,
  "unlockRule": "ALL",         // ALL (padrão) | ANY — ver nota abaixo
  "prereqs": ["M1.1"],         // arestas do grafo; podem cruzar módulos
  "concept": "...",            // o *porquê* da posição, não o passo a passo
  "video": null,               // null = ainda não catalogado (ver abaixo)
  "extraVideos": [],           // opcional — clipes complementares (ver abaixo)
  "quiz": []                   // 3–5 perguntas conceituais
}
```

### `concept`

O *porquê* da posição — a única parte do nó que funciona sem rede, sem vídeo e em trinta segundos
no vestiário. Não é passo a passo: "coloque o pé aqui, depois gire" é aula, e ensinar é o que a D1
proíbe. O padrão de escrita tem três movimentos, na ordem:

1. **O problema que a posição resolve** — a tese, em uma frase.
2. **O mecanismo** — por que funciona; qual troca o oponente é obrigado a fazer.
3. **O erro comum** — o que costuma dar errado e por quê.

Os 46 nós seguem essa estrutura (issue #58) — é o padrão a seguir ao escrever ou revisar qualquer
nó novo.

**Faixa de tamanho: 450 a 900 caracteres.** O piso é o que M0/M1 já entregavam antes da reescrita
de #58; o teto é o que ainda cabe numa tela de celular sem rolagem longa.

**Proibido**: enumeração de passos, imperativo de execução ("coloque", "gire", "puxe" descrevendo
a sequência), contagem de tempo, e qualquer coisa que só faça sentido com o vídeo aberto.

**Permitido e desejável**: nomear o erro comum, dizer o que o oponente faz em resposta, e ligar o
nó a outro nó do currículo pelo código — a árvore existe justamente para isso.

**Parágrafos**: linha em branco (`\n\n`) no JSON vira parágrafo novo na tela do nó
(`NodePage.tsx`). No máximo 3 parágrafos, um por movimento do padrão — mais que isso é a lição
voltando pela porta dos fundos. Sem markdown: negrito, lista e link abrem a porta para o texto que
este padrão proíbe.

`CurriculumValidator` recusa conceito com mais de 3 parágrafos, para o currículo inteiro, e fora da
faixa de 450–900 caracteres para os módulos em `CONCEPT_LENGTH_CURATED_MODULES` — hoje os 9
módulos, porque os 46 nós foram reescritos juntos. A constante continua existindo, e não virou um
booleano: um módulo novo (`M9` em diante) nasce fora dela por padrão, até ser escrito no padrão e
entrar no conjunto. Ver **D41**, **D42** e **D43** em `docs/07-decisoes.md` — a última é quem explica
por que M2–M8 entraram juntos, num PR só, em vez do rollout módulo a módulo que a D42 previa.

Se "não é passo a passo" não é automatizável por heurística de palavra — fica como item de revisão
humana do PR, não do validador.

### `unlockRule`

O desbloqueio padrão é **ALL**: todos os pré-requisitos concluídos. `docs/04` descreve o Módulo 4
como "pré-requisito: M3.2 **ou** M3.3 (qualquer passagem)" — semântica de OU, que o modelo
`ALL` não expressa. Daí `unlockRule: "ANY"`, usado hoje só em `M4.1`. Registrado como **D13**.

### `video`

**M0 e M1 estão catalogados** (11 nós); de M2 a M8 os nós seguem com `video: null`, que é estado
normal — a curadoria é incremental. Catalogar é a etapa 3 de `docs/05-mvp-web-plano.md` e é
trabalho de curadoria humana: exige assistir e escolher. Inventar IDs de vídeo produziria
referências quebradas ou, pior, tecnicamente erradas.

Para catalogar, **use o script em vez de editar à mão** — ele busca título e canal no próprio
YouTube e recusa vídeo inexistente, privado ou com incorporação desativada:

```bash
node scripts/catalogar-video.mjs M1.2 https://www.youtube.com/watch?v=XXXXXXXXXXX
```

Os ids já gravados são reconferidos semanalmente por `scripts/verificar-videos.mjs` (workflow
`videos`), que avisa quando um vídeo sai do ar ou perde a permissão de incorporação.

Critérios de escolha por nó estão em `docs/08-curadoria-videos.md`. O formato gravado é:

```jsonc
"video": {
  "youtubeId": "xxxxxxxxxxx",
  "title": "Título original do vídeo",
  "channel": "Nome do canal",       // crédito visível é obrigatório (política D7)
  "startSeconds": 0                 // opcional
}
```

Antes de incluir, conferir a política de uso de vídeo em `docs/07-decisoes.md`: só embed do player
oficial, só vídeos públicos e incorporáveis, sempre com crédito ao canal.

### `extraVideos`

Campo **opcional** (**D32**). O `video` do nó é a referência que ensina o conceito; `extraVideos`
são clipes que ajudam a lembrar dele — hoje, trechos curtos gravados na própria academia. A
hierarquia é de produto, não de banco: **o canônico ensina, o clipe lembra**. Nó sem o campo, ou
com ele em `[]`, é o caso normal.

```bash
node scripts/catalogar-video.mjs M1.3 --extra <url1> <url2>
node scripts/catalogar-video.mjs M1.3 --extra <url> --note "o giro para o lado da cabeça"
```

```jsonc
"extraVideos": [
  {
    "youtubeId": "xxxxxxxxxxx",
    "title": "Título original",       // do YouTube, nunca digitado
    "channel": "Nome do canal",       // crédito visível é obrigatório (D7)
    "orientation": "VERTICAL",        // opcional; detectada pelo script, default HORIZONTAL
    "note": "..."                     // opcional; o único campo escrito por quem cataloga
  }
]
```

Regras que o validador aplica, além das do canônico:

- **No máximo 4 por nó.** Não é limite técnico — é o que impede a tira virar catálogo, que é o
  risco de diluir a revisão (D1).
- **Sem repetir** o canônico do nó nem outro complementar do mesmo nó.
- **O mesmo id pode servir a nós diferentes**, e isso é intencional: uma reposição contra joelho na
  barriga é pista de memória tanto do nó de recuperação quanto do de joelho na barriga.
- **Nó pode ter complementar sem ter canônico.** A tela mostra o estado vazio do canônico acima da
  tira, então o clipe não se disfarça de referência.

`orientation` existe porque clipe de celular é 9:16 e o frame do canônico é 16:9 — vertical em
frame horizontal vira tarja preta dos dois lados. O script detecta a partir do formato real do
vídeo; não preencher à mão.

### `quiz`

Preenchido para **M0 a M3** (25 dos 46 nós). M0 e M1 vieram primeiro por serem fundamento e
sobrevivência; M2 e M3 entraram em seguida porque são o que se alcança poucas semanas depois de
terminar M1 — sem eles o app perde a camada de retenção justo onde a progressão continua. De M4 a
M8 os nós seguem com `quiz: []`, e o app trata isso como "quiz ainda não escrito", sem quebrar
(**D15**).

As perguntas testam compreensão de conceito — *quando usar*, *qual o erro comum*, *o que o oponente
faz* — e nunca decoreba de sequência de passos.

M0 e M1 têm 3 a 4 perguntas por nó; **M2 e M3 têm 4**, por causa da nota de corte de 70
(`QuizService.PASSING_SCORE`): com 3 perguntas, um erro reprova; com 4, tolera-se um erro (75).
Registrado em **D27**.

## Validação

`CurriculumIntegrityTest` roda a cada `./mvnw test` e falha se houver: código duplicado, referência a
pré-requisito inexistente, ciclo no grafo, nó órfão de módulo, faixa inválida, quiz sem exatamente
uma alternativa correta, conceito com mais de 3 parágrafos ou fora da faixa de tamanho nos módulos
já cobertos, ou complementar duplicado, sem crédito de canal ou acima do teto. Detecção de ciclo é
feita **na ingestão**, nunca em runtime.
