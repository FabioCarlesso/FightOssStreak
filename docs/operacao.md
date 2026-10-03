# Operação

Como saber se o app está no ar, se está sendo usado e o que quem usa está pedindo. O que cada
medição guarda — e o que não guarda — está em [`privacidade/`](privacidade/README.md).

## Monitoramento: duas metades (D54, #86)

**Quem avisa que o site caiu não pode ser o site.** As duas metades não se substituem.

- **De fora** — o workflow `saude` (`.github/workflows/saude.yml`) bate na URL pública a cada dez
  minutos de um runner do GitHub. Ele lê a variável de repositório **`URL_PUBLICA`** (Settings →
  Secrets and variables → Actions → Variables); sem ela o workflow não faz nada e não fica vermelho,
  que é o que se quer em um clone. **Duas execuções seguidas** sem `200` abrem uma issue; a volta
  comenta e fecha a mesma issue, nunca abre outra. Não é required check, e não deve virar: o site
  fora do ar não pode travar o merge da correção. Nenhum serviço externo pago e nenhuma conta nova
  em terceiro.
- **De dentro** — o `HttpStatFilter` conta requisição, status e latência por rota, agrega em memória
  e grava por hora em `http_stat_hourly`, junto com as subidas em `app_start` — sem Prometheus, sem
  Grafana, sem container novo (D22). Aparece em `/admin/painel`, seção *Saúde*. É filtro e não
  interceptor **porque o 401 da cadeia de segurança nunca chega ao MVC**, e é justamente o pico de
  401 que o alerta procura. A rota gravada é o **padrão** que o roteamento casou, nunca o caminho que
  chegou, senão token de confirmação acabaria em tabela de métrica. Latência vira **histograma de
  escada fixa** (`HttpStats`) porque percentil não é somável — trocar a escada é migration.

Os limiares e a retenção são as variáveis `FOS_HEALTH_*` de [`configuracao.md`](configuracao.md).

**Nada da coleta nem da medição de saúde guarda endereço de IP.** O IP deriva país e compõe a chave
de visita, e é descartado no mesmo método: não há coluna, não há log, e há teste que reprova o build
se uma coluna com cara de IP aparecer em qualquer migration. Contar requisição não é observar pessoa
— detalhes em [`privacidade/saude-do-site.md`](privacidade/saude-do-site.md).

## Alerta por e-mail

Um por incidente, não um por janela. Taxa de 5xx acima do limiar e pico de 401/403 avisam **uma
vez**, para os endereços de `FOS_OWNER_EMAILS`; enquanto a condição durar, nada mais sai, e um alerta
novo só depois de ela passar. Sem `FOS_EMAIL_API_KEY` nada é enviado e a aplicação sobe igual — mas
a **gravação** da estatística continua, de propósito: um ambiente sem provedor de envio não pode
ficar sem histórico nenhum.

A resposta 500 carrega um **identificador de correlação**: oito caracteres, no corpo e no log, para
que "deu erro ao salvar" vire uma busca em vez de uma adivinhação. Erro de quem chama não pode virar
500 — ele entraria na taxa que dispara o alerta (ver [`api.md`](api.md#erros)).

## Painel de uso

Com uma conta `ADMIN`, o menu mostra *Painel* (`/admin/painel`), com acessos por dia, funil de seis
degraus, origem, perfil de uso, telas mais abertas e três números sobre as contas — em 7, 30 ou 90
dias, sempre com o comparativo do período anterior. O painel é **agregado e de ninguém** (D52): ele
lê só a contagem diária (`usage_daily`), nunca a tabela crua de eventos, e nenhum campo da resposta
identifica alguém ([`privacidade/coleta-de-uso.md`](privacidade/coleta-de-uso.md)). O período
termina **ontem** — o dia corrente ainda recebe evento e só entra na contagem depois de fechado,
então um painel recém-instalado fica zerado até o job diário rodar pela primeira vez.

Dimensão nova do painel é **linha no `UsageAggregator`**, nunca migration — foi assim que navegador
e idioma entraram.

## Feedback

Entre com uma conta `ADMIN` e abra *Feedback*; a fila aparece abaixo do formulário. Também está em
`GET /api/admin/feedback`. O desenho da fila está em [`produto/feedback.md`](produto/feedback.md).

## Vídeos fora do ar

O workflow `videos` reconfere toda semana os vídeos catalogados e avisa por issue quando um sai do
ar. Detalhes em [`conteudo/videos.md`](conteudo/videos.md#manutenção).
