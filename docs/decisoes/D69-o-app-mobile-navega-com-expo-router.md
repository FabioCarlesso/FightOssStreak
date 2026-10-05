# D69 — O app mobile navega com Expo Router, e o que web e app mostram iguais mora em `shared/domain`

## Justificativa

A #141 trouxe as telas do MVP mobile, e com elas duas escolhas que valem por muito tempo.

### Navegação: Expo Router

O Expo Router é o padrão do Expo: rotas por arquivo em `mobile/app/`, montadas sobre o React
Navigation. Funciona no Expo Go, dá deep link sem configuração extra (o link que um dia abrir o app
direto num nó) e é o caminho que a documentação do Expo segue. O React Navigation declarado em código
era a alternativa, mais explícita e sem convenção de pasta, mas deep link fica manual.

Os **portões** (versão mínima, conta, aviso) não são rotas: ficam no layout do grupo `(app)` e
mostram a tela própria no lugar do app, como o `AuthGate` do web. A raiz só monta provedores e o
`Slot`, para o roteador estar pronto desde o primeiro quadro. As telas moram em `src/screens/`, e
cada arquivo de `app/` só liga o endereço à tela.

**O custo descoberto no emulador:** as abas do Expo Router não desmontam. A tela carregada uma vez
continuava com o dado velho — o streak em 0 depois de registrar um drill. Hoje, Árvore e Diário
recarregam ao voltar o foco (`useAoVoltar`).

### Texto e rótulos: uma cópia só, em `shared/domain`

O critério da #141 é que nenhuma regra seja reimplementada no app. Ao escrever as telas apareceram
quatro coisas que não são regra de negócio, mas que o web guardava dentro de si e que o app teria
que copiar:

- o texto do aviso de responsabilidade (`web/src/content/disclaimer.ts`);
- os rótulos de auto-avaliação, tipo de sessão e sensação, e as datas de calendário do diário;
- a conversão do formulário de sessão para o corpo da requisição (`web/src/state/sessionFields.ts`);
- a prévia de "volta em N dias" do drill, que estava dentro do componente do web.

Copiá-las criaria duas verdades. O aviso é o caso mais caro: o aceite é **por versão do texto**
(regra 5), e duas cópias que divergissem fariam a mesma versão significar textos diferentes conforme a
tela. Por isso as quatro passaram para `shared/domain`, e o web reexporta de lá sem mudar nenhum
import das telas. O `@fos/domain` deixa de ser só "regras puras" e passa a ser também o que os dois
clientes precisam mostrar iguais — sempre sem UI, sem rede e sem React.

**O que não foi junto:** o `useAsync` e os componentes. São React e não cabem em `shared/`, que é
agnóstico de UI. O `useAsync` do app é uma cópia curta do web, com o mesmo comportamento, e diz isso
no comentário.

## Revisar quando

- Se a navegação do app passar a precisar de algo que o Expo Router atrapalhe (fluxo modal complexo,
  navegação aninhada que as convenções de pasta não expressem bem).
- Se `shared/domain` começar a receber componente, hook ou dependência de plataforma. Aí o que é
  "mostrar igual" vira pacote próprio, e o domínio volta a ser só regra.
- Se o app e o web divergirem de propósito em algum rótulo. A divergência entra como decisão, não
  como cópia editada

---

[Índice das decisões](../README.md)
