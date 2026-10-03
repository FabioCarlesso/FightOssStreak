# D40 — Fontes de conteúdo registradas em `docs/conteudo/fontes.md`, curadoria e não campo de produto

## Justificativa

O currículo inteiro (46 conceitos, 91 perguntas) foi escrito sem registro de fonte — risco aceito na
D12 enquanto o uso é pessoal. O épico #55 multiplica o volume por três ou quatro, e nessa escala
**erro pedagógico deixa de ser localizável** (não dá para saber quais outros nós beberam da mesma
fonte errada) e **texto de terceiro passa a poder entrar no repositório sem ninguém ter decidido que
entrou** — o projeto é rígido com direito de vídeo (D7) e frouxo com texto, sem motivo para a
diferença. `docs/conteudo/fontes.md` fixa uma ordem de preferência (professor e treino,
livro-referência, canal instrucional estabelecido — mesmo crivo da `docs/conteudo/videos.md` —, regulamento oficial,
enciclopédia só para nomenclatura), o que **não** conta como fonte (fórum, rede social, resumo de
IA, blog sem autor identificável, vídeo de highlight) e uma regra sem ambiguidade para texto de
terceiro: escreve-se com as próprias palavras, citação literal é curta e creditada, e tradução de
trecho alheio continua sendo texto de terceiro.

**É documento de curadoria, não campo de produto**: nó exibindo bibliografia seria o app parecendo
curso (D1), então a tabela `nó → fontes` fica fora do JSON e da tela do nó.

**Não fecha o risco da D12** — só torna o rastro auditável a partir de agora: os oito canais já
usados nos canônicos de M0/M1 entraram na lista porque já foram julgados uma vez, e a tabela nasce
vazia de M2 a M8, que é estado normal, igual a `video: null`

## Revisar quando

Ao validar o currículo com um faixa-preta (D12) — aí o documento vira insumo da revisão, não só
rastro; ou se a tabela `nó → fontes` começar a ficar visivelmente atrasada em relação ao que B e C
de fato usaram, sinal de que registrar deixou de acontecer no fluxo de trabalho

---

[Índice das decisões](../README.md)
