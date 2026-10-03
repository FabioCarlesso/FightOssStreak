# Prints da landing: histórico de recapturas

Registro do que cada recaptura mudou e por quê. O procedimento está em
[`prints-da-landing.md`](prints-da-landing.md).

> ℹ️ **A recaptura ficou bloqueada da #24 até a #58, e agora está destravada** — pelo caminho que
> a versão anterior deste documento já prescrevia, não por um atalho. O app exige login (D36/D37) e
> o script não tem como se autenticar sozinho: ele semeia pela API com `fetch` e sobe um Chrome com
> perfil novo, e os dois levavam `401`.
>
> **O que resolveu:** o script passou a **receber** uma sessão já obtida, em `FOS_PRINT_COOKIE` —
> aplicada no `fetch` da semeadura (mais o cabeçalho `X-XSRF-TOKEN`, que o
> `CookieCsrfTokenRepository.withHttpOnlyFalse()` cobra) e no Chrome via `Network.setCookie` do CDP.
> Continua valendo o que já estava escrito aqui: é caminho de operador, com login de verdade, e
> **não** vale criar um modo que desliga o portão para capturar tela — seria porta dos fundos
> permanente para economizar dez imagens. Sem a variável o script roda como antes e falha em `401`.
>
> **Defasagem zerada na #58:** os nove arquivos foram recapturados. O cabeçalho voltou a bater com
> o app — com o nome da conta e o botão *Sair* da #24 — nos três prints que de fato o mostram
> (`no-desktop`, `hoje-desktop`, `hoje-mobile`); nos outros o enquadramento é ancorado abaixo dele,
> e por isso a defasagem nunca chegou a aparecer ali. `arvore-desktop` e `arvore-mobile` saíram
> **byte a byte idênticos** aos anteriores, o que é um bom sinal: a captura é determinística, e a
> árvore não exibe conceito nenhum.
>
> **O `og.jpg` era o mais defasado de todos, e ninguém tinha percebido** — justamente o arquivo que
> nenhuma tela do app contém e que é a primeira coisa que se vê ao receber o link. Ele ainda trazia
> o hero anterior ao login: botão *Abrir o app* e a linha "**Sem cadastro** e sem cobrança". Desde a
> #24/#52 a página diz *Pedir acesso* e "acesso sob aprovação" — e `LandingPage.tsx` tem comentário
> explicando que a troca foi feita porque "copy que promete o que o produto não entrega é o defeito
> que esta página existe para não ter". A prévia de link seguia fazendo exatamente essa promessa.
>
> **A #82 mexeu no hero de novo, e só nele.** Com o cadastro aberto (D47/D48) o botão virou *Criar
> conta* e a linha passou a ser "Cadastro aberto e sem cobrança: e-mail e senha, ou a conta do
> Google" — a promessa mudou, então o `og.jpg` foi refeito no mesmo PR. Os oito prints de tela do app
> **não** foram recapturados: nada em `/arvore`, `/no/*` ou `/hoje` mudou nessa fatia, e recapturar
> por via das dúvidas só troca bytes idênticos por bytes idênticos. A tela de entrada mudou muito, e
> não é print da landing — ela vive atrás do botão, não dentro da página.

> **A #99 acrescentou uma linha ao cartão de streak, e por isso mexeu em três arquivos.** O saldo
> de freeze (D55) aparece em `/hoje`, então `hoje-desktop` e `hoje-mobile` foram refeitos — e o
> `og.jpg` junto, porque a prévia de link **contém a tela `/hoje`** dentro do celular do hero: é o
> arquivo que mais parece independente do app e o que mais silenciosamente envelhece com ele.
> `arvore-*` e `no-*` saíram byte a byte idênticos, de novo. `drill-*` mudou, mas só na **data da
> anotação semeada** (a semeadura é relativa a hoje), então foram restaurados — recapturar por isso
> só trocaria uma data por outra em arquivo que a #99 não tocou.
>
> **Duas armadilhas custaram uma recaptura inteira aqui, e valem para a próxima.** A primeira: as
> contas de `scripts/seed-dev-users.mjs` se chamam *Aluno Teste* e *Dono Teste*, e o cabeçalho
> aparece em `hoje-*` e `no-*` — capturar com elas põe um nome de conta de teste na página pública.
> O `display_name` foi ajustado para *Autor* no banco local, que é o que os prints anteriores
> mostram. A segunda, do mesmo tipo do [aviso da
> demonstração](prints-da-landing.md#como-obter-a-sessão): **`dono@teste.local` é
> `ADMIN`**, e capturar com ela põe *Painel* e *Usuários* na barra de navegação — itens que a
> maioria de quem chega pela landing nunca vai ver. Capture com a conta de aluno.

> **A #114 mudou `/hoje` e a barra de navegação, e trouxe uma tela nova — tudo recapturado.** O
> diário (D56) acrescentou um cartão **abaixo** da agenda em `/hoje` e um item *Diário* no
> cabeçalho, e a própria tela `/diario` entrou na landing como quinto passo. Foram refeitos:
> `hoje-desktop` e `hoje-mobile` (cartão novo **e** cabeçalho), `no-desktop` (só o cabeçalho, que é
> o único print além de `hoje-*` que o mostra), o `og.jpg` (contém a tela `/hoje` dentro do celular
> do hero) e `drill-desktop`/`drill-mobile` — estes por um motivo que não existia antes: o
> histórico do nó ganhou o link *ver o treino* quando o drill veio de uma sessão, e a semeadura
> agora registra duas técnicas por lá. Nasceram `diario-desktop` e `diario-mobile`.
>
> `arvore-mobile` e `no-mobile` saíram com bytes diferentes e foram **restaurados**: nada nessas
> telas mudou, e a diferença é ruído de compressão e de miniatura do YouTube. É a mesma decisão da
> #99 — trocar bytes idênticos por bytes idênticos não é recaptura, é churn.
>
> **A pendência declarada na primeira fatia da #114 está paga aqui**, no mesmo PR, pelo caminho
> prescrito nesta página: Postgres do Compose, `seed-dev-users.mjs`, `mint-dev-login.mjs` e uma
> sessão de verdade em `FOS_PRINT_COOKIE`. O portão nunca foi desligado.

> **A #102 acrescentou o heatmap ao cartão de streak, e por isso mexeu em três arquivos.** A grade
> de seis meses (D59) aparece em `/hoje`, então `hoje-desktop` e `hoje-mobile` foram refeitos — e o
> `og.jpg` junto, pelo motivo de sempre: a prévia de link **contém a tela `/hoje`** dentro do
> celular do hero. `arvore-mobile` e `no-mobile` saíram com bytes diferentes e foram
> **restaurados**, pela mesma régua da #114: nada nessas telas mudou, e trocar bytes por bytes não
> é recaptura.
>
> **A recaptura pegou um defeito que teste nenhum pegaria, e é a razão de esta página existir.** No
> celular a grade abria rolada no passado remoto — meio ano de células vazias — e a semana com
> treino ficava fora da tela, à direita. O print saiu literalmente em branco. O conserto está no
> componente (o invólucro abre rolado até o fim), e sem refazer o print ninguém teria olhado.
