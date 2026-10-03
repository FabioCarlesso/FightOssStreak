# D45 — Vídeo canônico: entre candidatos equivalentes, ganha o em português — desempate, não passe livre

## Justificativa

Os 11 canônicos catalogados até aqui (M0 e M1) são de oito canais e nenhum fala português, e isso
não foi escolha: `docs/conteudo/videos.md` nunca teve critério de idioma, e o inglês entrou por ser
onde havia mais material catalogado. O custo aparece exatamente onde o produto promete valor —
revisão depois do treino, no celular, em português, do que acabou de ser explicado em português no
tatame — e pesa mais em M0.3, o nó mais sensível do currículo (D-tap), onde entender "tap early, tap
often" com meia certeza é diferente de entender a frase na própria língua. Há também argumento de
vocabulário: raspagem, passagem, pegada, montada e reposição são os termos que os nós e o `concept`
já usam, e só vídeo em português usa os mesmos termos.

**A regra é desempate, não substituição do resto do crivo**: entre dois vídeos que atendem *todos*
os outros critérios de `docs/conteudo/videos.md` (Gi, instrucional, canal estabelecido, ensina o
conceito), ganha o português; vídeo em português que é highlight, no-gi (D6) ou que não ensina o
conceito continua descartado igual a qualquer outro. Onde não existir português à altura,
cataloga-se o inglês e o nó entra como candidato a revisita na tabela de estado atual — nó sem vídeo
é pior que nó com vídeo em inglês.

**Sem campo `language` no schema**: um enum novo em `VideoRef` custaria migration, OpenAPI e tipos
gerados para responder uma pergunta que a curadoria já responde na hora de escolher; se o app um dia
precisar filtrar por idioma na tela, é issue própria, com motivo próprio.

**Sem trilha dupla** (canônico + equivalente em português por nó): dobraria o gargalo real do
projeto (curadoria de vídeo) e poria escolha numa tela cujo propósito é remover escolha. A
preferência vale só para o **canônico**; os complementares (D32) já são material da própria academia
do autor, logo já em português, e não precisam de regra nova. Dois canais em português entraram
triados pela régua de `docs/conteudo/videos.md` e estão listados em `docs/conteudo/fontes.md`:

**FEU BJJ** (936 mil inscritos, aulas em capítulos além dos podcasts recentes) e **Lawrence Luna /
@lawluna** (34 mil inscritos, curso completo em vídeo-aulas encadeadas, formato que casa bem com
"ensinar o conceito"). Um terceiro lead da abertura do épico, **Jiu Jitsu Channel** (@jiu-jitsu),
foi triado e **reprovado**: descrição do canal em inglês ("BJJ & No-Gi Training"), uploads recentes
são clipe de competição/reação ("Tiago vs Lincoln REMATCH", "KID CELEBRATES LIKE A CHAMPION") —
falha em instrucional-não-highlight e em no-gi (D6) ao mesmo tempo, além de não ser efetivamente
canal em português

## Revisar quando

Se a curadoria em português forçar canal fraco ou material pedagogicamente pior que o equivalente em
inglês disponível — aí a preferência recua para "só decide em empate técnico" e nada além disso. Ou
se `VideoRef` precisar mesmo de um campo de idioma no produto — decisão própria, não consequência
desta

---

[Índice das decisões](../README.md)
