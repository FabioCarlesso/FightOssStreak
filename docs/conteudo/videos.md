# Curadoria de Vídeos

Etapa 3 de `docs/produto/mvp-web.md`. É o gargalo real do projeto (`docs/arquitetura.md`): mapear cada
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
- **Prefira canal estabelecido.** [Risco conhecido](../decisoes/README.md#riscos-conhecidos-em-aberto): vídeo sai do ar e o nó fica
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
[risco listado](../decisoes/README.md#riscos-conhecidos-em-aberto). Sem checagem, a descoberta é acidental e acontece no pior
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

O workflow [`videos.yml`](../../.github/workflows/videos.yml) roda a verificação **toda segunda-feira
às 06:00 UTC** e também sob demanda pela aba *Actions* (`workflow_dispatch`). Encontrando problema,
ele abre uma issue com o relatório — ou atualiza a que já estiver aberta, em vez de abrir uma nova a
cada semana — e comenta só quando o relatório muda. Quando todos voltam a estar disponíveis, a issue
é fechada sozinha.

O job `videos` **não é required check de `main`** (`docs/repositorio.md`), de propósito: um
vídeo fora do ar é problema de curadoria e não pode travar o merge de código. Ele nem roda em pull
request — a agenda é o gatilho.

Para conferir o formato do aviso sem esperar a segunda-feira, dispare o workflow manualmente em
*Actions → videos → Run workflow*.

## Onde está o resto

- [`videos-por-no.md`](videos-por-no.md) — o que o vídeo de cada nó precisa mostrar
- [`videos-catalogo.md`](videos-catalogo.md) — o que já está catalogado e o que falta conferir
