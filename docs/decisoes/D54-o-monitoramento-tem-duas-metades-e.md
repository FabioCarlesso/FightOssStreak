# D54 — O monitoramento tem duas metades, e a que avisa que o site caiu vive fora do site

## Justificativa

Existia `/actuator/health`, e só: a Railway o usava para decidir se o deploy subiu, e mais nada
olhava. App caído às 3h não avisava ninguém, e rota que passasse a devolver 500 depois de um deploy
só aparecia quando alguém reclamava — e com o cadastro aberto (D47) o número de pessoas que topa com
uma queda deixou de ser um. O que **decide o desenho** é uma frase: **alerta gerado dentro da
aplicação é justamente o que não roda quando ela morre.** Daí a divisão.

**De fora**, um workflow do GitHub Actions em cron bate na URL pública a cada dez minutos, de um
runner que não é a máquina do app e de uma conta que não é a da plataforma de deploy; duas execuções
seguidas sem `200` abrem uma issue, a volta comenta e fecha a mesma issue.

**De dentro**, um filtro conta requisição, status e latência por rota, agrega em memória e grava por
hora numa tabela pequena — sem Prometheus, sem Grafana, sem container novo, porque o deploy é de
instância única (D22) e não comporta stack de observabilidade. Três escolhas que não são detalhe:

**(a)** a rota gravada é o *padrão* que o roteamento casou, nunca o caminho que chegou — é a guarda
do `UsagePaths` (D50) por outro caminho, e sem ela um token de confirmação de e-mail acabaria numa
tabela de métrica;

**(b)** o que se guarda de latência é um **histograma de escada fixa**, porque percentil não é
somável e o p95 de uma semana não sai da média dos p95 de cada hora;

**(c)** o alerta por e-mail tem **trava por incidente**, não por janela — é o mesmo defeito que a
D38 resolveu para a fila, e repeti-lo aqui transformaria o alerta em filtro no Gmail até o incidente
seguinte também não ser lido. Nada disso guarda IP, conta ou chave de visita: contar requisição não
é observar pessoa, e a régua da D50 vale igual. E a gravação **não** depende da credencial de envio
(a armadilha da D38): quem depende dela é só o alerta, senão dev, CI e qualquer instalação sem
provedor de e-mail ficariam sem histórico nenhum

## Revisar quando

Se o app passar a ter mais de uma réplica — o buffer em memória vira um por réplica, e a soma deixa
de ser o total. Ou se a conta de minutos do Actions incomodar: aí o intervalo sobe, não a
arquitetura

---

[Índice das decisões](../README.md)
