# Curadoria de Vídeos — M0 e M1

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

## Estado atual (M0 e M1)

Os 11 nós estão catalogados. O que foi verificado e o que não foi está em D21 de
`07-decisoes.md` — em resumo: id, título, canal e permissão de incorporação vieram do YouTube;
o encaixe pedagógico foi julgado sem assistir aos vídeos por inteiro.

| Nó | Canal | Duração | Observação |
|---|---|---|---|
| M0.1 | Keenan Cornelius | 23:04 | O mais longo da leva; formato de lista, mas merece um `--start` depois de assistir |
| M0.2 | Knight Jiu-Jitsu | 6:36 | — |
| M0.3 ⚠️ | Ritchie Yip | 1:41 | Enquadra o tap como segurança do parceiro e cita o tap verbal; escolhido por ser o mais conservador |
| M0.4 | SBG PDX (Matt Thornton) | 3:42 | Base e postura como conceito; não confirmado que percorre as três bases do nó |
| M0.5 | RVV BJJ | 8:05 | Gola, manga e calça em capítulos |
| M1.1 | Jordan Teaches Jiujitsu | 5:32 | "T-rex arms" = cotovelos colados; o exemplo é meia-guarda |
| M1.2 | Ritchie Yip | 4:36 | — |
| M1.3 | Stephan Kesting | 8:11 | — |
| M1.4 | Stephan Kesting | 5:20 | Cobre por que a upa falha sem o braço preso; não cobre o elbow escape |
| M1.5 | Jordan Teaches Jiujitsu | 8:33 | Gi; conceitos de barreira de pernas, não "voltar à guarda fechada" |
| M1.6 ⚠️ | Matt Arroyo Jiu Jitsu | 11:15 | Falta confirmar que ensina pescoço antes de ganchos — a regra de descarte deste nó |

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

Risco em aberto de `07-decisoes.md`: vídeos saem do ar. Vale rodar uma checagem periódica de
disponibilidade dos ids já catalogados. Não existe ainda — quando existir, o lugar natural é um job
no `backend.yml` que só avisa, sem quebrar o build.
