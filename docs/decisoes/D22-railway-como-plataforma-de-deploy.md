# D22 — Railway como plataforma de deploy

## Justificativa

`docs/arquitetura.md` deixava "Railway/Render/Fly.io" em aberto, e isso travava as imagens: dá para escrever um
Dockerfile que sobe no Compose e não sobe em lugar nenhum — a plataforma decide se a porta vem de
`$PORT`, como os serviços se enxergam e em que formato o banco chega. Railway porque o modelo é o
desta fase: Postgres gerenciado sem administrar servidor, rede privada entre serviços sem VPC para
configurar, deploy por Dockerfile sem buildpack no meio. A configuração de cada serviço é versionada
em `backend/railway.json` e `web/railway.json`, mesma lógica da D18 — configuração de servidor sem
versionamento não tem histórico. O acoplamento à plataforma é pequeno e está todo em variável de
ambiente: as imagens não sabem que estão na Railway

## Revisar quando

Se o custo passar de simbólico, ou se aparecer necessidade que ela não cobre (região específica,
backup gerenciado com retenção). Trocar de plataforma custa reescrever os dois `railway.json` e as
variáveis, não as imagens

---

[Índice das decisões](../README.md)
