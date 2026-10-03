# Deploy

O mesmo par de imagens roda no Compose local e na Railway (D22) — o que muda é só o ambiente. As
duas imagens constroem a partir da **raiz** do repo (`-f backend/Dockerfile .`,
`-f web/Dockerfile .`). Variáveis e valores por ambiente em [`configuracao.md`](configuracao.md).

## Compose local

```bash
docker compose up --build
```

Abra <http://localhost:8081>. Sobe `db` (Postgres), `backend` e `web`. O browser fala só com o
`web`: é o nginx dele que encaminha `/api` para o backend — mesma topologia de produção, e por isso
nenhuma requisição sai para `:8080`. Os dados ficam no volume `fos-pgdata` e sobrevivem a
`docker compose down`; `docker compose down -v` zera.

```bash
docker compose logs -f backend     # logs
docker compose restart backend     # reiniciar sem perder dados
docker compose down -v             # apagar tudo, inclusive o banco
```

O backend também publica `:8080` no host, só para depurar e para o Swagger
(<http://localhost:8080/swagger-ui.html>). O app não usa essa porta.

As portas são parametrizadas — a imagem não tem porta fixa dentro dela:

```bash
WEB_HOST_PORT=3000 docker compose up   # app em http://localhost:3000
PORT=9090 docker compose up web        # nginx escuta 9090 dentro do container
```

## Railway

Dois serviços a partir deste repo (papéis **backend** e **web**) mais um Postgres gerenciado.
Nenhum host, porta ou credencial está fixo dentro das imagens, e o deploy não exige editar
`application.yml` nem os Dockerfiles. **Só o `web` recebe domínio público.** O backend fica
acessível apenas pela rede privada, atrás do nginx (D24).

1. Criar o projeto e adicionar o **Postgres** (template gerenciado da Railway). Manter o nome padrão
   `Postgres`: as referências `${{Postgres.PGHOST}}` da tabela casam pelo nome do serviço.
2. Criar o serviço **backend** a partir deste repo. Em *Settings → Config as code*, apontar para
   `backend/railway.json` — ele já traz `dockerfilePath` e o `healthcheckPath`. Não mexer em *Root
   Directory*: as duas imagens constroem a partir da raiz do repo.
3. Criar o serviço **web** do mesmo jeito, com `web/railway.json`.
4. Preencher as [variáveis](configuracao.md#variáveis) em cada serviço, **backend primeiro**: até ele
   subir no Postgres, o `web` não tem o que proxiar.
5. Gerar domínio público **só no `web`** (*Settings → Networking → Generate Domain*), com *target
   port* igual ao `PORT` do serviço.

Os nomes dos serviços são livres, mas não são cosméticos: o domínio da rede privada sai deles. Um
serviço chamado `FOS-backend` atende em `fos-backend.railway.internal`, e é esse valor que vai no
`BACKEND_ORIGIN` do `web`. Confirme o domínio em *Settings → Networking → Private Networking* do
backend antes de colar.

**Código e variável entram por caminhos diferentes** — o código por PR, a variável por clique no
painel. Variável nova que o código novo exige precisa estar na Railway **antes** do deploy (lição
da #96). As conferências depois do deploy estão em [`seguranca.md`](seguranca.md).

## Detalhes que não são óbvios

- **`PORT` explícita, mesmo a plataforma sabendo injetá-la.** No backend porque o `BACKEND_ORIGIN`
  do nginx aponta para uma porta fixa: serviço sem domínio público não tem garantia de receber a
  variável, e se receber outra o proxy bate em porta errada. No web para que o *target port* do
  domínio tenha um valor conhecido para casar.
- **`SPRING_PROFILES_ACTIVE` esquecida não quebra nada — e é justamente o problema.** O
  `application.yml` tem `profiles.default: dev`, que é H2 em memória. O deploy fica verde, o
  healthcheck passa, a API responde, e todo progresso some no deploy seguinte. Sinal de que o perfil
  pegou: as migrations do Flyway nos logs da subida.
- **`VITE_PUBLIC_URL` é de build, não de runtime.** Ela só existe para as tags `og:url` e `og:image`
  da prévia de link, que exigem URL absoluta. Mudá-la pede um novo deploy do `web`, e sem ela as duas
  tags simplesmente não são emitidas — o link fica sem imagem de prévia e nada mais quebra (D33).
- **`DATABASE_URL` não serve.** A Railway a expõe no formato `postgresql://user:pass@host/db`, que
  não é uma URL JDBC e o Spring não aceita. Daí montar `FOS_DB_URL` a partir das variáveis de
  referência do Postgres.
- **`SERVER_ADDRESS=::`.** A rede privada da Railway resolve IPv6, e ambientes criados antes de
  16/10/2025 são IPv6-only. Com `0.0.0.0` o backend fica invisível para o nginx.
- **`NGINX_RESOLVER`.** O nginx resolve o upstream no start e cacheia; como o IP do backend muda a
  cada deploy, o `proxy_pass` vai por variável e o `resolver` re-resolve em runtime. Confirme o
  endereço na doc da Railway antes de colar — é o tipo de detalhe que muda.
- **`TZ`.** O streak usa a data do servidor. Em UTC, um drill às 22h de Brasília contaria como do
  dia seguinte.
- **A base de geolocalização é baixada no build da imagem, não versionada (D50).** O
  `backend/Dockerfile` puxa o [DB-IP Lite](https://db-ip.com) (CC BY 4.0) do mês corrente, com recuo
  para o mês anterior, e já aponta `FOS_USAGE_GEOIP_DATABASE` para ele — **na Railway não há o que
  configurar**. Quem fala com o db-ip.com é a máquina de build; nenhuma chamada a serviço externo
  acontece por requisição, e o IP de quem usa o app não sai daqui. O download **nunca derruba o
  build**: terceiro fora do ar vira base ausente, o app sobe igual e coleta tudo menos país, que vira
  `ZZ`. É assim que dev e CI rodam — o CI passa `--build-arg GEOIP=false` de propósito. Para usar
  outra base, aponte a variável para um CSV `início,fim,país[,região]` (aceita `.gz`); para ficar sem
  nenhuma, defina-a vazia. **Crédito**: dado de país por DB-IP, sob CC BY 4.0.
