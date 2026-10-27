# D73 — O CSS do web é dividido por área, em ordem de import fixa; CSS Modules só se uma tela nova pedir

## Justificativa

`web/src/styles.css` chegou a ~2.900 linhas num arquivo só: base, as telas do app, a landing, as
telas de fronteira, o admin e, no fim, as regras responsivas de todas elas. Achar o estilo de uma
tela exigia busca, e todo PR visual mexia no mesmo arquivo — conflito garantido e nenhuma pista do
que uma mudança alcança (#184).

### Um arquivo por área, importados em `main.tsx`

`web/src/styles/` tem um arquivo por área, nenhum acima de ~600 linhas: `base.css` (reset,
tipografia, botões, foco, card e os utilitários `.linklike`, `.link-button`, `.button-link` e
`button.danger`), `layout.css` (cabeçalho, navegação, rodapé, conta no topo e as faixas de
demonstração), `home.css`, `arvore.css`, `no.css` (inclui vídeo, clipes, anotação e histórico),
`revisao.css` (quiz, drill e sessão guiada), `auth.css` (portão, login, cadastro e conta),
`landing.css`, `feedback.css`, `admin.css` (usuários, painel e saúde) e `diario.css`.

**Todos são importados em `main.tsx`, depois do `tokens.css`, e não pela página.** O Vite junta o
CSS na ordem dos imports, e import por página faria a ordem da cascata depender da ordem em que os
módulos das rotas são avaliados — um detalhe do grafo de imports que ninguém lê como decisão de
estilo. A ordem em `main.tsx` é a do arquivo antigo, e é parte do contrato: um arquivo sobrescreve
os anteriores quando a especificidade empata. Área nova entra no fim da lista.

**A `@media` mora no arquivo da área que ela ajusta**, no fim dele. Isso revisita só a metade
"concentrado" da D26: os dois breakpoints (640px e 380px) e o alvo de toque por `pointer: coarse`
continuam os mesmos, agora repetidos por área. O bloco único no fim do arquivo vencia tudo por
posição; repartido, cada pedaço passou a vir antes das áreas seguintes. A divisão foi conferida por
script contra o arquivo antigo: as 454 combinações de seletor e regra são as mesmas, sem perda nem
duplicata, e de todos os pares cuja ordem relativa mudou, só quatro tinham mesma especificidade,
propriedade em comum com valor diferente e alvo possivelmente comum — e os quatro vivem em rotas
que nunca se encontram (landing × cabeçalho do app, landing × clipes, diário × sessão guiada).

### CSS Modules: avaliado, não adotado agora

O Vite suporta `*.module.css` sem configuração, e o escopo por arquivo resolveria de vez a colisão
de nome. Não migramos porque: as classes BEM já dão escopo por convenção e não colidiram até aqui;
migrar reescreveria todo `className` do web sem mudar nada que se veja; e os testes de UI e o
`scripts/capturar-prints.mjs` selecionam elementos pelo nome da classe, que o módulo trocaria por
um hash. **Tela nova pode nascer em CSS Module**, se quem a escrever preferir — o arquivo fica ao
lado do componente, e o global continua valendo para `base.css` e `layout.css`. O que não vale é
converter tela existente de passagem num PR que não seja sobre isso.

## Revisar quando

- Um arquivo de área passar de ~600 linhas: divide-se de novo, pela tela.
- Duas telas colidirem num nome de classe, ou a ordem de import virar fonte de defeito: aí CSS
  Modules deixa de ser opcional para telas novas e passa a ser a regra.
- Entrar code-splitting por rota com CSS carregado sob demanda: a ordem deixa de ser só a de
  `main.tsx`, e a regra de importar tudo ali precisa ser revista.

---

[Índice das decisões](../README.md)
