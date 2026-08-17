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
  "quiz": []                   // 3–5 perguntas conceituais
}
```

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
pré-requisito inexistente, ciclo no grafo, nó órfão de módulo, faixa inválida ou quiz sem exatamente
uma alternativa correta. Detecção de ciclo é feita **na ingestão**, nunca em runtime.
