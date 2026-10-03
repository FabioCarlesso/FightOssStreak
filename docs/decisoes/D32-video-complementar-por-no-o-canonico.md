# D32 — Vídeo complementar por nó: o canônico ensina, o clipe lembra

## Justificativa

A #41 chegou com 37 vídeos "para os 35 nós vazios de M2–M8", e a consulta ao YouTube desmontou a
premissa: são **clipes de aula de 7 a 29 segundos** (mediana 12s, 8min36s somando os 37), Shorts
verticais 720×1280, todos de um canal só, agrupados em 12 dias de aula — e com ~11 grupos quase
duplicados, seis de título idêntico, o que reduz 37 vídeos a ~23 assuntos. Como **canônico** eles
reprovam em dois critérios de `docs/conteudo/videos.md`: não são instrucionais e não ensinam o conceito, que é o que a D1
pede. Como **complementar** os mesmos fatos deixam de ser defeito e dois viram vantagem: a duplicata
passa a ser ângulo alternativo do mesmo golpe, e a concentração de canal fica contida porque o nó
mantém o canônico se o canal cair. O argumento decisivo é de produto: os clipes são **da própria
academia**, e para uma ferramenta de retenção a pista de recuperação mais forte não é o vídeo que
explica melhor, é a memória do próprio treino.

**O que isto não resolve:** nenhum canônico de M2–M8 — a curadoria daqueles 35 nós continua sendo
trabalho separado, e a promessa da issue de "resolver tudo sem uma linha de código" morreu com os
dados.

**Forma:** lista `extraVideos` opcional no nó, e não `videos[]` substituindo o campo único — o campo
separado documenta a hierarquia no próprio dado e não migra os 11 nós já catalogados. Dois campos
que o canônico não tem: `orientation`, porque clipe de celular é 9:16 e o frame do canônico é 16:9,
detectada pelo script a partir do formato real (nunca digitada); e `note`, a anotação de quem
catalogou, o **único** campo do bloco que não vem do YouTube — os títulos são taquigrafia de aula
("aula do dia 09/05") e não dizem por que o clipe importa.

**Teto de 4 por nó no validador**, não em documento: regra que mora só em doc se esquece na hora de
catalogar o quinto clipe, e o risco que ela contém é o de a tela virar catálogo.

**Complementar sem canônico é permitido**: exigir o canônico encodaria a hierarquia no dado, mas
travaria os clipes atrás da curadoria inteira de M2–M8, e o estado vazio do canônico já é honesto na
tela.

**Na UI, tira de miniaturas e não `<details>` recolhido**: cada player do YouTube puxa ~1 MB de
JavaScript, então o `<iframe>` só nasce no clique — mas o acordeão resolveria o peso matando o
recurso por outro caminho, porque ninguém expande um bloco para descobrir se vale ver nove segundos.
A url do embed leva `autoplay` e `loop`, defensáveis só juntos com esse carregamento tardio: o
iframe nasce do clique, então o autoplay atende o gesto que acabou de acontecer, e um clipe de nove
segundos em loop é visto três vezes sem ninguém tocar em nada

## Revisar quando

Se a tira passar a ser o que se olha primeiro e o canônico virar decoração — aí a pergunta não é
sobre o campo, é se o nó ainda precisa de vídeo de referência (D1). E se algum dia entrar
complementar que **não** seja da própria academia, revisar o título da seção, que hoje nomeia
procedência

---

[Índice das decisões](../README.md)
