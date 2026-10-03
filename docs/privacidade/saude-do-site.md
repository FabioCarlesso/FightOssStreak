# Saúde do site (D54, #86)

O app mede **a si mesmo**: quantas requisições chegaram, com que status responderam e quanto tempo
levaram. É o que faz uma rota quebrada por deploy aparecer no mesmo quarto de hora, em vez de
aparecer quando alguém reclama.

Isto é um documento próprio e não um parágrafo dentro da [coleta de uso](coleta-de-uso.md) porque a pergunta é outra — lá
é "quantas pessoas chegaram", aqui é "o app está respondendo" —, mas **a régua é a mesma**:

## O que é gravado

| Campo | Exemplo | De onde vem |
|---|---|---|
| Hora | `2026-08-27T10:00` | do relógio do servidor, truncada na hora |
| Rota | `/api/nodes/{code}` | do **padrão** que o roteamento casou |
| Contagens | `requisições, 4xx, 5xx` | do status da resposta |
| Tempo | soma, máximo e faixas | da duração da requisição |

E, numa segunda tabela, uma linha por **subida da aplicação**: o instante e os perfis ativos. Ela
existe para transformar "acho que reiniciou de madrugada" em fato — o log da plataforma rotaciona, e
um restart que ninguém pediu não deixa rastro em outro lugar.

## O que NÃO é gravado

- **Endereço de IP.** Não há coluna, não há log. O mesmo teste que varre o schema atrás de coluna de
  IP (`UsageSemIpTest`) cobre estas tabelas — ele varre o banco inteiro, não só as da coleta.
- **Quem fez a requisição.** Não há `user_id`, não há chave de visita, não há sessão. Contar
  requisição não é observar pessoa, e é essa fronteira que faz o resto deste documento continuar
  verdadeiro.
- **O caminho que chegou.** O que se grava é o *padrão* da rota — `/api/nodes/{code}`, nunca
  `/api/nodes/ABC`. Não é arrumação: é o que impede um token de confirmação de e-mail de acabar
  numa tabela de métrica, pela mesma porta que o `UsagePaths` fecha na coleta de uso.
- **Corpo, cabeçalho ou parâmetro de qualquer requisição.**

## O aviso de configuração do cookie de sessão

O backend compara o flag `Secure` que a configuração declara com o esquema pelo qual a requisição
chegou, e escreve um **aviso no log** quando os dois não batem — `https` com o cookie sem o flag, ou
`http` com ele. Existe porque a variável de ambiente que liga o flag e o código que a lê entram por
caminhos diferentes, e o valor errado é um valor válido: sem o aviso, o desencontro seria silencioso
nos dois sentidos.

O que esse aviso carrega é **o nome de uma variável de ambiente e mais nada**. Não há endereço, rota,
conta, cookie nem identificador de sessão no texto — nem o valor do cabeçalho que originou o esquema,
só o fato de a requisição ter chegado por `http` ou por `https`. Sai no máximo **uma vez por hora**,
por mais requisições que passem. A regra do bloco acima continua valendo inteira: cabeçalho de
requisição não é registrado.

## O identificador de correlação do erro

Quando o app responde **500**, a resposta traz um código de oito caracteres, e a mesma linha do log
traz o mesmo código. Ele é **sorteado**, não deriva de nada e não descreve nada: serve só para que
um relato ("deu erro e apareceu K7QF3M2P") ache a linha certa num log de um dia inteiro. A mensagem
da exceção não vai no corpo da resposta — erro não previsto é justamente aquele cujo texto ninguém
revisou.

## O alerta por e-mail

Quando a taxa de 5xx passa do limiar, ou quando há um pico de respostas 401/403, sai **um** e-mail
para os endereços de `FOS_OWNER_EMAILS` — os mesmos que administram, nunca um endereço vindo de
formulário. O e-mail carrega **números**, e nada mais: quantas requisições, quantos erros, qual a
janela. Não há rota, não há conta, não há endereço.

Um por incidente, e não um por janela: enquanto a condição durar, nada mais é enviado.

## Por quanto tempo

90 dias, e depois some. Aqui a retenção é economia de disco e não promessa de privacidade — não há
dado pessoal nestas tabelas para expirar.

## Quem vê isso

Quem administra (`app_user.role = ADMIN`), em `/admin/painel`, seção *Saúde*. A resposta não tem
e-mail, nome nem id de conta, e há teste que varre o corpo inteiro atrás de uma arroba.

## O que essa medição não responde

**Se o site ficou fora do ar.** Aplicação parada não escreve estatística, e uma hora sem linha é
indistinguível de uma madrugada sem visita. Quem responde isso é uma verificação **de fora**, num
runner do GitHub Actions, que bate na URL pública e abre issue no repositório quando ela não
responde. Essa verificação não vê nada além do que qualquer visitante vê: ela pede a página
inicial e olha o código de status.
