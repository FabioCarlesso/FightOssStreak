# D79 — A imagem do runtime do backend fixa a versão exata da JRE, e o Dependabot `docker` a atualiza

## Justificativa

A CVE-2026-114938 (JIT do HotSpot, CVSS 8.1, explorável pela rede) afetava o Java 21 até a 21.0.13
e foi corrigida na 21.0.13.1 (#206). O runtime do backend usava a tag flutuante
`eclipse-temurin:21-jre-alpine`, e por isso a correção dependia de duas coisas que o repositório não
controla nem enxerga: o Docker Hub atualizar a tag (o que levou três dias depois do build da
Adoptium) e a Railway não reaproveitar a camada da base em cache no próximo build. Nada no git
mudava quando a correção chegava, e nada avisava que um deploy tinha que acontecer.

**(a) O runtime fixa a versão exata** (`eclipse-temurin:21.0.13.1_2-jre-alpine`). A versão que
roda em produção passa a ser a que está escrita no `backend/Dockerfile`, e trocá-la é um commit:
o merge dispara o deploy, e o build não tem camada antiga para reaproveitar.

**(b) Quem atualiza é o Dependabot, no ecossistema `docker` em `/backend`.** Tag fixa sem rotina de
atualização deixaria a próxima correção trimestral da Oracle (a de 19/01/2027) para trás em
silêncio, que é o problema oposto. O PR do bot passa pelos checks `backend` e `web` (D18) e não tem
automerge, como os outros ecossistemas. Major é ignorado: trocar de Java (21 → 25) é decisão
própria, não atualização de rotina.

**(c) Só o runtime é fixado.** O estágio de build (`maven:3.9-eclipse-temurin-21`) compila e não
fica exposto à rede; o Dependabot o acompanha do mesmo bloco, mas a CVE não passa por ali.

## Revisar quando

- Se os PRs do Dependabot `docker` passarem a chegar com frequência que incomoda (rebuild de
  Alpine sem mudança de JRE): agrupar ou restringir a `eclipse-temurin`, sem voltar à tag flutuante.
- Se o Dependabot deixar de reconhecer a tag da Temurin (formato `21.0.13.1_2`) e parar de abrir
  PR: a tag fixa vira dívida silenciosa, e é preciso outra rotina (ou fixar por digest) antes de
  qualquer coisa.
- Se o projeto adotar Java 25: a mudança é manual, numa PR própria, e esta decisão continua valendo
  para a versão nova.

---

[Índice das decisões](../README.md)
